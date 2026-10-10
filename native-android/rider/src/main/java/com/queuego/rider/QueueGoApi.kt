package com.queuego.rider

import com.queuego.shared.nativeOrderNumber

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
                "/rest/v1/users?select=id,name,phone,role,status&auth_user_id=eq." + enc(authUserId),
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
                    status = row.optString("status"),
                    phone = row.optString("phone").takeIf { it.isNotBlank() }
                )
            )
        }

    internal suspend fun registrationProfileMissing(auth: QueueGoAuth): Boolean = withContext(Dispatchers.IO) {
        require(auth.user.role == "rider" && auth.user.status == "pending")
        requestArray("GET", "/rest/v1/rider_profiles?select=user_id&user_id=eq." + enc(auth.user.id), auth.session.accessToken).length() == 0
    }

    internal suspend fun registerRider(
        form: RiderRegistrationForm, password: String, documents: Map<String, String>,
        deviceId: String, existing: QueueGoAuth?, checkpoint: suspend (QueueGoAuth) -> Unit
    ): QueueGoAuth = withContext(Dispatchers.IO) {
        for (step in 0..2) require(form.validate(step, password, documents.keys, existing != null) == null) {
            form.validate(step, password, documents.keys, existing != null).orEmpty()
        }
        val email = form.email.trim().lowercase().ifBlank { form.normalizedPhone + "@auth.queuetech.local" }
        val auth = if (existing != null) validate(existing) else {
            // No automatic signup replay: if Auth succeeded but its reply was lost,
            // the owner can log in with the same credentials and resume this draft.
            val signup = requestObject("POST", "/auth/v1/signup", null,
                JSONObject().put("email", email).put("password", password)
                    .put("data", JSONObject().put("name", form.name.trim()).put("phone", form.normalizedPhone).put("role", "rider")))
            val token = signup.optString("access_token").ifBlank {
                requestObject("POST", "/auth/v1/token?grant_type=password", null,
                    JSONObject().put("email",email).put("password",password)).getString("access_token")
            }
            val authId = signup.optJSONObject("user")?.optString("id").orEmpty()
            require(authId.isNotBlank()) { "สร้างบัญชี Supabase Authentication ไม่สำเร็จ" }
            val users = requestArray("GET", "/rest/v1/users?select=id,role,status&auth_user_id=eq." + enc(authId), token)
            if (users.length() == 0) requestAny("POST", "/rest/v1/users", token,
                JSONObject().put("auth_user_id",authId).put("role","rider").put("name",form.name.trim())
                    .put("phone",form.normalizedPhone).put("status","pending"))
            signIn(email, password, deviceId)
        }
        require(auth.user.role == "rider" && auth.user.status == "pending") { "บัญชีนี้ไม่ใช่ใบสมัครไรเดอร์ที่รออนุมัติ" }
        checkpoint(auth)
        val metadata = JSONObject().put("source","rider-registration")
            .put("email",form.email.trim().ifBlank { null }).put("serviceArea",form.area.trim().ifBlank { null })
            .put("serviceProvince",form.province).put("serviceDistrict",form.district.trim())
            .put("vehicleType",form.vehicle).put("vehiclePlate",form.plate.trim())
            .put("vehicleMake",form.make.trim().ifBlank { null }).put("vehicleModel",form.model.trim().ifBlank { null })
            .put("requestedCapacityKg",form.capacity.ifBlank { "0" }.toDouble())
            .put("applicationStatus","pending").put("applicationSubmittedAt",System.currentTimeMillis())
            .put("available",false).put("online",false)
        val imageKeys = listOf("profileImage","idCardFrontImage","idCardBackImage","driverLicenseImage","compulsoryInsuranceImage","vehiclePhoto")
        RIDER_DOCUMENTS.keys.forEachIndexed { index, key -> metadata.put(imageKeys[index],documents.getValue(key)) }
        val body = JSONObject().put("user_id",auth.user.id).put("rider_name",form.name.trim())
            .put("phone",form.normalizedPhone).put("vehicle_type",form.vehicle).put("vehicle_plate",form.plate.trim())
            .put("address",form.area.trim().ifBlank { null }).put("status","pending").put("metadata",metadata)
        val before = requestArray("GET","/rest/v1/rider_profiles?select=user_id,status&user_id=eq." + enc(auth.user.id),auth.session.accessToken)
        val result = if (before.length() == 0) {
            requestAny("POST","/rest/v1/rider_profiles",auth.session.accessToken,body)
        } else {
            require(before.getJSONObject(0).optString("status") == "pending") { "ใบสมัครนี้ถูกตรวจสอบแล้ว กรุณาเข้าสู่ระบบอีกครั้ง" }
            body.remove("user_id"); body.remove("status")
            requestAny("PATCH","/rest/v1/rider_profiles?status=eq.pending&user_id=eq." + enc(auth.user.id),auth.session.accessToken,body)
        }
        require(result is JSONArray && result.length() == 1) { "บันทึกใบสมัครไม่สำเร็จ กรุณาลองใหม่" }
        auth
    }

    suspend fun validate(auth: QueueGoAuth): QueueGoAuth = withContext(Dispatchers.IO) {
        val liveAuth = refreshIfNeeded(auth)
        val result = rpc(
            "check_active_session",
            liveAuth.session.accessToken,
            JSONObject().put("p_session_id", liveAuth.session.sessionId)
        )
        val valid = result == true ||
            (result is JSONArray && result.length() > 0 &&
                (result.optBoolean(0, false) ||
                    result.optJSONObject(0)?.optBoolean("check_active_session", false) == true)) ||
            (result is JSONObject && result.optBoolean("check_active_session", false))
        if (!valid) throw RiderSessionInvalidException("บัญชีนี้ถูกเข้าสู่ระบบจากอุปกรณ์อื่น")

        val users = requestArray(
            "GET",
            "/rest/v1/users?select=id,name,phone,role,status&auth_user_id=eq." + enc(liveAuth.session.authUserId),
            liveAuth.session.accessToken
        )
        if (users.length() == 0) throw RiderSessionInvalidException("ไม่พบบัญชี QueueGo")
        val row = users.getJSONObject(0)
        if (row.optString("role") != "rider") throw RiderSessionInvalidException("สิทธิ์บัญชีไม่ถูกต้อง")
        liveAuth.copy(
            user = QueueGoUser(
                id = row.getString("id"),
                name = row.optString("name").ifBlank { "ไรเดอร์" },
                role = "rider",
                status = row.optString("status"),
                phone = row.optString("phone").takeIf { it.isNotBlank() }
            )
        )
    }

    private fun refreshIfNeeded(auth: QueueGoAuth): QueueGoAuth {
        val session = auth.session
        val refresh = session.refreshToken?.takeIf { it.isNotBlank() } ?: return auth
        if (session.expiresAtMs > System.currentTimeMillis() + 60_000L) return auth

        val renewed = try { requestObject(
            "POST",
            "/auth/v1/token?grant_type=refresh_token",
            null,
            JSONObject().put("refresh_token", refresh)
        ) } catch (e: RiderHttpException) {
            if (e.statusCode == 400 || e.statusCode == 401) {
                throw RiderSessionInvalidException("Session Rider หมดอายุ กรุณาเข้าสู่ระบบอีกครั้ง")
            }
            throw e
        }
        val nextAccess = renewed.optString("access_token")
        if (nextAccess.isBlank()) error("ไม่สามารถต่ออายุ Session Rider ได้")
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

    suspend fun subscribeNativePush(
        auth: QueueGoAuth,
        deviceId: String,
        token: String
    ) = withContext(Dispatchers.IO) {
        require(deviceId.matches(Regex("^[0-9a-fA-F-]{36}$"))) { "รหัสอุปกรณ์แจ้งเตือนไม่ถูกต้อง" }
        require(token.length in 16..4096) { "Push token ไม่ถูกต้อง" }
        requestAny(
            "POST",
            "/functions/v1/queuego-push",
            auth.session.accessToken,
            JSONObject()
                .put("action", "subscribe-native")
                .put("deviceId", deviceId)
                .put("platform", "android")
                .put("token", token)
                .put("sessionId", auth.session.sessionId)
        )
        Unit
    }

    suspend fun testNativePush(auth: QueueGoAuth) = withContext(Dispatchers.IO) {
        requestAny(
            "POST",
            "/functions/v1/queuego-push",
            auth.session.accessToken,
            JSONObject().put("action", "test")
        )
        Unit
    }

    suspend fun unsubscribeNativePush(
        auth: QueueGoAuth,
        deviceId: String
    ) = withContext(Dispatchers.IO) {
        if (!deviceId.matches(Regex("^[0-9a-fA-F-]{36}$"))) return@withContext
        requestAny(
            "POST",
            "/functions/v1/queuego-push",
            auth.session.accessToken,
            JSONObject()
                .put("action", "unsubscribe-native")
                .put("deviceId", deviceId)
        )
        Unit
    }

    suspend fun setOnline(
        auth: QueueGoAuth,
        online: Boolean,
        latitude: Double?,
        longitude: Double?
    ) = withContext(Dispatchers.IO) {
        val profiles = requestArray(
            "GET",
            "/rest/v1/rider_profiles?select=id,rider_name,phone,vehicle_type,vehicle_plate,vehicle_status,vehicle_verified_at,metadata&user_id=eq." + enc(auth.user.id) + "&limit=1",
            auth.session.accessToken
        )
        if (profiles.length() == 0) error("ไม่พบโปรไฟล์ Rider")
        val row = profiles.getJSONObject(0)
        if (!online) {
            // Going offline must release a live sequential offer immediately instead of
            // blocking the next Rider until the 30-second lease expires.
            val liveOffer = runCatching {
                rpcArray("qg_get_my_rider_offer", auth.session.accessToken)
            }.getOrDefault(JSONArray())
            firstOrderId(liveOffer)?.let { offeredId ->
                runCatching {
                    rpc(
                        "qg_rider_decline_offer",
                        auth.session.accessToken,
                        JSONObject().put("p_order_id", offeredId)
                    )
                }
            }
        }
        val metadata = row.optJSONObject("metadata") ?: JSONObject()
        metadata.put("online", online).put("available", online)
        val body = JSONObject().put("metadata", metadata)
        if (latitude != null) body.put("latitude", latitude)
        if (longitude != null) body.put("longitude", longitude)
        requestAny(
            "PATCH",
            "/rest/v1/rider_profiles?user_id=eq." + enc(auth.user.id),
            auth.session.accessToken,
            body
        )
        Unit
    }

    suspend fun updateLocation(
        auth: QueueGoAuth,
        latitude: Double,
        longitude: Double
    ) = withContext(Dispatchers.IO) {
        requestAny(
            "PATCH",
            "/rest/v1/rider_profiles?user_id=eq." + enc(auth.user.id),
            auth.session.accessToken,
            JSONObject().put("latitude", latitude).put("longitude", longitude)
        )
        Unit
    }

    suspend fun periodSummary(auth: QueueGoAuth, days: Int = 1): RiderPeriodSummary =
        withContext(Dispatchers.IO) {
            val raw = rpc(
                "qg_rider_period_summary",
                auth.session.accessToken,
                JSONObject().put("p_days", days)
            )
            val o = when (raw) {
                is JSONObject -> raw
                is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
                else -> JSONObject()
            }
            RiderPeriodSummary(
                days = o.optInt("days", days),
                jobs = o.optInt("jobs", 0),
                income = o.optDouble("income", 0.0),
                onlineHours = o.optDouble("online_hours", 0.0),
                incomePerHour = if (o.has("income_per_hour") && !o.isNull("income_per_hour"))
                    o.optDouble("income_per_hour").takeIf { !it.isNaN() } else null
            )
        }

    suspend fun history(auth: QueueGoAuth, limit: Int = 50): List<RiderHistoryOrder> =
        withContext(Dispatchers.IO) {
            val profiles = requestArray(
                "GET",
                "/rest/v1/rider_profiles?select=id&user_id=eq." + enc(auth.user.id) + "&limit=1",
                auth.session.accessToken
            )
            if (profiles.length() == 0) return@withContext emptyList()
            val profileId = profiles.getJSONObject(0).getString("id")
            val rows = requestArray(
                "GET",
                "/rest/v1/orders?select=id,order_number,pickup_address,delivery_address,delivery_fee,updated_at" +
                    "&rider_id=eq." + enc(profileId) +
                    "&status=eq.completed&order=updated_at.desc&limit=" + limit.coerceIn(1, 200),
                auth.session.accessToken
            )
            buildList {
                for (i in 0 until rows.length()) {
                    val r = rows.optJSONObject(i) ?: continue
                    val id = r.optString("id")
                    val raw = r.optString("order_number")
                    val number = nativeOrderNumber(raw)
                    add(
                        RiderHistoryOrder(
                            id = id,
                            number = number,
                            pickupAddress = r.optString("pickup_address").takeIf { it.isNotBlank() },
                            deliveryAddress = r.optString("delivery_address").takeIf { it.isNotBlank() },
                            deliveryFee = r.optDouble("delivery_fee", 0.0),
                            completedAt = r.optString("updated_at").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }

    suspend fun cashLedger(
        auth: QueueGoAuth,
        fromDate: String,
        toDate: String
    ): List<RiderCashLedgerEntry> = withContext(Dispatchers.IO) {
        val raw = rpc(
            "qg_rider_cash_ledger",
            auth.session.accessToken,
            JSONObject()
                .put("p_from", fromDate)
                .put("p_to", toDate)
        )
        val rows = when (raw) {
            is JSONArray -> raw
            is JSONObject -> JSONArray().put(raw)
            else -> JSONArray()
        }
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val orderId = row.optString("order_id")
                if (orderId.isBlank()) continue
                add(
                    RiderCashLedgerEntry(
                        orderId = orderId,
                        orderNumber = row.optString("order_number").takeIf { it.isNotBlank() },
                        status = row.optString("status"),
                        deliveryFee = row.doubleOrZero("delivery_fee"),
                        cashPaidMerchant = row.doubleOrZero("cash_paid_merchant"),
                        cashCollectedCustomer = row.doubleOrZero("cash_collected_customer"),
                        createdAt = row.optString("created_at").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    suspend fun updateAccount(
        auth: QueueGoAuth,
        name: String,
        phone: String
    ) = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        val cleanPhone = phone.filter(Char::isDigit)
        require(cleanName.length >= 2) { "กรุณาระบุชื่ออย่างน้อย 2 ตัวอักษร" }
        require(Regex("^0\\d{9}$").matches(cleanPhone)) { "เบอร์โทรศัพท์ต้องมี 10 หลักและขึ้นต้นด้วย 0" }
        requestAny(
            "PATCH",
            "/rest/v1/users?auth_user_id=eq." + enc(auth.session.authUserId),
            auth.session.accessToken,
            JSONObject().put("name", cleanName).put("phone", cleanPhone)
        )
        requestAny(
            "PATCH",
            "/rest/v1/rider_profiles?user_id=eq." + enc(auth.user.id),
            auth.session.accessToken,
            JSONObject().put("rider_name", cleanName).put("phone", cleanPhone)
        )
        Unit
    }

    suspend fun riderSnapshot(auth: QueueGoAuth): RiderSnapshot = withContext(Dispatchers.IO) {
        val profiles = requestArray(
            "GET",
            "/rest/v1/rider_profiles?select=id,rider_name,phone,vehicle_type,vehicle_plate,vehicle_status,vehicle_verified_at,metadata&user_id=eq." + enc(auth.user.id) + "&limit=1",
            auth.session.accessToken
        )
        if (profiles.length() == 0) return@withContext RiderSnapshot(false, null, null, emptyList())

        val profile = profiles.getJSONObject(0)
        val profileId = profile.getString("id")
        val online = profile.optJSONObject("metadata")?.optBoolean("online", false) ?: false

        val fields = "id,order_number,status,pickup_address,pickup_latitude,pickup_longitude," +
            "delivery_address,delivery_latitude,delivery_longitude,delivery_fee,subtotal,total_amount,market_order_id," +
            "rider_arrived_shop_at,rider_arrived_customer_at"
        val activeRows = requestArray(
            "GET",
            "/rest/v1/orders?select=" + fields +
                "&order_type=eq.shopping&rider_id=eq." + enc(profileId) +
                "&status=in.(rider_assigned,preparing,ready,assigned,picked_up,in_progress)" +
                "&order=created_at.desc&limit=1",
            auth.session.accessToken
        )
        var active = if (activeRows.length() > 0) parseJob(activeRows.getJSONObject(0)) else null
        var marketPickups = emptyList<MarketPickup>()
        if (active?.marketOrderId != null) {
            marketPickups = marketPickupRoute(auth, active.marketOrderId)
            val parents = requestArray(
                "GET",
                "/rest/v1/market_orders?select=delivery_fee,rider_bonus,total_amount&id=eq." +
                    enc(active.marketOrderId) + "&limit=1",
                auth.session.accessToken
            )
            if (parents.length() > 0) {
                val parent = parents.getJSONObject(0)
                active = active.copy(
                    deliveryFee = parent.doubleOrZero("delivery_fee") +
                        parent.doubleOrZero("rider_bonus"),
                    shopCash = null,
                    customerCash = parent.doubleOrNull("total_amount")
                )
            }
        }

        var offered: RiderJob? = null
        if (active == null && online) {
            // Server is the single source of truth for sequential Rider offers.
            // Never expose a shared pool: only the Rider selected by qg_dispatch_rider_offers()
            // can see and accept this live 30-second offer.
            val offer = rpcArray("qg_get_my_rider_offer", auth.session.accessToken)
            val offeredId = firstOrderId(offer)
            val offerExpiry = firstOfferExpiry(offer)
            if (offeredId != null && offerExpiry != null) {
                val rows = requestArray(
                    "GET",
                    "/rest/v1/orders?select=" + fields + "&id=eq." + enc(offeredId) + "&limit=1",
                    auth.session.accessToken
                )
                if (rows.length() > 0) {
                    offered = parseJob(rows.getJSONObject(0)).copy(offerExpiresAt = offerExpiry)
                }
            }
        }
        val laundry = loadLaundryState(
            auth = auth,
            profileId = profileId,
            online = online,
            hasNormalActive = active != null
        )
        val profileInfo = RiderProfileInfo(
            profileId = profileId,
            riderName = profile.optString("rider_name").takeIf { it.isNotBlank() },
            phone = profile.optString("phone").takeIf { it.isNotBlank() },
            vehicleType = profile.optString("vehicle_type").takeIf { it.isNotBlank() },
            vehiclePlate = profile.optString("vehicle_plate").takeIf { it.isNotBlank() },
            vehicleStatus = profile.optString("vehicle_status").takeIf { it.isNotBlank() },
            vehicleVerifiedAt = profile.optString("vehicle_verified_at").takeIf { it.isNotBlank() }
        )
        RiderSnapshot(online, active, offered, marketPickups, profileId, laundry, profileInfo)
    }

    suspend fun setLaundryMode(auth: QueueGoAuth, enabled: Boolean) = withContext(Dispatchers.IO) {
        rpc(
            "queuego_set_laundry_rider_mode",
            auth.session.accessToken,
            JSONObject().put("p_enabled", enabled)
        )
        Unit
    }

    suspend fun laundryInviteAction(
        auth: QueueGoAuth,
        inviteId: String,
        accept: Boolean
    ) = withContext(Dispatchers.IO) {
        rpc(
            "queuego_laundry_rider_invite_action",
            auth.session.accessToken,
            JSONObject()
                .put("p_invite_id", inviteId)
                .put("p_accept", accept)
        )
        Unit
    }

    suspend fun claimLaundryJob(auth: QueueGoAuth, jobId: String) = withContext(Dispatchers.IO) {
        rpc(
            "queuego_claim_laundry_job",
            auth.session.accessToken,
            JSONObject().put("p_job_id", jobId)
        )
        Unit
    }

    suspend fun laundryAction(
        auth: QueueGoAuth,
        jobId: String,
        action: String
    ) = withContext(Dispatchers.IO) {
        require(action in setOf("arrive", "collect", "deliver")) { "สถานะงานฝากซักไม่ถูกต้อง" }
        rpc(
            "queuego_laundry_rider_action",
            auth.session.accessToken,
            JSONObject()
                .put("p_job_id", jobId)
                .put("p_action", action)
        )
        Unit
    }

    suspend fun verificationDetails(auth: QueueGoAuth, orderId: String): RiderVerificationDetails =
        withContext(Dispatchers.IO) {
            val order = requestArray("GET", "/rest/v1/orders?select=shop_id,customer_id,note&id=eq." + enc(orderId) + "&limit=1", auth.session.accessToken)
                .optJSONObject(0) ?: error("ไม่พบข้อมูลออเดอร์ที่มีสิทธิ์อ่าน")
            val shopId = order.optString("shop_id").takeIf { it.isNotBlank() && it != "null" }
            val shop = shopId?.let { requestArray("GET", "/rest/v1/shop_profiles?select=shop_name,public_logo&id=eq." + enc(it) + "&limit=1", auth.session.accessToken).optJSONObject(0) }
            val contact = requestAny("POST", "/rest/v1/rpc/qg_rider_order_contact", auth.session.accessToken, JSONObject().put("p_order_id", orderId))
            val customer = when (contact) {
                is JSONObject -> contact
                is JSONArray -> contact.optJSONObject(0)
                else -> null
            }
            fun text(row: JSONObject?, key: String): String? = row?.optString(key)?.takeIf { it.isNotBlank() && it != "null" }
            RiderVerificationDetails(text(shop, "shop_name"), text(shop, "public_logo"), text(customer, "name"), text(customer, "phone"), cleanRiderOrderNote(text(order, "note")))
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

    suspend fun acceptOffer(auth: QueueGoAuth, job: RiderJob) = withContext(Dispatchers.IO) {
        actionOnce(
            auth,
            if (job.marketOrderId != null) "market_claim" else "claim",
            JSONObject().put("p_order_id", job.id)
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

    suspend fun startDelivery(auth: QueueGoAuth, job: RiderJob) = withContext(Dispatchers.IO) {
        if (job.marketOrderId != null) {
            actionOnce(
                auth,
                "market",
                JSONObject()
                    .put("p_market_order_id", job.marketOrderId)
                    .put("p_action", "deliver")
            )
        } else {
            actionOnce(
                auth,
                "order",
                JSONObject().put("p_order_id", job.id).put("p_action", "deliver")
            )
        }
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
        job: RiderJob,
        photoUri: Uri,
        latitude: Double?,
        longitude: Double?
    ) = withContext(Dispatchers.IO) {
        val path = uploadEvidence(context, auth, photoUri)
        if (job.marketOrderId != null) {
            val body = JSONObject()
                .put("p_market_order_id", job.marketOrderId)
                .put("p_photo_path", path)
                .putNullable("p_lat", latitude)
                .putNullable("p_lng", longitude)
            rpc("qg_complete_market_with_photo", auth.session.accessToken, body)
        } else {
            val body = JSONObject()
                .put("p_order_id", job.id)
                .put("p_photo_path", path)
                .putNullable("p_lat", latitude)
                .putNullable("p_lng", longitude)
            rpc("qg_complete_with_photo", auth.session.accessToken, body)
        }
    }

    suspend fun marketPickupWithPhoto(
        context: Context,
        auth: QueueGoAuth,
        pickup: MarketPickup,
        photoUri: Uri,
        latitude: Double?,
        longitude: Double?
    ) = withContext(Dispatchers.IO) {
        val path = uploadEvidence(context, auth, photoUri)
        val body = JSONObject()
            .put("p_pickup_id", pickup.pickupId)
            .put("p_amount", pickup.shopAmount)
            .put("p_photo_path", path)
            .putNullable("p_lat", latitude)
            .putNullable("p_lng", longitude)
        rpc("qg_market_pickup_with_photo", auth.session.accessToken, body)
    }

    private fun loadLaundryState(
        auth: QueueGoAuth,
        profileId: String,
        online: Boolean,
        hasNormalActive: Boolean
    ): RiderLaundryState {
        val prefRows = runCatching {
            requestArray(
                "GET",
                "/rest/v1/laundry_rider_preferences?select=laundry_mode_enabled" +
                    "&rider_id=eq." + enc(profileId) + "&limit=1",
                auth.session.accessToken
            )
        }.getOrDefault(JSONArray())
        val modeEnabled = prefRows.optJSONObject(0)?.optBoolean("laundry_mode_enabled", false) == true

        val invitesRaw = runCatching {
            rpc("queuego_laundry_rider_invites", auth.session.accessToken, JSONObject())
        }.getOrNull()
        val inviteRows = when (invitesRaw) {
            is JSONArray -> invitesRaw
            is JSONObject -> JSONArray().put(invitesRaw)
            else -> JSONArray()
        }
        val invites = buildList {
            for (i in 0 until inviteRows.length()) {
                val row = inviteRows.optJSONObject(i) ?: continue
                val inviteId = row.optString("invite_id")
                val hubId = row.optString("hub_id")
                if (inviteId.isBlank() || hubId.isBlank()) continue
                add(
                    RiderLaundryInvite(
                        inviteId = inviteId,
                        hubId = hubId,
                        hubName = row.optString("hub_name").ifBlank { "ศูนย์ฝากซัก" },
                        shopName = row.optString("shop_name").ifBlank { "ร้านซัก" },
                        status = row.optString("status").ifBlank { "pending" },
                        createdAt = row.optString("created_at").takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        val activeRaw = runCatching {
            rpc("queuego_laundry_rider_active_job", auth.session.accessToken, JSONObject())
        }.getOrNull()
        val active = when (activeRaw) {
            is JSONObject -> parseLaundryJob(activeRaw)
            is JSONArray -> activeRaw.optJSONObject(0)?.let(::parseLaundryJob)
            else -> null
        }

        var pool = emptyList<RiderLaundryJob>()
        if (!hasNormalActive && active == null && online && modeEnabled) {
            val poolRaw = runCatching {
                rpc("queuego_laundry_rider_pool", auth.session.accessToken, JSONObject())
            }.getOrNull()
            val rows = when (poolRaw) {
                is JSONArray -> poolRaw
                is JSONObject -> JSONArray().put(poolRaw)
                else -> JSONArray()
            }
            pool = buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    parseLaundryJob(row)?.let(::add)
                }
            }
        }

        return RiderLaundryState(
            modeEnabled = modeEnabled,
            invites = invites,
            activeJob = active,
            pool = pool
        )
    }

    private fun parseLaundryJob(row: JSONObject): RiderLaundryJob? {
        val jobId = row.optString("job_id")
        val laundryOrderId = row.optString("laundry_order_id")
        if (jobId.isBlank() || laundryOrderId.isBlank()) return null
        return RiderLaundryJob(
            jobId = jobId,
            laundryOrderId = laundryOrderId,
            orderNumber = row.optString("order_number").takeIf { it.isNotBlank() },
            leg = row.optString("leg").ifBlank { "pickup" },
            jobStatus = row.optString("job_status").ifBlank { "waiting" },
            orderStatus = row.optString("order_status").takeIf { it.isNotBlank() },
            hubId = row.optString("hub_id").takeIf { it.isNotBlank() },
            hubName = row.optString("hub_name").takeIf { it.isNotBlank() },
            shopName = row.optString("shop_name").takeIf { it.isNotBlank() },
            serviceName = row.optString("service_name").takeIf { it.isNotBlank() },
            actualQuantity = row.doubleOrNull("actual_quantity"),
            pricingType = row.optString("pricing_type").takeIf { it.isNotBlank() },
            jobFee = row.doubleOrZero("job_fee"),
            fromAddress = row.optString("from_address").takeIf { it.isNotBlank() },
            fromLatitude = row.doubleOrNull("from_latitude"),
            fromLongitude = row.doubleOrNull("from_longitude"),
            toAddress = row.optString("to_address").takeIf { it.isNotBlank() },
            toLatitude = row.doubleOrNull("to_latitude"),
            toLongitude = row.doubleOrNull("to_longitude"),
            customerAmount = row.doubleOrNull("customer_amount"),
            actualKg = row.doubleOrNull("actual_kg"),
            createdAt = row.optString("created_at").takeIf { it.isNotBlank() }
        )
    }

    private fun marketPickupRoute(auth: QueueGoAuth, marketOrderId: String): List<MarketPickup> {
        val rows = rpc(
            "market_pickup_route_summary",
            auth.session.accessToken,
            JSONObject().put("p_market_order_id", marketOrderId)
        )
        val array = when (rows) {
            is JSONArray -> rows
            is JSONObject -> JSONArray().put(rows)
            else -> JSONArray()
        }
        return buildList {
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                add(
                    MarketPickup(
                        pickupId = row.optString("pickup_id"),
                        status = row.optString("status"),
                        shopName = row.optString("shop_name").ifBlank { "ร้านค้า" },
                        shopAddress = row.optString("shop_address").takeIf { it.isNotBlank() },
                        shopAmount = row.doubleOrZero("shop_amount"),
                        latitude = row.doubleOrNull("latitude"),
                        longitude = row.doubleOrNull("longitude")
                    )
                )
            }
        }
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
                throw RiderHttpException(code, message)
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
        arrivedCustomerAt = o.optString("rider_arrived_customer_at").takeIf { it.isNotBlank() },
        shopCash = o.doubleOrNull("subtotal"),
        customerCash = o.doubleOrNull("total_amount")
    )

    private fun JSONObject.doubleOrNull(name: String): Double? =
        if (!has(name) || isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }

    private fun JSONObject.doubleOrZero(name: String): Double =
        if (!has(name) || isNull(name)) 0.0 else optDouble(name, 0.0)

    private fun JSONObject.putNullable(name: String, value: Double?): JSONObject {
        if (value == null) put(name, JSONObject.NULL) else put(name, value)
        return this
    }

    private fun firstOfferExpiry(rows: JSONArray): String? {
        for (i in 0 until rows.length()) {
            val value = rows.optJSONObject(i)?.optString("expires_at").orEmpty()
            if (value.isNotBlank()) return value
        }
        return null
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
                throw RiderHttpException(code, message)
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
