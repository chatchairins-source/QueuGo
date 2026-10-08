package com.queuetech.queuego.core.auth

import com.queuetech.queuego.core.model.AppRole
import com.queuetech.queuego.core.model.QueueGoSession
import com.queuetech.queuego.core.model.QueueGoUser
import com.queuetech.queuego.core.network.AuthTokenResponse
import com.queuetech.queuego.core.network.QueueGoApi
import java.util.UUID

enum class ActiveSessionCheck {
    VALID,
    REPLACED,
    UNAVAILABLE,
    NOT_APPLICABLE,
}

class AuthRepository(
    private val api: QueueGoApi,
    private val sessionStore: SecureSessionStore,
    private val deviceIdentityStore: DeviceIdentityStore? = null,
) {
    suspend fun login(identifier: String, password: String, expectedRole: AppRole): QueueGoUser {
        val auth = api.passwordLogin(AuthIdentity.toSupabaseEmail(identifier), password)
        val profile = api.loadUserProfile(auth.accessToken, auth.authUserId)
        if (profile.role != expectedRole) {
            throw IllegalStateException("บัญชีนี้เป็นบัญชี${profile.role.displayName} กรุณาใช้แอปให้ตรงกับประเภทบัญชี")
        }
        if (expectedRole == AppRole.CUSTOMER && profile.status != "active") {
            throw IllegalStateException("บัญชีลูกค้ายังไม่พร้อมใช้งาน")
        }

        val activeSessionId = if (expectedRole == AppRole.RIDER) {
            val deviceStore = deviceIdentityStore
                ?: throw IllegalStateException("ไม่พบข้อมูลอุปกรณ์สำหรับ QueueGo Rider")
            val sessionId = UUID.randomUUID().toString()
            val claimed = api.claimActiveSession(
                accessToken = auth.accessToken,
                sessionId = sessionId,
                deviceId = deviceStore.getOrCreate(),
            )
            if (!claimed) throw IllegalStateException("ไม่สามารถยืนยันอุปกรณ์ไรเดอร์ได้")
            sessionId
        } else {
            null
        }

        sessionStore.save(auth.toSession(profile, activeSessionId))
        return profile
    }

    suspend fun restore(expectedRole: AppRole): QueueGoUser? {
        var session = sessionStore.read() ?: return null
        if (session.role != expectedRole) {
            sessionStore.clear()
            return null
        }
        if (session.expiresAtMillis - System.currentTimeMillis() < 60_000L) {
            if (session.refreshToken.isBlank()) {
                sessionStore.clear()
                return null
            }
            val refreshed = runCatching { api.refreshSession(session.refreshToken) }.getOrElse {
                sessionStore.clear()
                return null
            }
            session = refreshed.toSession(
                userId = session.userId,
                role = session.role,
                fallbackAuthUserId = session.authUserId,
                activeSessionId = session.activeSessionId,
            )
            sessionStore.save(session)
        }
        val profile = runCatching { api.loadUserProfile(session.accessToken, session.authUserId) }.getOrElse {
            sessionStore.clear()
            return null
        }
        if (profile.role != expectedRole || (expectedRole == AppRole.CUSTOMER && profile.status != "active")) {
            sessionStore.clear()
            return null
        }

        if (expectedRole == AppRole.RIDER) {
            val sid = session.activeSessionId
            if (sid.isNullOrBlank()) {
                sessionStore.clear()
                return null
            }
            when (verifyActiveSession(session)) {
                ActiveSessionCheck.REPLACED -> {
                    sessionStore.clear()
                    return null
                }
                ActiveSessionCheck.VALID -> runCatching {
                    api.touchActiveSession(session.accessToken, sid)
                }
                ActiveSessionCheck.UNAVAILABLE,
                ActiveSessionCheck.NOT_APPLICABLE -> Unit
            }
        }

        return profile
    }

    suspend fun verifyActiveSession(): ActiveSessionCheck {
        val session = sessionStore.read() ?: return ActiveSessionCheck.NOT_APPLICABLE
        if (session.role != AppRole.RIDER) return ActiveSessionCheck.NOT_APPLICABLE

        val sid = session.activeSessionId
        if (sid.isNullOrBlank()) {
            sessionStore.clear()
            return ActiveSessionCheck.REPLACED
        }

        val current = refreshForGuard(session)
        return verifyActiveSession(current)
    }

    private suspend fun verifyActiveSession(session: QueueGoSession): ActiveSessionCheck {
        val sid = session.activeSessionId ?: return ActiveSessionCheck.REPLACED
        return try {
            if (!api.checkActiveSession(session.accessToken, sid)) {
                sessionStore.clear()
                ActiveSessionCheck.REPLACED
            } else {
                runCatching { api.touchActiveSession(session.accessToken, sid) }
                ActiveSessionCheck.VALID
            }
        } catch (_: Exception) {
            ActiveSessionCheck.UNAVAILABLE
        }
    }

    private suspend fun refreshForGuard(session: QueueGoSession): QueueGoSession {
        if (session.expiresAtMillis - System.currentTimeMillis() >= 60_000L) return session
        if (session.refreshToken.isBlank()) return session

        val refreshed = runCatching { api.refreshSession(session.refreshToken) }.getOrNull()
            ?: return session
        val next = refreshed.toSession(
            userId = session.userId,
            role = session.role,
            fallbackAuthUserId = session.authUserId,
            activeSessionId = session.activeSessionId,
        )
        sessionStore.save(next)
        return next
    }

    suspend fun logout() {
        sessionStore.read()?.let { runCatching { api.signOut(it.accessToken) } }
        sessionStore.clear()
    }

    private fun AuthTokenResponse.toSession(
        profile: QueueGoUser,
        activeSessionId: String? = null,
    ): QueueGoSession =
        toSession(profile.id, profile.role, authUserId, activeSessionId)

    private fun AuthTokenResponse.toSession(
        userId: String,
        role: AppRole,
        fallbackAuthUserId: String,
        activeSessionId: String? = null,
    ): QueueGoSession {
        val expiry = expiresAtEpochSeconds?.times(1000)
            ?: (System.currentTimeMillis() + expiresInSeconds * 1000)
        return QueueGoSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = expiry,
            authUserId = authUserId.ifBlank { fallbackAuthUserId },
            userId = userId,
            role = role,
            activeSessionId = activeSessionId,
        )
    }
}
