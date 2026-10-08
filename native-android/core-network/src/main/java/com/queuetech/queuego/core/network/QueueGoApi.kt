package com.queuetech.queuego.core.network

import com.queuetech.queuego.core.model.AppRole
import com.queuetech.queuego.core.model.QueueGoUser
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val expiresAtEpochSeconds: Long?,
    val authUserId: String,
)

class QueueGoApi(
    private val baseUrl: String = QueueGoSupabaseConfig.URL,
    private val publishableKey: String = QueueGoSupabaseConfig.PUBLISHABLE_KEY,
) {
    suspend fun passwordLogin(email: String, password: String): AuthTokenResponse {
        val body = JSONObject().put("email", email).put("password", password)
        return parseAuthToken(request("POST", "/auth/v1/token?grant_type=password", body = body.toString()))
    }

    suspend fun refreshSession(refreshToken: String): AuthTokenResponse {
        val body = JSONObject().put("refresh_token", refreshToken)
        return parseAuthToken(request("POST", "/auth/v1/token?grant_type=refresh_token", body = body.toString()))
    }

    suspend fun signOut(accessToken: String) {
        request("POST", "/auth/v1/logout", accessToken, "{}", acceptEmpty = true)
    }

    suspend fun loadUserProfile(accessToken: String, authUserId: String): QueueGoUser {
        val raw = request(
            "GET",
            "/rest/v1/users?select=id,auth_user_id,role,name,phone,status&auth_user_id=eq.$authUserId&limit=1",
            accessToken,
        )
        val rows = JSONArray(raw)
        if (rows.length() == 0) throw QueueGoApiException(404, "ไม่พบข้อมูลบัญชี QueueGo")
        val item = rows.getJSONObject(0)
        val role = AppRole.fromBackend(item.optString("role"))
            ?: throw QueueGoApiException(403, "ประเภทบัญชีนี้ยังไม่รองรับในแอป")
        return QueueGoUser(
            id = item.getString("id"),
            authUserId = item.getString("auth_user_id"),
            role = role,
            name = item.optString("name"),
            phone = item.optString("phone"),
            status = item.optString("status"),
        )
    }

    suspend fun rpc(accessToken: String, functionName: String, body: JSONObject = JSONObject()): String =
        request("POST", "/rest/v1/rpc/$functionName", accessToken, body.toString())

    private fun parseAuthToken(raw: String): AuthTokenResponse {
        val json = JSONObject(raw)
        val user = json.optJSONObject("user")
            ?: throw QueueGoApiException(401, "Supabase ไม่ส่งข้อมูลผู้ใช้กลับมา")
        return AuthTokenResponse(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token"),
            expiresInSeconds = json.optLong("expires_in", 3600),
            expiresAtEpochSeconds = json.optLong("expires_at").takeIf { it > 0 },
            authUserId = user.getString("id"),
        )
    }

    private suspend fun request(
        method: String,
        path: String,
        accessToken: String? = null,
        body: String? = null,
        acceptEmpty: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            useCaches = false
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (!accessToken.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $accessToken")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw QueueGoApiException(code, parseError(text, code))
            if (text.isBlank() && !acceptEmpty) "[]" else text
        } finally {
            connection.disconnect()
        }
    }

    private fun parseError(raw: String, status: Int): String {
        val parsed = runCatching { JSONObject(raw) }.getOrNull()
        return sequenceOf(
            parsed?.optString("message"),
            parsed?.optString("msg"),
            parsed?.optString("error_description"),
            parsed?.optString("hint"),
        ).firstOrNull { !it.isNullOrBlank() } ?: "เชื่อมต่อ QueueGo ไม่สำเร็จ (HTTP $status)"
    }
}
