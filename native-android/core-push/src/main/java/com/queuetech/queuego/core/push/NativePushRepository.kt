package com.queuetech.queuego.core.push

import com.queuetech.queuego.core.auth.DeviceIdentityStore
import com.queuetech.queuego.core.auth.SecureSessionStore
import com.queuetech.queuego.core.model.AppRole
import com.queuetech.queuego.core.network.QueueGoApi
import org.json.JSONObject

data class NativePushRegistration(
    val deviceId: String,
    val role: AppRole,
)

class NativePushRepository(
    private val api: QueueGoApi,
    private val sessionStore: SecureSessionStore,
    private val deviceIdentityStore: DeviceIdentityStore,
) {
    suspend fun subscribe(
        firebaseToken: String,
        expectedRole: AppRole,
    ): NativePushRegistration {
        val cleanToken = firebaseToken.trim()
        require(cleanToken.length in 16..4096) { "FCM token ไม่ถูกต้อง" }

        val session = sessionStore.read()
            ?: throw IllegalStateException("กรุณาเข้าสู่ระบบใหม่")
        require(session.role == expectedRole) { "ประเภทบัญชีไม่ตรงกับแอป" }

        val deviceId = deviceIdentityStore.getOrCreate()
        val response = JSONObject(
            api.edgeFunction(
                accessToken = session.accessToken,
                functionName = "queuego-push",
                body = JSONObject()
                    .put("action", "subscribe-native")
                    .put("deviceId", deviceId)
                    .put("platform", "android")
                    .put("token", cleanToken),
            )
        )

        if (!response.optBoolean("ok", false)) {
            throw IllegalStateException("ลงทะเบียนแจ้งเตือนไม่สำเร็จ")
        }

        val role = AppRole.fromBackend(response.optString("role"))
            ?: throw IllegalStateException("ระบบแจ้งเตือนส่ง role ไม่ถูกต้อง")
        require(role == expectedRole) { "สิทธิ์แจ้งเตือนไม่ตรงกับแอป" }

        return NativePushRegistration(
            deviceId = response.optString("deviceId").ifBlank { deviceId },
            role = role,
        )
    }

    suspend fun unsubscribe() {
        val session = sessionStore.read() ?: return
        val deviceId = deviceIdentityStore.getOrCreate()

        runCatching {
            api.edgeFunction(
                accessToken = session.accessToken,
                functionName = "queuego-push",
                body = JSONObject()
                    .put("action", "unsubscribe-native")
                    .put("deviceId", deviceId),
            )
        }
    }

    suspend fun test(): Boolean {
        val session = sessionStore.read()
            ?: throw IllegalStateException("กรุณาเข้าสู่ระบบใหม่")

        val response = JSONObject(
            api.edgeFunction(
                accessToken = session.accessToken,
                functionName = "queuego-push",
                body = JSONObject().put("action", "test"),
            )
        )
        return response.optBoolean("ok", false)
    }
}
