package com.queuego.shared

import android.content.Context
import org.json.JSONObject
import java.util.UUID

class NativePushDeviceStore(context: Context, roleKey: String) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "queuego_native_push_" + roleKey,
        Context.MODE_PRIVATE
    )

    fun deviceId(): String {
        prefs.getString("device_id", null)?.takeIf { isUuid(it) }?.let { return it }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("device_id", created).apply()
        return created
    }

    private fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value); true }.getOrDefault(false)
}

class NativePushApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun subscribe(auth: NativeAuth, deviceId: String, token: String) {
        require(UUID.fromString(deviceId).toString().equals(deviceId, ignoreCase = true)) {
            "รหัสอุปกรณ์แจ้งเตือนไม่ถูกต้อง"
        }
        require(token.length in 16..4096) { "Push token ไม่ถูกต้อง" }
        http.functionPost(
            "queuego-push",
            auth.session.accessToken,
            JSONObject()
                .put("action", "subscribe-native")
                .put("deviceId", deviceId)
                .put("platform", "android")
                .put("token", token)
                .put("sessionId", auth.session.sessionId)
        )
    }

    suspend fun unsubscribe(auth: NativeAuth, deviceId: String) {
        if (runCatching { UUID.fromString(deviceId) }.getOrNull() == null) return
        http.functionPost(
            "queuego-push",
            auth.session.accessToken,
            JSONObject()
                .put("action", "unsubscribe-native")
                .put("deviceId", deviceId)
        )
    }

    suspend fun test(auth: NativeAuth) {
        http.functionPost(
            "queuego-push",
            auth.session.accessToken,
            JSONObject().put("action", "test")
        )
    }
}
