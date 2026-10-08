package com.queuego.rider

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

class QueueGoApi {
    companion object {
        private const val BASE_URL = "https://pkypiqhlrmzocysgeqew.supabase.co"
        private const val PUBLISHABLE_KEY = "sb_publishable_Vn2Il4jp-iBtzNsGRNY8Lg_Tpvk2Vre"
        private const val TIMEOUT = 15000
    }

    suspend fun signIn(identifier: String, password: String, deviceId: String): QueueGoAuth =
        withContext(Dispatchers.IO) {
            val email = if (identifier.contains("@")) identifier.trim()
            else identifier.filter(Char::isDigit) + "@auth.queuetech.local"

            val auth = requestObject(
                "POST",
                "/auth/v1/token?grant_type=password",
                null,
                JSONObject().put("email", email).put("password", password)
            )
            val accessToken = auth.getString("access_token")
            val authUserId = auth.getJSONObject("user").getString("id")
            val users = requestArray(
                "GET",
                "/rest/v1/users?select=id,name,role,status&auth_user_id=eq." + enc(authUserId),
                accessToken
            )
            if (users.length() == 0) error("ไม่พบบัญชี QueueGo")
            val row = users.getJSONObject(0)
            if (row.optString("role") != "rider") error("บัญชีนี้ไม่ใช่บัญชีไรเดอร์")

            val sessionId = UUID.randomUUID().toString()
            val claim = rpc(
                "claim_active_session",
                accessToken,
                JSONObject().put("p_session_id", sessionId).put("p_device_id", deviceId)
            )
            if (claim is JSONArray && claim.length() > 0 &&
                claim.optJSONObject(0)?.optBoolean("success", true) == false
            ) error("ไม่สามารถเปิด Session นี้ได้")

            QueueGoAuth(
                session = QueueGoSession(
                    authUserId = authUserId,
                    accessToken = accessToken,
                    refreshToken = auth.optString("refresh_token").takeIf { it.isNotBlank() },
                    expiresAtMs = auth.optLong("expires_at", 0L) * 1000L,
                    sessionId = sessionId
                ),
                user = QueueGoUser(
                    id = row.getString("id"),
                    name = row.optString("name").ifBlank { "ไรเดอร์" },
                    role = row.optString("role"),
                    status = row.optString("status")
                )
            )
        }

    suspend fun validate(auth: QueueGoAuth): QueueGoAuth = withContext(Dispatchers.IO) {
        val result = rpc(
            "check_active_session",
            auth.session.accessToken,
            JSONObject().put("p_session_id", auth.session.sessionId)
        )
        val valid = result == true ||
            (result is JSONArray && result.length() > 0 &&
                (result.optBoolean(0, false) ||
                    result.optJSONObject(0)?.optBoolean("check_active_session", false) == true))
        if (!valid) error("บัญชีนี้ถูกเข้าสู่ระบบจากอุปกรณ์อื่น")

        val users = requestArray(
            "GET",
            "/rest/v1/users?select=id,name,role,status&auth_user_id=eq." + enc(auth.session.authUserId),
            auth.session.accessToken
        )
        if (users.length() == 0) error("ไม่พบบัญชี QueueGo")
        val row = users.getJSONObject(0)
        if (row.optString("role") != "rider") error("สิทธิ์บัญชีไม่ถูกต้อง")
        auth.copy(
            user = QueueGoUser(
                id = row.getString("id"),
                name = row.optString("name").ifBlank { "ไรเดอร์" },
                role = "rider",
                status = row.optString("status")
            )
        )
    }

    suspend fun touch(session: QueueGoSession) = withContext(Dispatchers.IO) {
        rpc(
            "touch_active_session",
            session.accessToken,
            JSONObject().put("p_session_id", session.sessionId)
        )
        Unit
    }

    suspend fun revoke(session: QueueGoSession) = withContext(Dispatchers.IO) {
        runCatching {
            rpc(
                "revoke_active_session",
                session.accessToken,
                JSONObject().put("p_session_id", session.sessionId)
            )
        }
        Unit
    }

    suspend fun riderSnapshot(auth: QueueGoAuth): RiderSnapshot = withContext(Dispatchers.IO) {
        val profiles = requestArray(
            "GET",
            "/rest/v1/rider_profiles?select=id,metadata&user_id=eq." + enc(auth.user.id) + "&limit=1",
            auth.session.accessToken
        )
        if (profiles.length() == 0) return@withContext RiderSnapshot(false, null, null)

        val profile = profiles.getJSONObject(0)
        val profileId = profile.getString("id")
        val online = profile.optJSONObject("metadata")?.optBoolean("online", false) ?: false

        val activeRows = requestArray(
            "GET",
            "/rest/v1/orders?select=id,order_number,status,pickup_address,pickup_latitude,pickup_longitude,delivery_address,delivery_latitude,delivery_longitude,delivery_fee&order_type=eq.shopping&rider_id=eq." +
                enc(profileId) +
                "&status=in.(rider_assigned,preparing,ready,assigned,picked_up,in_progress)&order=created_at.desc&limit=1",
            auth.session.accessToken
        )
        val active = if (activeRows.length() > 0) parseJob(activeRows.getJSONObject(0)) else null

        var offered: RiderJob? = null
        if (active == null && online) {
            val pool = rpcArray("get_rider_delivery_pool", auth.session.accessToken)
            val offer = rpcArray("qg_get_my_rider_offer", auth.session.accessToken)
            val offeredId = firstOrderId(offer) ?: firstOrderId(pool)
            if (offeredId != null) {
                val rows = requestArray(
                    "GET",
                    "/rest/v1/orders?select=id,order_number,status,pickup_address,pickup_latitude,pickup_longitude,delivery_address,delivery_latitude,delivery_longitude,delivery_fee&id=eq." +
                        enc(offeredId) + "&limit=1",
                    auth.session.accessToken
                )
                if (rows.length() > 0) offered = parseJob(rows.getJSONObject(0))
            }
        }
        RiderSnapshot(online, active, offered)
    }

    private fun parseJob(o: JSONObject) = RiderJob(
        id = o.getString("id"),
        orderNumber = o.optString("order_number").takeIf { it.isNotBlank() },
        status = o.optString("status"),
        pickupAddress = o.optString("pickup_address").takeIf { it.isNotBlank() },
        pickupLat = o.doubleOrNull("pickup_latitude"),
        pickupLng = o.doubleOrNull("pickup_longitude"),
        deliveryAddress = o.optString("delivery_address").takeIf { it.isNotBlank() },
        deliveryLat = o.doubleOrNull("delivery_latitude"),
        deliveryLng = o.doubleOrNull("delivery_longitude"),
        deliveryFee = o.doubleOrNull("delivery_fee")
    )

    private fun JSONObject.doubleOrNull(name: String): Double? =
        if (!has(name) || isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }

    private fun firstOrderId(rows: JSONArray): String? {
        for (i in 0 until rows.length()) {
            val id = rows.optJSONObject(i)?.optString("order_id").orEmpty()
            if (id.isNotBlank()) return id
        }
        return null
    }

    private fun rpcArray(name: String, token: String): JSONArray =
        when (val value = rpc(name, token, JSONObject())) {
            is JSONArray -> value
            is JSONObject -> JSONArray().put(value)
            else -> JSONArray()
        }

    private fun rpc(name: String, token: String, body: JSONObject): Any =
        requestAny("POST", "/rest/v1/rpc/" + name, token, body)

    private fun requestObject(
        method: String,
        path: String,
        token: String?,
        body: JSONObject
    ): JSONObject = requestAny(method, path, token, body) as? JSONObject
        ?: error("รูปแบบข้อมูลไม่ถูกต้อง")

    private fun requestArray(method: String, path: String, token: String): JSONArray =
        requestAny(method, path, token, null) as? JSONArray ?: JSONArray()

    private fun requestAny(method: String, path: String, token: String?, body: JSONObject?): Any {
        val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.setRequestProperty("apikey", PUBLISHABLE_KEY)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer " + token)
            }
            if (method != "GET") {
                connection.setRequestProperty("Prefer", "return=representation")
            }
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
