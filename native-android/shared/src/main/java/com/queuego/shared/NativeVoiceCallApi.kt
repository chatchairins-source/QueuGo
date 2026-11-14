package com.queuego.shared

import org.json.JSONObject

data class NativeVoiceIceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
) {
    init {
        require(urls.isNotEmpty())
        require(urls.all { url ->
            url.startsWith("stun:") || url.startsWith("stuns:") ||
                url.startsWith("turn:") || url.startsWith("turns:")
        }) { "ICE server URL ไม่ถูกต้อง" }
    }
}

data class NativeVoiceCall(
    val id: String,
    val orderId: String,
    val topic: String,
    val target: String,
    val status: String,
    val callerUserId: String,
    val calleeUserId: String,
    val callerName: String? = null,
    val calleeName: String? = null,
    val callerRole: String? = null,
    val calleeRole: String? = null,
    val createdAt: String?,
    val expiresAt: String?
) {
    init {
        require(id.isNotBlank())
        require(orderId.isNotBlank())
        require(VOICE_CALL_TOPIC.matches(topic))
        require(target in setOf("shop", "rider", "customer"))
        require(status in setOf("ringing", "accepted", "declined", "ended", "missed"))
        require(callerUserId.isNotBlank() && calleeUserId.isNotBlank())
    }
}

private val VOICE_CALL_TOPIC =
    Regex("^qg-call:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")

class NativeVoiceCallApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun start(auth: NativeAuth, orderId: String, target: String): NativeVoiceCall {
        require(target in setOf("shop", "rider", "customer")) { "ปลายทางสายโทรไม่ถูกต้อง" }
        return parse(http.obj(http.rpc(
            "qg_call_start",
            auth.session.accessToken,
            JSONObject()
                .put("p_order_id", orderId)
                .put("p_target", target)
                .put("p_session_id", auth.session.sessionId)
        )))
    }

    suspend fun incoming(auth: NativeAuth): NativeVoiceCall? {
        val value = http.rpc(
            "qg_call_incoming",
            auth.session.accessToken,
            JSONObject().put("p_session_id", auth.session.sessionId)
        )
        if (value === JSONObject.NULL) return null
        val obj = http.obj(value)
        if (obj.length() == 0 || obj.optBoolean("none", false)) return null
        return parse(obj)
    }

    suspend fun answer(auth: NativeAuth, callId: String): NativeVoiceCall =
        parse(http.obj(http.rpc(
            "qg_call_answer",
            auth.session.accessToken,
            JSONObject().put("p_call_id", callId).put("p_session_id", auth.session.sessionId)
        )))

    suspend fun decline(auth: NativeAuth, callId: String): NativeVoiceCall =
        parse(http.obj(http.rpc(
            "qg_call_decline",
            auth.session.accessToken,
            JSONObject().put("p_call_id", callId).put("p_session_id", auth.session.sessionId)
        )))

    suspend fun end(auth: NativeAuth, callId: String): NativeVoiceCall =
        parse(http.obj(http.rpc(
            "qg_call_end",
            auth.session.accessToken,
            JSONObject().put("p_call_id", callId).put("p_session_id", auth.session.sessionId)
        )))

    suspend fun iceConfig(auth: NativeAuth, callId: String): List<NativeVoiceIceServer> {
        val result = http.obj(http.functionPost(
            "queuego-turn",
            auth.session.accessToken,
            JSONObject()
                .put("callId", callId)
                .put("sessionId", auth.session.sessionId)
        ))
        return parseIceConfig(result)
    }

    internal fun parseIceConfig(result: JSONObject): List<NativeVoiceIceServer> {
        require(result.optBoolean("turnReady", false)) { "TURN relay ยังไม่พร้อม" }
        val rows = result.optJSONArray("iceServers") ?: error("ไม่พบ TURN relay")
        val parsed = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val urls = row.optJSONArray("urls")?.let { values ->
                    buildList {
                        for (i in 0 until values.length()) {
                            values.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }.orEmpty()
                if (urls.isEmpty()) continue
                add(NativeVoiceIceServer(
                    urls = urls,
                    username = row.optString("username").takeIf { it.isNotBlank() },
                    credential = row.optString("credential").takeIf { it.isNotBlank() }
                ))
            }
        }
        require(parsed.any { server -> server.urls.any { it.startsWith("turn:") || it.startsWith("turns:") } }) {
            "TURN relay ไม่พร้อมใช้งาน"
        }
        return parsed
    }

    suspend fun active(auth: NativeAuth, orderId: String): NativeVoiceCall? {
        val value = http.rpc(
            "qg_call_active",
            auth.session.accessToken,
            JSONObject().put("p_order_id", orderId).put("p_session_id", auth.session.sessionId)
        )
        if (value === JSONObject.NULL) return null
        val obj = http.obj(value)
        if (obj.length() == 0 || obj.optBoolean("none", false)) return null
        return parse(obj)
    }

    internal fun parse(row: JSONObject): NativeVoiceCall = NativeVoiceCall(
        id = row.getString("id"),
        orderId = row.getString("order_id"),
        topic = row.getString("topic"),
        target = row.getString("target"),
        status = row.getString("status"),
        callerUserId = row.getString("caller_user_id"),
        calleeUserId = row.getString("callee_user_id"),
        callerName = row.optString("caller_name").takeIf { it.isNotBlank() && it != "null" },
        calleeName = row.optString("callee_name").takeIf { it.isNotBlank() && it != "null" },
        callerRole = row.optString("caller_role").takeIf { it.isNotBlank() && it != "null" },
        calleeRole = row.optString("callee_role").takeIf { it.isNotBlank() && it != "null" },
        createdAt = row.optString("created_at").takeIf { it.isNotBlank() && it != "null" },
        expiresAt = row.optString("expires_at").takeIf { it.isNotBlank() && it != "null" }
    )
}

internal fun requireVoiceCallTopic(topic: String): String {
    require(VOICE_CALL_TOPIC.matches(topic)) { "Voice call topic ไม่ถูกต้อง" }
    return topic
}
