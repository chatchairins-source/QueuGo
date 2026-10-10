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

class NativeAuthApi(
    private val baseUrl: String = "https://pkypiqhlrmzocysgeqew.supabase.co"
) {
    companion object {
        private const val KEY = "sb_publishable_Vn2Il4jp-iBtzNsGRNY8Lg_Tpvk2Vre"
        private const val TIMEOUT = 15000
    }

    suspend fun registerCustomer(form: NativeCustomerRegistration, deviceId: String): NativeAuth {
        form.validate()
        val identifier = form.email?.trim() ?: form.phone.trim()
        try { withContext(Dispatchers.IO) {
            requestObject("POST", "/auth/v1/signup", null, JSONObject()
                .put("email", form.email?.trim() ?: form.phone.filter(Char::isDigit) + "@auth.queuetech.local")
                .put("password", form.password)
                .put("data", JSONObject().put("name", form.name.trim())
                    .put("phone", form.phone.trim()).put("role", "customer")
                    .put("latitude", form.latitude).put("longitude", form.longitude)))
        } } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            if (failure is NativeAuthHttpException && failure.statusCode in 400..499) throw failure
            throw NativeCustomerSignupUncertainException(failure)
        }
        return try {
            signIn(identifier, form.password, "customer", deviceId)
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            throw NativeCustomerSignupCompletedException(failure)
        }
    }

    suspend fun registerMerchant(
        form: NativeMerchantRegistration,
        deviceId: String,
        skipSignup: Boolean = false
    ): NativeAuth = withContext(Dispatchers.IO) {
        form.validate()
        val authEmail = form.normalizedPhone + "@auth.queuetech.local"
        var accountCheckpoint = skipSignup
        if (!skipSignup) {
            try {
                requestObject(
                    "POST",
                    "/auth/v1/signup",
                    null,
                    JSONObject()
                        .put("email", authEmail)
                        .put("password", form.password)
                        .put(
                            "data",
                            JSONObject()
                                .put("name", form.contactName.trim())
                                .put("phone", form.normalizedPhone)
                                .put("role", "shop")
                                .put("shop_name", form.shopName.trim())
                                .put("category", form.category)
                                .put("shoppingSubcategories", JSONArray(form.shoppingSubcategories.toList()))
                        )
                )
                accountCheckpoint = true
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                if (failure is NativeAuthHttpException && failure.statusCode in 400..499) throw failure
                throw NativeMerchantSignupUncertainException(failure)
            }
        }

        try {
            val auth = requestObject(
                "POST",
                "/auth/v1/token?grant_type=password",
                null,
                JSONObject().put("email", authEmail).put("password", form.password)
            )
            val token = auth.getString("access_token")
            val authUserId = auth.getJSONObject("user").getString("id")

            val existingUsers = requestArray(
                "GET",
                "/rest/v1/users?select=id,name,role,status,auth_user_id&auth_user_id=eq." + enc(authUserId),
                token
            )
            val userRow = if (existingUsers.length() > 0) {
                existingUsers.getJSONObject(0)
            } else {
                val created = requestArray(
                    "POST",
                    "/rest/v1/users?select=id,name,role,status,auth_user_id",
                    token,
                    JSONObject()
                        .put("auth_user_id", authUserId)
                        .put("role", "shop")
                        .put("name", form.contactName.trim())
                        .put("phone", form.normalizedPhone)
                        .put("status", "pending")
                        .put(
                            "metadata",
                            JSONObject()
                                .put("shopName", form.shopName.trim())
                                .put("category", form.category)
                                .put("shoppingSubcategories", JSONArray(form.shoppingSubcategories.toList()))
                                .put("terms_version", NATIVE_MERCHANT_TERMS_VERSION)
                                .put("terms_type", "beta_service")
                                .put("truth_confirmed", true)
                                .put("beta_acknowledged", true)
                        )
                )
                if (created.length() == 0) error("สร้างข้อมูลผู้ใช้ในฐานข้อมูลไม่สำเร็จ")
                created.getJSONObject(0)
            }
            if (userRow.optString("role") != "shop") error("บัญชีนี้ใช้กับแอปร้านค้าไม่ได้")
            if (userRow.optString("status") in setOf("suspended", "deleted"))
                throw NativeSessionInvalidException("บัญชีนี้ถูกระงับหรือปิดใช้งาน")

            val queueGoUserId = userRow.getString("id")
            val currentShops = requestArray(
                "GET",
                "/rest/v1/shop_profiles?select=id,shop_name,public_category,status,latitude,longitude,market_suggested_id,market_suggested_distance_km,market_membership_status" +
                    "&user_id=eq." + enc(queueGoUserId) + "&archived_at=is.null&order=created_at.desc&limit=1",
                token
            )
            val shopRow = if (currentShops.length() > 0) {
                currentShops.getJSONObject(0)
            } else {
                val body = JSONObject()
                    .put("user_id", queueGoUserId)
                    .put("shop_name", form.shopName.trim())
                    .put("status", "pending")
                    .put(
                        "metadata",
                        JSONObject()
                            .put("shopName", form.shopName.trim())
                            .put("category", form.category)
                            .put("shoppingSubcategories", JSONArray(form.shoppingSubcategories.toList()))
                    )
                form.marketRegistration?.let {
                    body.put("latitude", it.latitude)
                    body.put("longitude", it.longitude)
                }
                val created = requestArray(
                    "POST",
                    "/rest/v1/shop_profiles?select=id,shop_name,public_category,status,latitude,longitude,market_suggested_id,market_suggested_distance_km,market_membership_status",
                    token,
                    body
                )
                if (created.length() != 1) error("สร้างบัญชีแล้ว แต่บันทึกข้อมูลร้านไม่สำเร็จ กรุณาลองอีกครั้ง")
                created.getJSONObject(0)
            }
            if (shopRow.optString("status") == "archived")
                error("ร้านเดิมถูกเก็บถาวร กรุณาเริ่มสมัครร้านใหม่จากบัญชีเดิม")

            if (form.category == "market") {
                val market = requireNotNull(form.marketRegistration)
                if (!market.marketId.isNullOrBlank()) {
                    rpc(
                        "queuego_submit_market_membership",
                        token,
                        JSONObject()
                            .put("p_market_id", market.marketId)
                            .put("p_stall_no", market.stallNo?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                            .put("p_zone", market.zone?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                            .put("p_confirmed", true)
                            .put("p_proof_path", JSONObject.NULL)
                    )
                } else {
                    val request = requireNotNull(market.request)
                    rpc(
                        "queuego_request_market",
                        token,
                        JSONObject()
                            .put("p_name", request.name.trim())
                            .put("p_province", request.province.trim())
                            .put("p_district", request.district?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                            .put("p_subdistrict", request.subdistrict?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                            .put("p_address", request.address?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                            .put("p_lat", market.latitude)
                            .put("p_lng", market.longitude)
                            .put(
                                "p_note",
                                "สมัครร้านตลาดสดพร้อมการลงทะเบียนร้านครั้งแรก" +
                                    (market.stallNo?.takeIf { it.isNotBlank() }?.let { " · แผง " + it } ?: "") +
                                    (market.zone?.takeIf { it.isNotBlank() }?.let { " · โซน " + it } ?: "")
                            )
                    )
                }
            }

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
                    queueGoUserId,
                    authUserId,
                    userRow.optString("name").ifBlank { form.contactName.trim() },
                    "shop",
                    userRow.optString("status").ifBlank { "pending" }
                )
            )
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            if (accountCheckpoint) throw NativeMerchantSignupCompletedException(failure)
            throw failure
        }
    }

    suspend fun loadMerchantRegistrationMarkets(
        latitude: Double,
        longitude: Double
    ): List<NativeRegistrationMarket> = withContext(Dispatchers.IO) {
        require(latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            "พิกัดร้านไม่ถูกต้อง กรุณาลองใหม่"
        }
        val rows = requestArray(
            "GET",
            "/rest/v1/markets?select=id,name,address,province,district,subdistrict,latitude,longitude,verified,assignment_radius_km" +
                "&active=eq.true&latitude=not.is.null&longitude=not.is.null&province=eq." + enc("บุรีรัมย์") + "&order=name.asc",
            null
        )
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val lat = row.optDouble("latitude", Double.NaN)
                val lng = row.optDouble("longitude", Double.NaN)
                if (!lat.isFinite() || !lng.isFinite()) continue
                add(
                    NativeRegistrationMarket(
                        row.getString("id"),
                        row.optString("name"),
                        row.optString("address").takeIf { it.isNotBlank() },
                        row.optString("province").takeIf { it.isNotBlank() },
                        row.optString("district").takeIf { it.isNotBlank() },
                        row.optString("subdistrict").takeIf { it.isNotBlank() },
                        lat,
                        lng,
                        row.optDouble("assignment_radius_km", 3.0).takeIf { it.isFinite() && it > 0 } ?: 3.0,
                        nativeDistanceKm(latitude, longitude, lat, lng)
                    )
                )
            }
        }.sortedWith(compareBy<NativeRegistrationMarket> { it.distanceKm }.thenBy { it.name })
    }

    suspend fun recoverPassword(email: String) = withContext(Dispatchers.IO) {
        val clean = email.trim()
        require(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(clean)) {
            "กรุณากรอกอีเมลบัญชีร้านค้า"
        }
        requestObject("POST", "/auth/v1/recover", null, JSONObject().put("email", clean))
        Unit
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
        if (rows.length() == 0) throw NativeSessionInvalidException("ไม่พบบัญชี QueueGo")
        val row = rows.getJSONObject(0)
        if (row.optString("role") != expectedRole) error("บัญชีนี้ใช้กับแอปนี้ไม่ได้")
        if (row.optString("status") in setOf("suspended", "deleted") ||
                (expectedRole == "customer" && row.optString("status") != "active"))
            throw NativeSessionInvalidException("บัญชีนี้ถูกระงับหรือปิดใช้งาน")
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

    suspend fun validate(
        auth: NativeAuth,
        expectedRole: String,
        onSessionRefreshed: (NativeAuth) -> Unit = {}
    ): NativeAuth =
        withContext(Dispatchers.IO) {
            val liveAuth = refreshIfNeeded(auth)
            // Refresh tokens rotate. Preserve the new token even if a later read times out.
            if (liveAuth.session != auth.session) onSessionRefreshed(liveAuth)
            val check = rpc(
                "check_active_session",
                liveAuth.session.accessToken,
                JSONObject().put("p_session_id", liveAuth.session.sessionId)
            )
            val result = when (check) {
                is Boolean -> check
                is JSONArray -> check.opt(0).let { first ->
                    if (first is JSONObject) first.opt("check_active_session") else first
                }
                is JSONObject -> check.opt("check_active_session")
                else -> null
            }
            // Unexpected 200 response shapes are not proof of session revocation.
            val valid = result as? Boolean ?: error("รูปแบบข้อมูล Session ไม่ถูกต้อง")
            if (!valid) throw NativeSessionInvalidException("Session นี้ไม่ได้ใช้งานบนอุปกรณ์นี้แล้ว")
            val rows = requestArray(
                "GET",
                "/rest/v1/users?select=id,name,role,status&auth_user_id=eq." + enc(liveAuth.session.authUserId),
                liveAuth.session.accessToken
            )
            if (rows.length() == 0) throw NativeSessionInvalidException("ไม่พบบัญชี QueueGo")
            val row = rows.getJSONObject(0)
            if (row.optString("role") != expectedRole) throw NativeSessionInvalidException("สิทธิ์บัญชีไม่ตรงกับแอป")
            if (row.optString("status") in setOf("suspended", "deleted") ||
                (expectedRole == "customer" && row.optString("status") != "active"))
                throw NativeSessionInvalidException("บัญชีนี้ถูกระงับหรือปิดใช้งาน")
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

    private fun requestArray(
        method: String,
        path: String,
        token: String?,
        body: JSONObject? = null
    ): JSONArray =
        requestAny(method, path, token, body) as? JSONArray ?: error("รูปแบบข้อมูลบัญชีไม่ถูกต้อง")

    private fun requestAny(method: String, path: String, token: String?, body: JSONObject?): Any {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
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
                val authCode = runCatching { JSONObject(text).optString("error_code").ifBlank { JSONObject(text).optString("code") } }.getOrDefault("")
                if (path.startsWith("/auth/v1/token?grant_type=refresh_token") &&
                    authCode in setOf("refresh_token_not_found", "refresh_token_already_used", "session_not_found", "user_not_found", "user_banned"))
                    throw NativeSessionInvalidException(message)
                throw NativeAuthHttpException(code, message)
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
