package com.queuetech.queuego.core.auth

import com.queuetech.queuego.core.model.AppRole
import com.queuetech.queuego.core.model.QueueGoSession
import com.queuetech.queuego.core.model.QueueGoUser
import com.queuetech.queuego.core.network.AuthTokenResponse
import com.queuetech.queuego.core.network.QueueGoApi

class AuthRepository(
    private val api: QueueGoApi,
    private val sessionStore: SecureSessionStore,
) {
    suspend fun login(identifier: String, password: String, expectedRole: AppRole): QueueGoUser {
        val auth = api.passwordLogin(authEmail(identifier), password)
        val profile = api.loadUserProfile(auth.accessToken, auth.authUserId)
        if (profile.role != expectedRole) {
            throw IllegalStateException("บัญชีนี้เป็นบัญชี${profile.role.displayName} กรุณาใช้แอปให้ตรงกับประเภทบัญชี")
        }
        if (expectedRole == AppRole.CUSTOMER && profile.status != "active") {
            throw IllegalStateException("บัญชีลูกค้ายังไม่พร้อมใช้งาน")
        }
        sessionStore.save(auth.toSession(profile))
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
            session = refreshed.toSession(session.userId, session.role, session.authUserId)
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
        return profile
    }

    suspend fun logout() {
        sessionStore.read()?.let { runCatching { api.signOut(it.accessToken) } }
        sessionStore.clear()
    }

    private fun authEmail(identifier: String): String {
        val clean = identifier.trim()
        if (clean.contains("@")) return clean.lowercase()
        val digits = clean.filter(Char::isDigit)
        require(digits.isNotBlank()) { "กรุณากรอกเบอร์โทรศัพท์หรืออีเมล" }
        return "$digits@auth.queuetech.local"
    }

    private fun AuthTokenResponse.toSession(profile: QueueGoUser): QueueGoSession =
        toSession(profile.id, profile.role, authUserId)

    private fun AuthTokenResponse.toSession(userId: String, role: AppRole, fallbackAuthUserId: String): QueueGoSession {
        val expiry = expiresAtEpochSeconds?.times(1000) ?: (System.currentTimeMillis() + expiresInSeconds * 1000)
        return QueueGoSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = expiry,
            authUserId = authUserId.ifBlank { fallbackAuthUserId },
            userId = userId,
            role = role,
        )
    }
}
