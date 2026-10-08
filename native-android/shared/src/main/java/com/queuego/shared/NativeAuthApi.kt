package com.queuego.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

class NativeAuthApi {
    companion object {
        private const val BASE_URL = "https://pkypiqhlrmzocysgeqew.supabase.co"
        private const val KEY = "sb_publishable_Vn2Il4jp-iBtzNsGRNY8Lg_Tpvk2Vre"
        private const val TIMEOUT = 15000
    }

    suspend fun signIn(
        identifier: String,
        password: String,
        expectedRole: String,
        deviceId: String
    ): NativeAuth = withContext(Dispatchers.IO) {
        val email = if (identifier.contains("@")) identifier.trim()
        else identifier.filter(Char::isDigit) + "@auth.queuetech.local"
        val auth = requestObject(
            "POST",
            "/auth/v1/token?grant_type=password",
            null,
            JSONObject().put("email", email).put("password", password)
        )
        val token = auth.getString("access_token")
        val authUserId = auth.getJSONObject("user").getString("id")
        val rows = requestArray(
            "GET",
            "/rest/v1/users?select=id,name,role,status,auth_user_id&auth_user_id=eq." + enc(authUserId),
            token
        )
        if (rows.length() == 0) error("ไม่พบบัญชี QueueGo")
        val row = rows.getJSONObject(0)
        if (row.optString("role") != expectedRole) error("บัญชีนี้ใช้กับแอปนี้ไม่ได้")
        val sessionId = UUID.randomUUID().toString()
        val claim = rpc(
            "claim_active_session",
            token,
            JSONObject().put("p_session_id", sessionId).put("p_device_id", deviceId)
        )
        if (claim is JSONArray && claim.length() > 0 &&
            claim.optJSONObject(0)?.optBoolean("success", true) == false
        ) error("ไม่สามารถเปิด Session นี้ได้")
        NativeAuth(
            NativeSession(
                authUserId,
                token,
                auth.optString("refresh_token").takeIf { it.isNotBlank() },
                auth.optLong("expires_at", 0L) * 1000L,
                sessionId
            ),
            NativeUser(
                row.getString("id"),
                authUserId,
                row.optString("name").ifBlank { "QueueGo" },
                row.optString("role"),
                row.optString("status")
            )
        )
    }

    suspend fun validate(auth: NativeAuth, expectedRole: String): NativeAuth =
        withContext(Dispatchers.IO) {
            val liveAuth = refreshIfNeeded(auth)
            val check = rpc(
                "check_active_session",
                liveAuth.session.accessToken,
                JSONObject().put("p_session_id", liveAuth.session.sessionId)
            )
            val valid = check == true ||
                (check is JSONArray && check.length() > 0 &&
                    (check.optBoolean(0, false) ||
                        check.optJSONObject(0)?.optBoolean("check_active_session", false) == true)) ||
                (check is JSONObject && check.optBoolean("check_active_session", false))
            if (!valid) error("Session นี้ไม่ได้ใช้งานบนอุปกรณ์นี้แล้ว")
            val rows = requestArray(
                "GET",
                "/rest/v1/users?select=id,name,role,status&auth_user_id=eq." + enc(liveAuth.session.authUserId),
                liveAuth.session.accessToken
            )
            if (rows.length() == 0) error("ไม่พบบัญชี QueueGo")
            val row = rows.getJSONObject(0)
            if (row.optString("role") != expectedRole) error("สิทธิ์บัญชีไม่ตรงกับแอป")
            liveAuth.copy(
                user = NativeUser(
                    row.getString("id"),
                    liveAuth.session.authUserId,
                    row.optString("name").ifBlank { "QueueGo" },
                    row.optString("role"),
                    row.optString("status")
                )
            )
        }

    private fun refreshIfNeeded(auth: NativeAuth): NativeAuth {
        val session = auth.session
        val refresh = session.refreshToken?.takeIf { it.isNotBlank() } ?: return auth
        if (session.expiresAtMs > System.currentTimeMillis() + 60_000L) return auth

        val renewed = requestObject(
            "POST",
            "/auth/v1/token?grant_type=refresh_token",
            null,
            JSONObject().put("refresh_token", refresh)
        )
        val nextAccess = renewed.optString("access_token")
        if (nextAccess.isBlank()) error("ไม่สามารถต่ออายุ Session ได้")
        val nextRefresh = renewed.optString("refresh_token").takeIf { it.isNotBlank() } ?: refresh
        val nextExpiry = renewed.optLong("expires_at", 0L).let {
            if (it > 0L) it * 1000L else System.currentTimeMillis() + 55 * 60_000L
        }
        return auth.copy(
            session = session.copy(
                accessToken = nextAccess,
                refreshToken = nextRefresh,
                expiresAtMs = nextExpiry
            )
        )
    }

    suspend fun touch(session: NativeSession) = withContext(Dispatchers.IO) {
        rpc(
            "touch_active_session",
            session.accessToken,
            JSONObject().put("p_session_id", session.sessionId)
        )
        Unit
    }

    suspend fun revoke(session: NativeSession) = withContext(Dispatchers.IO) {
        runCatching {
            rpc(
                "revoke_active_session",
                session.accessToken,
                JSONObject().put("p_session_id", session.sessionId)
            )
        }
        Unit
    }

    private fun rpc(name: String, token: String, body: JSONObject): Any =
        requestAny("POST", "/rest/v1/rpc/" + name, token, body)

    private fun requestObject(method: String, path: String, token: String?, body: JSONObject): JSONObject =
        requestAny(method, path, token, body) as? JSONObject ?: error("รูปแบบข้อมูลไม่ถูกต้อง")

    private fun requestArray(method: String, path: String, token: String): JSONArray =
        requestAny(method, path, token, null) as? JSONArray ?: JSONArray()

    private fun requestAny(method: String, path: String, token: String?, body: JSONObject?): Any {
        val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.setRequestProperty("apikey", KEY)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer " + token)
            if (method != "GET") connection.setRequestProperty("Prefer", "return=representation")
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText()
            }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(text).optString("message") }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "HTTP " + code
                error(message)
            }
            if (text.isBlank()) return JSONObject()
            val value = text.trim()
            return when {
                value.startsWith("[") -> JSONArray(value)
                value.startsWith("{") -> JSONObject(value)
                value == "true" -> true
                value == "false" -> false
                else -> value
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
