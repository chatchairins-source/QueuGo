package com.queuetech.queuego.core.network

import com.queuetech.queuego.core.model.AppRole
import com.queuetech.queuego.core.model.QueueGoProduct
import com.queuetech.queuego.core.model.QueueGoShop
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

    suspend fun loadActiveShops(): List<QueueGoShop> {
        val raw = request(
            "GET",
            "/rest/v1/shop_profiles?select=id,shop_name,status,public_category,public_subcategories,public_logo,public_cover,public_open_time,public_close_time,public_description,address,latitude,longitude,delivery_enabled&status=eq.active&order=shop_name.asc",
        )
        val rows = JSONArray(raw)
        val openStates = runCatching {
            val stateRows = JSONArray(
                request("GET", "/rest/v1/shop_open_states?select=shop_id,is_open,resume_at")
            )
            buildMap<String, Boolean> {
                for (index in 0 until stateRows.length()) {
                    val item = stateRows.getJSONObject(index)
                    put(item.getString("shop_id"), item.optBoolean("is_open", true))
                }
            }
        }.getOrDefault(emptyMap())

        return buildList {
            for (index in 0 until rows.length()) {
                val item = rows.getJSONObject(index)
                if (item.optString("status") != "active") continue
                val subcategories = item.optJSONArray("public_subcategories")
                add(
                    QueueGoShop(
                        id = item.getString("id"),
                        name = item.optString("shop_name").ifBlank { "ร้านค้า" },
                        category = item.optString("public_category").ifBlank { "other" },
                        subcategories = buildList {
                            if (subcategories != null) {
                                for (subIndex in 0 until subcategories.length()) {
                                    add(subcategories.optString(subIndex))
                                }
                            }
                        }.filter(String::isNotBlank),
                        description = item.optString("public_description"),
                        address = item.optString("address"),
                        logoUrl = item.optString("public_logo"),
                        coverUrl = item.optString("public_cover"),
                        latitude = item.optNullableDouble("latitude"),
                        longitude = item.optNullableDouble("longitude"),
                        deliveryEnabled = item.optBoolean("delivery_enabled", true),
                        isOpen = openStates[item.getString("id")] ?: true,
                    )
                )
            }
        }
    }

    suspend fun loadProductsForShop(shopId: String): List<QueueGoProduct> {
        val encodedShopId = java.net.URLEncoder.encode(shopId, Charsets.UTF_8.name())
        val raw = request(
            "GET",
            "/rest/v1/products?select=id,shop_id,name,description,price,delivery_price,image,available,delivery_available&shop_id=eq.$encodedShopId&delivery_available=eq.true&order=created_at.asc",
        )
        val rows = JSONArray(raw)
        return buildList {
            for (index in 0 until rows.length()) {
                val item = rows.getJSONObject(index)
                val deliveryAvailable = item.optBoolean("delivery_available", true)
                if (!deliveryAvailable) continue
                add(
                    QueueGoProduct(
                        id = item.getString("id"),
                        shopId = item.getString("shop_id"),
                        name = item.optString("name").ifBlank { "สินค้า" },
                        description = item.optString("description"),
                        price = item.optDouble("price", 0.0).coerceAtLeast(0.0),
                        deliveryPrice = item.optNullableDouble("delivery_price"),
                        imageUrl = item.optString("image"),
                        available = item.optBoolean("available", true),
                        deliveryAvailable = deliveryAvailable,
                    )
                )
            }
        }
    }

    suspend fun rpc(accessToken: String, functionName: String, body: JSONObject = JSONObject()): String =
        request("POST", "/rest/v1/rpc/$functionName", accessToken, body.toString())

    suspend fun rest(
        accessToken: String,
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        preferRepresentation: Boolean = false,
    ): String = request(
        method = method,
        path = "/rest/v1/$path",
        accessToken = accessToken,
        body = body?.toString(),
        preferRepresentation = preferRepresentation,
    )

    suspend fun uploadEvidenceJpeg(
        accessToken: String,
        authUserId: String,
        jpegBytes: ByteArray,
    ): String = withContext(Dispatchers.IO) {
        require(jpegBytes.isNotEmpty()) { "ไม่พบข้อมูลรูปหลักฐาน" }
        require(jpegBytes.size <= 5 * 1024 * 1024) {
            "รูปหลักฐานต้องมีขนาดไม่เกิน 5 MB"
        }

        val path = authUserId + "/" + java.util.UUID.randomUUID().toString() + ".jpg"
        val connection = (
            URL(baseUrl + "/storage/v1/object/qg-evidence/" + path).openConnection()
                as HttpURLConnection
        ).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            useCaches = false
            doOutput = true
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "image/jpeg")
            setRequestProperty("x-upsert", "false")
        }

        try {
            connection.outputStream.use { it.write(jpegBytes) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw QueueGoApiException(code, parseError(text, code))
            }
            path
        } finally {
            connection.disconnect()
        }
    }

    suspend fun claimActiveSession(
        accessToken: String,
        sessionId: String,
        deviceId: String,
    ): Boolean {
        val raw = rpc(
            accessToken,
            "claim_active_session",
            JSONObject()
                .put("p_session_id", sessionId)
                .put("p_device_id", deviceId),
        )
        val rows = runCatching { JSONArray(raw) }.getOrNull() ?: return false
        return rows.length() > 0 && rows.optJSONObject(0)?.optBoolean("success", false) == true
    }

    suspend fun checkActiveSession(accessToken: String, sessionId: String): Boolean =
        parseRpcBoolean(
            rpc(
                accessToken,
                "check_active_session",
                JSONObject().put("p_session_id", sessionId),
            ),
        )

    suspend fun touchActiveSession(accessToken: String, sessionId: String): Boolean =
        parseRpcBoolean(
            rpc(
                accessToken,
                "touch_active_session",
                JSONObject().put("p_session_id", sessionId),
            ),
        )

    private fun parseRpcBoolean(raw: String): Boolean {
        val clean = raw.trim()
        if (clean.equals("true", ignoreCase = true)) return true
        if (clean.equals("false", ignoreCase = true)) return false

        val array = runCatching { JSONArray(clean) }.getOrNull()
        if (array != null && array.length() > 0) {
            val first = array.opt(0)
            if (first is Boolean) return first
            if (first is JSONObject) {
                val keys = first.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (first.opt(key) is Boolean) return first.optBoolean(key)
                }
            }
        }

        val obj = runCatching { JSONObject(clean) }.getOrNull()
        if (obj != null) {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (obj.opt(key) is Boolean) return obj.optBoolean(key)
            }
        }
        return false
    }

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
        preferRepresentation: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            useCaches = false
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (preferRepresentation) setRequestProperty("Prefer", "return=representation")
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

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key).takeIf(Double::isFinite)
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
