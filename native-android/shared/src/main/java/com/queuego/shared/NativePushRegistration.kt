package com.queuego.shared

import android.content.Context
import java.util.UUID
import org.json.JSONObject

class NativePushDeviceStore(context: Context, role: String) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "queuego_native_push_" + role,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun deviceId(): String {
        val current = prefs.getString("device_id", null).orEmpty()
        if (current.matches(UUID_PATTERN)) return current
        val created = UUID.randomUUID().toString()
        check(prefs.edit().putString("device_id", created).commit())
        return created
    }

    private companion object {
        val UUID_PATTERN = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
    }
}

class NativePushApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun subscribe(auth: NativeAuth, deviceId: String, token: String) {
        require(deviceId.matches(UUID_PATTERN)) { "รหัสอุปกรณ์แจ้งเตือนไม่ถูกต้อง" }
        require(token.length in 16..4096) { "Push token ไม่ถูกต้อง" }
        http.functionPost(
            "queuego-push",
            auth.session.accessToken,
            JSONObject()
                .put("action", "subscribe-native")
                .put("deviceId", deviceId)
                .put("platform", "android")
                .put("token", token)
        )
    }

    suspend fun unsubscribe(auth: NativeAuth, deviceId: String) {
        if (!deviceId.matches(UUID_PATTERN)) return
        http.functionPost(
            "queuego-push",
            auth.session.accessToken,
            JSONObject()
                .put("action", "unsubscribe-native")
                .put("deviceId", deviceId)
        )
    }

    private companion object {
        val UUID_PATTERN = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
    }
}
