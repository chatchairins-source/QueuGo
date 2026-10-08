package com.queuego.rider

import android.content.Context
import android.net.Uri
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
                    result.optJSONObject(0)?.optBoolean("check_active_session", false) == true)) ||
            (result is JSONObject && result.optBoolean("check_active_session", false))
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

        val fields = "id,order_number,status,pickup_address,pickup_latitude,pickup_longitude," +
            "delivery_address,delivery_latitude,delivery_longitude,delivery_fee,market_order_id," +
            "rider_arrived_shop_at,rider_arrived_customer_at"
        val activeRows = requestArray(
            "GET",
            "/rest/v1/orders?select=" + fields +
                "&order_type=eq.shopping&rider_id=eq." + enc(profileId) +
                "&status=in.(rider_assigned,preparing,ready,assigned,picked_up,in_progress)" +
                "&order=created_at.desc&limit=1",
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
                    "/rest/v1/orders?select=" + fields + "&id=eq." + enc(offeredId) + "&limit=1",
                    auth.session.accessToken
                )
                if (rows.length() > 0) offered = parseJob(rows.getJSONObject(0))
            }
        }
        RiderSnapshot(online, active, offered)
    }

    suspend fun orderItems(auth: QueueGoAuth, orderId: String): List<RiderItem> =
        withContext(Dispatchers.IO) {
            val rows = requestArray(
                "GET",
                "/rest/v1/order_items?select=item_name,description,quantity,unit_price,total_price,item_image" +
                    "&order_id=eq." + enc(orderId) + "&order=created_at.asc",
                auth.session.accessToken
            )
            buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val qty = row.optInt("quantity", 1).coerceAtLeast(1)
                    val unit = row.doubleOrZero("unit_price")
                    val total = if (row.has("total_price") && !row.isNull("total_price")) {
                        row.doubleOrZero("total_price")
                    } else unit * qty
                    add(
                        RiderItem(
                            name = row.optString("item_name").ifBlank { "รายการ" },
                            description = row.optString("description").takeIf { it.isNotBlank() },
                            quantity = qty,
                            unitPrice = unit,
                            totalPrice = total,
                            imageUrl = row.optString("item_image").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }

    suspend fun acceptOffer(auth: QueueGoAuth, orderId: String) = withContext(Dispatchers.IO) {
        actionOnce(
            auth,
            "claim",
            JSONObject().put("p_order_id", orderId)
        )
    }

    suspend fun declineOffer(auth: QueueGoAuth, orderId: String) = withContext(Dispatchers.IO) {
        rpc(
            "qg_rider_decline_offer",
            auth.session.accessToken,
            JSONObject().put("p_order_id", orderId)
        )
    }

    suspend fun markArrival(
        auth: QueueGoAuth,
        orderId: String,
        target: String,
        latitude: Double?,
        longitude: Double?
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_order_id", orderId)
            .put("p_target", target)
            .putNullable("p_lat", latitude)
            .putNullable("p_lng", longitude)
        rpc("qg_rider_mark_arrival", auth.session.accessToken, body)
    }

    suspend fun startDelivery(auth: QueueGoAuth, orderId: String) = withContext(Dispatchers.IO) {
        actionOnce(
            auth,
            "order",
            JSONObject().put("p_order_id", orderId).put("p_action", "deliver")
        )
    }

    suspend fun pickupWithPhoto(
        context: Context,
        auth: QueueGoAuth,
        orderId: String,
        photoUri: Uri,
        latitude: Double?,
        longitude: Double?
    ) = withContext(Dispatchers.IO) {
        val path = uploadEvidence(context, auth, photoUri)
        val body = JSONObject()
            .put("p_order_id", orderId)
            .put("p_photo_path", path)
            .putNullable("p_lat", latitude)
            .putNullable("p_lng", longitude)
        rpc("qg_pickup_with_photo", auth.session.accessToken, body)
    }

    suspend fun completeWithPhoto(
        context: Context,
        auth: QueueGoAuth,
        orderId: String,
        photoUri: Uri,
        latitude: Double?,
        longitude: Double?
    ) = withContext(Dispatchers.IO) {
        val path = uploadEvidence(context, auth, photoUri)
        val body = JSONObject()
            .put("p_order_id", orderId)
            .put("p_photo_path", path)
            .putNullable("p_lat", latitude)
            .putNullable("p_lng", longitude)
        rpc("qg_complete_with_photo", auth.session.accessToken, body)
    }

    private fun actionOnce(auth: QueueGoAuth, kind: String, payload: JSONObject): Any {
        val seed = auth.user.id + "|" + kind + "|" + payload.toString()
        val requestId = UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
        return rpc(
            "qg_rider_action_once",
            auth.session.accessToken,
            JSONObject()
                .put("p_request_id", requestId)
                .put("p_kind", kind)
                .put("p_payload", payload)
        )
    }

    private fun uploadEvidence(context: Context, auth: QueueGoAuth, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("อ่านรูปหลักฐานไม่ได้")
        if (bytes.isEmpty()) error("รูปหลักฐานว่าง")
        if (bytes.size > 5 * 1024 * 1024) error("รูปหลักฐานต้องมีขนาดไม่เกิน 5 MB")

        val path = auth.session.authUserId + "/" + UUID.randomUUID() + ".jpg"
        val connection = URL(BASE_URL + "/storage/v1/object/qg-evidence/" + path)
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.doOutput = true
            connection.setRequestProperty("apikey", PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer " + auth.session.accessToken)
            connection.setRequestProperty("Content-Type", "image/jpeg")
            connection.setRequestProperty("x-upsert", "false")
            connection.outputStream.use { it.write(bytes) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val text = connection.errorStream?.use {
                    BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText()
                }.orEmpty()
                val message = runCatching { JSONObject(text).optString("message") }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "อัปโหลดหลักฐานไม่สำเร็จ"
                error(message)
            }
            return path
        } finally {
            connection.disconnect()
        }
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
        deliveryFee = o.doubleOrNull("delivery_fee"),
        marketOrderId = o.optString("market_order_id").takeIf { it.isNotBlank() },
        arrivedShopAt = o.optString("rider_arrived_shop_at").takeIf { it.isNotBlank() },
        arrivedCustomerAt = o.optString("rider_arrived_customer_at").takeIf { it.isNotBlank() }
    )

    private fun JSONObject.doubleOrNull(name: String): Double? =
        if (!has(name) || isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }

    private fun JSONObject.doubleOrZero(name: String): Double =
        if (!has(name) || isNull(name)) 0.0 else optDouble(name, 0.0)

    private fun JSONObject.putNullable(name: String, value: Double?): JSONObject {
        if (value == null) put(name, JSONObject.NULL) else put(name, value)
        return this
    }

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
                else -> value.trim(34.toChar())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
