package com.queuego.rider

import com.queuego.shared.nativeOrderNumber

import android.content.Context
import android.net.Uri
import android.os.SystemClock
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

internal fun riderNullableText(value: String?): String? =
    value?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

internal fun riderNullableUuid(value: String?): String? =
    riderNullableText(value)?.takeIf { candidate ->
        runCatching { UUID.fromString(candidate) }.isSuccess
    }

internal fun riderProfilePhoto(metadata: JSONObject?): String? =
    riderNullableText(metadata?.optString("profileImage"))

internal fun riderRatingSummaryFromRpc(raw: Any?): RiderRatingSummary {
    val row = when (raw) {
        is JSONObject -> raw
        is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
        else -> JSONObject()
    }
    val distribution = row.optJSONObject("distribution")
    val average = if (row.has("average") && !row.isNull("average")) {
        row.optDouble("average").takeIf { it.isFinite() }
    } else null
    return RiderRatingSummary(
        average = average,
        count = row.optInt("count", 0).coerceAtLeast(0),
        distribution = (1..5).associateWith { star ->
            distribution?.optInt(star.toString(), 0)?.coerceAtLeast(0) ?: 0
        }
    )
}

internal fun riderDemandZonesFromRpc(raw: Any?): List<RiderDemandZone> {
    val rows = when (raw) {
        is JSONArray -> raw
        is JSONObject -> raw.optJSONArray("zones") ?: JSONArray().put(raw)
        else -> JSONArray()
    }
    val seen = hashSetOf<String>()
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val latitude = if (row.has("latitude") && !row.isNull("latitude")) {
                row.optDouble("latitude").takeIf { it.isFinite() && it in 5.0..21.0 }
            } else null
            val longitude = if (row.has("longitude") && !row.isNull("longitude")) {
                row.optDouble("longitude").takeIf { it.isFinite() && it in 97.0..106.0 }
            } else null
            val level = row.optInt("level", 0)
            val windowHours = row.optInt("window_hours", 0)
            if (latitude == null || longitude == null || level !in 1..3 || windowHours !in 1..168) continue
            val key = latitude.toString() + ":" + longitude.toString()
            if (!seen.add(key)) continue
            add(RiderDemandZone(latitude, longitude, level, windowHours))
        }
    }
}

internal fun riderSupplyPlanFromRpc(raw: Any?): RiderSupplyPlan {
    val row = when (raw) {
        is JSONObject -> raw
        is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
        else -> JSONObject()
    }
    val ready = row.optBoolean("ready", false)
    val confidence = row.optString("confidence").takeIf {
        it in setOf("low", "medium", "high")
    } ?: "low"
    val sourceDays = row.optInt("source_days", 28).coerceIn(1, 365)
    val forecast = row.optBoolean("forecast", false)
    val values = row.optJSONArray("windows") ?: JSONArray()
    val seen = hashSetOf<String>()
    val windows = buildList {
        if (ready) {
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val day = item.optInt("day_of_week", 0)
                val start = item.optInt("start_hour", -1)
                val end = item.optInt("end_hour", -1)
                val level = item.optInt("level", 0)
                if (day !in 1..7 || start !in 0..23 || end !in 1..24 || end <= start || level !in 1..3) continue
                val key = "$day:$start:$end"
                if (!seen.add(key)) continue
                add(RiderSupplyWindow(day, start, end, level))
            }
        }
    }
    return RiderSupplyPlan(
        ready = ready && windows.isNotEmpty(),
        confidence = confidence,
        sourceDays = sourceDays,
        forecast = forecast,
        windows = windows
    )
}

internal val riderComplimentKeys = setOf(
    "polite","fast","careful","professional","friendly","helpful"
)

internal fun riderComplimentSummaryFromRpc(raw: Any?): RiderComplimentSummary {
    val row = when (raw) {
        is JSONObject -> raw
        is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
        else -> JSONObject()
    }
    val items = row.optJSONArray("items") ?: JSONArray()
    val seen = hashSetOf<String>()
    val parsed = buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val key = item.optString("key").trim().lowercase()
            val count = item.optInt("count", 0)
            if (key !in riderComplimentKeys || count <= 0 || !seen.add(key)) continue
            add(RiderComplimentCount(key, count))
        }
    }
    return RiderComplimentSummary(
        total = row.optInt("total", 0).coerceAtLeast(0),
        items = parsed
    )
}

internal fun riderProfilePhotoMimeType(raw: String?): String? = when (raw?.trim()?.lowercase()) {
    "image/jpeg", "image/jpg" -> "image/jpeg"
    "image/png" -> "image/png"
    "image/webp" -> "image/webp"
    else -> null
}

internal fun riderReverificationStatusFromRpc(raw: Any?): RiderReverificationStatus {
    val row = when (raw) {
        is JSONObject -> raw
        is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
        else -> JSONObject()
    }
    fun nullable(key: String): String? =
        row.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
    return RiderReverificationStatus(
        required = row.optBoolean("required", false),
        requestId = nullable("request_id"),
        status = nullable("status"),
        reason = nullable("reason"),
        requestedAt = nullable("requested_at"),
        submittedAt = nullable("submitted_at"),
        reviewedAt = nullable("reviewed_at"),
        reviewNote = nullable("review_note")
    )
}

internal fun riderReverificationBlocksOnline(status: RiderReverificationStatus): Boolean =
    status.required && status.status in setOf("required", "pending", "rejected")

internal fun riderVehicleInspectionStatusFromRpc(raw: Any?): RiderVehicleInspectionStatus {
    val row = when (raw) {
        is JSONObject -> raw
        is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
        else -> JSONObject()
    }
    fun nullable(key: String): String? =
        row.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
    return RiderVehicleInspectionStatus(
        required = row.optBoolean("required", false),
        requestId = nullable("request_id"),
        status = nullable("status"),
        reason = nullable("reason"),
        requestedAt = nullable("requested_at"),
        submittedAt = nullable("submitted_at"),
        reviewedAt = nullable("reviewed_at"),
        reviewNote = nullable("review_note")
    )
}

internal fun riderVehicleInspectionBlocksOnline(status: RiderVehicleInspectionStatus): Boolean =
    status.required && status.status in setOf("required", "pending", "rejected")

internal fun riderVehiclesFromRpc(raw: Any?): List<RiderVehicle> {
    val rows = when (raw) {
        is JSONArray -> raw
        is JSONObject -> JSONArray().put(raw)
        else -> JSONArray()
    }
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val id = riderNullableText(row.optString("vehicle_id")) ?: continue
            val type = riderNullableText(row.optString("vehicle_type")) ?: continue
            val plate = riderNullableText(row.optString("vehicle_plate")) ?: continue
            val status = riderNullableText(row.optString("status")) ?: "pending"
            add(
                RiderVehicle(
                    id = id,
                    type = type,
                    name = riderNullableText(row.optString("vehicle_name")),
                    plate = plate,
                    capacityKg = if (!row.has("vehicle_capacity_kg") || row.isNull("vehicle_capacity_kg")) {
                        null
                    } else {
                        row.optDouble("vehicle_capacity_kg").takeIf { it.isFinite() }
                    },
                    status = status,
                    selected = row.optBoolean("is_selected", false),
                    legacySeed = row.optBoolean("legacy_seed", false),
                    approvedAt = riderNullableText(row.optString("approved_at")),
                    reviewNote = riderNullableText(row.optString("review_note"))
                )
            )
        }
    }
}

internal fun riderVehicleBlocksOnline(vehicle: RiderVehicle?): Boolean =
    vehicle != null && vehicle.selected && vehicle.status != "approved" && !vehicle.legacySeed

internal fun riderEmergencyContactFromRpc(raw: Any?): RiderEmergencyContact {
    val row = when (raw) {
        is JSONObject -> raw
        is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
        else -> JSONObject()
    }
    fun nullable(key: String): String? = riderNullableText(row.optString(key))
    val configured = row.optBoolean("configured", false)
    return RiderEmergencyContact(
        configured = configured,
        name = if (configured) nullable("name") else null,
        phone = if (configured) nullable("phone") else null,
        updatedAt = if (configured) nullable("updated_at") else null
    )
}

internal fun riderFatigueStatusFromRpc(raw: Any?): RiderFatigueStatus {
    val row = when (raw) {
        is JSONObject -> raw
        is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
        else -> JSONObject()
    }
    val level = row.optString("level").takeIf {
        it in setOf("normal", "rest_recommended", "strong_rest_recommended")
    } ?: "normal"
    return RiderFatigueStatus(
        online = row.optBoolean("online", false),
        onlineSince = riderNullableText(row.optString("online_since")),
        continuousOnlineMinutes = row.optInt("continuous_online_minutes", 0).coerceAtLeast(0),
        onlineMinutesToday = row.optInt("online_minutes_today", 0).coerceAtLeast(0),
        level = level,
        medicalDetection = row.optBoolean("medical_detection", false)
    )
}

internal fun riderMissionsFromRpc(raw: Any?): List<RiderMission> {
    val rows = when (raw) {
        is JSONArray -> raw
        is JSONObject -> JSONArray().put(raw)
        else -> JSONArray()
    }
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val id = row.optString("id").trim()
            val title = row.optString("title").trim()
            val target = row.optInt("target_jobs", 0)
            if (id.isBlank() || title.isBlank() || target <= 0) continue
            val progress = row.optInt("progress_jobs", 0).coerceIn(0, target)
            add(
                RiderMission(
                    id = id,
                    title = title,
                    description = riderNullableText(row.optString("description")),
                    targetJobs = target,
                    rewardNote = riderNullableText(row.optString("reward_note")),
                    startsAt = riderNullableText(row.optString("starts_at")),
                    endsAt = riderNullableText(row.optString("ends_at")),
                    state = row.optString("state").takeIf { it in setOf("upcoming","active","completed") } ?: "active",
                    progressJobs = progress,
                    completed = row.optBoolean("completed", progress >= target)
                )
            )
        }
    }
}

internal fun riderOfferPerformanceFromRpc(
    raw: Any?,
    fallbackDays: Int
): RiderOfferPerformance {
    val row = when (raw) {
        is JSONObject -> raw
        is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
        else -> JSONObject()
    }
    fun nullableDouble(key: String): Double? =
        if (row.has(key) && !row.isNull(key)) row.optDouble(key).takeIf { it.isFinite() } else null
    return RiderOfferPerformance(
        days = row.optInt("days", fallbackDays),
        offered = row.optInt("offered", 0).coerceAtLeast(0),
        accepted = row.optInt("accepted", 0).coerceAtLeast(0),
        declined = row.optInt("declined", 0).coerceAtLeast(0),
        expired = row.optInt("expired", 0).coerceAtLeast(0),
        acceptanceRate = nullableDouble("acceptance_rate"),
        responseRate = nullableDouble("response_rate"),
        declineRate = nullableDouble("decline_rate"),
        timeoutRate = nullableDouble("timeout_rate"),
        averageResponseSeconds = nullableDouble("average_response_seconds")
    )
}

internal fun riderDestinationPreferenceFromRpc(raw: Any?): RiderDestinationPreference {
    val row = when (raw) {
        is JSONObject -> raw
        is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
        else -> JSONObject()
    }
    val configured = row.optBoolean("configured", false)
    val label = riderNullableText(row.optString("label"))
    val latitude = if (row.has("latitude") && !row.isNull("latitude")) row.optDouble("latitude").takeIf { it.isFinite() } else null
    val longitude = if (row.has("longitude") && !row.isNull("longitude")) row.optDouble("longitude").takeIf { it.isFinite() } else null
    val valid = configured &&
        !label.isNullOrBlank() &&
        latitude != null && latitude in 5.0..21.0 &&
        longitude != null && longitude in 97.0..106.0
    return RiderDestinationPreference(
        configured = valid,
        enabled = valid && row.optBoolean("enabled", false),
        label = label.takeIf { valid },
        latitude = latitude.takeIf { valid },
        longitude = longitude.takeIf { valid },
        updatedAt = riderNullableText(row.optString("updated_at")).takeIf { valid }
    )
}

private val riderDestinationAddressKeys =
    listOf("house_number", "soi", "road", "subdistrict", "district", "province", "postcode")

internal fun riderFormatLongdoAddress(raw: String): String? = runCatching {
    val row = JSONObject(raw)
    riderDestinationAddressKeys
        .mapNotNull { key -> row.optString(key).trim().takeIf { it.isNotBlank() && it != "null" } }
        .joinToString(" ")
        .takeIf { it.isNotBlank() }
}.getOrNull()

internal fun riderDocumentReadinessFromRpc(raw: Any?): RiderDocumentReadiness {
    val row = when (raw) {
        is JSONObject -> raw
        is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
        else -> JSONObject()
    }
    val items = row.optJSONArray("items") ?: JSONArray()
    val parsed = buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val key = riderNullableText(item.optString("key")) ?: continue
            val label = riderNullableText(item.optString("label")) ?: key
            val status = riderNullableText(item.optString("status"))
                ?.takeIf { it in setOf("unknown","valid","expiring","expired") }
                ?: "unknown"
            add(
                RiderDocumentStatus(
                    key = key,
                    label = label,
                    vehicleId = riderNullableText(item.optString("vehicle_id")),
                    vehiclePlate = riderNullableText(item.optString("vehicle_plate")),
                    expiresOn = riderNullableText(item.optString("expires_on")),
                    status = status,
                    daysRemaining = if (!item.has("days_remaining") || item.isNull("days_remaining")) {
                        null
                    } else {
                        item.optInt("days_remaining")
                    }
                )
            )
        }
    }
    return RiderDocumentReadiness(
        blocking = row.optBoolean("blocking", false),
        items = parsed
    )
}

internal fun riderPickupGuideFromRpc(raw: Any?): RiderPickupGuide {
    val row = when (raw) {
        is JSONObject -> raw
        is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
        else -> JSONObject()
    }
    fun nullable(key: String): String? =
        row.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
    return RiderPickupGuide(
        pickupInstructions = nullable("pickup_instructions"),
        parkingInstructions = nullable("parking_instructions"),
        marketZone = nullable("market_zone"),
        marketStallNo = nullable("market_stall_no")
    )
}

internal fun riderLoadSummaryFromRpc(raw: Any?): RiderLoadSummary {
    val row = when (raw) {
        is JSONObject -> raw
        is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
        else -> JSONObject()
    }
    fun number(key: String): Double? =
        if (!row.has(key) || row.isNull(key)) null
        else row.optDouble(key).takeIf { it.isFinite() && it >= 0.0 }
    val state = riderNullableText(row.optString("state"))
        ?.takeIf { it in setOf("unknown","capacity_unknown","normal","near_capacity","over_capacity") }
        ?: "unknown"
    return RiderLoadSummary(
        weightKg = number("weight_kg"),
        vehicleCapacityKg = number("vehicle_capacity_kg"),
        utilizationRatio = number("utilization_ratio"),
        state = state
    )
}

internal fun riderInsurancePoliciesFromRpc(raw: Any?): List<RiderInsurancePolicy> {
    val rows = when (raw) {
        is JSONArray -> raw
        is JSONObject -> JSONArray().put(raw)
        else -> JSONArray()
    }
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val id = riderNullableText(row.optString("policy_id")) ?: continue
            val provider = riderNullableText(row.optString("provider_name")) ?: continue
            val starts = riderNullableText(row.optString("starts_on")) ?: continue
            val expires = riderNullableText(row.optString("expires_on")) ?: continue
            val type = riderNullableText(row.optString("coverage_type"))
                ?.takeIf { it in setOf("personal_accident","income_protection","vehicle","other") } ?: "other"
            val status = riderNullableText(row.optString("status"))
                ?.takeIf { it in setOf("active","upcoming","expired") } ?: "expired"
            add(RiderInsurancePolicy(
                id=id,coverageType=type,providerName=provider,
                productName=riderNullableText(row.optString("product_name")),
                policyNumber=riderNullableText(row.optString("policy_number")),
                startsOn=starts,expiresOn=expires,status=status,
                coverageSummary=riderNullableText(row.optString("coverage_summary")),
                supportPhone=riderNullableText(row.optString("support_phone")),
                supportUrl=riderNullableText(row.optString("support_url"))?.takeIf { it.startsWith("https://") }
            ))
        }
    }
}

class QueueGoApi {
    companion object {
        private const val BASE_URL = "https://pkypiqhlrmzocysgeqew.supabase.co"
        private const val PUBLISHABLE_KEY = "sb_publishable_Vn2Il4jp-iBtzNsGRNY8Lg_Tpvk2Vre"
        private const val TIMEOUT = 15000
        private const val RIDER_JOB_FIELDS = "id,order_number,status,pickup_address,pickup_latitude,pickup_longitude," +
            "delivery_address,delivery_latitude,delivery_longitude,delivery_fee,subtotal,total_amount,market_order_id," +
            "rider_arrived_shop_at,rider_arrived_customer_at"
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
        if (online) {
            val identityState = runCatching { reverificationStatus(auth) }.getOrNull()
            if (identityState != null && riderReverificationBlocksOnline(identityState)) {
                error("กรุณายืนยันตัวตน Rider ให้ผ่านก่อนเปิดออนไลน์")
            }
            val vehicleState = runCatching { vehicleInspectionStatus(auth) }.getOrNull()
            if (vehicleState != null && riderVehicleInspectionBlocksOnline(vehicleState)) {
                error("กรุณาส่งรูปตรวจสภาพรถและรออนุมัติก่อนเปิดออนไลน์")
            }
            val selectedVehicle = runCatching { vehicles(auth).firstOrNull { it.selected } }.getOrNull()
            if (riderVehicleBlocksOnline(selectedVehicle)) {
                error("กรุณาเลือกรถที่ผ่านการอนุมัติก่อนเปิดออนไลน์")
            }
            val documentState = runCatching { documentReadiness(auth) }.getOrNull()
            if (documentState?.blocking == true) {
                error("เอกสาร Rider หรือ พ.ร.บ. รถคันปัจจุบันหมดอายุ กรุณาติดต่อ Admin")
            }
        }
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

    suspend fun missions(auth: QueueGoAuth): List<RiderMission> =
        withContext(Dispatchers.IO) {
            riderMissionsFromRpc(
                rpc(
                    "qg_rider_missions",
                    auth.session.accessToken,
                    JSONObject()
                )
            )
        }

    suspend fun offerPerformance(auth: QueueGoAuth, days: Int = 1): RiderOfferPerformance =
        withContext(Dispatchers.IO) {
            require(days in setOf(1, 7, 30)) { "days must be 1, 7 or 30" }
            riderOfferPerformanceFromRpc(
                rpc(
                    "qg_rider_offer_performance",
                    auth.session.accessToken,
                    JSONObject().put("p_days", days)
                ),
                days
            )
        }

    suspend fun fatigueStatus(auth: QueueGoAuth): RiderFatigueStatus =
        withContext(Dispatchers.IO) {
            riderFatigueStatusFromRpc(
                rpc(
                    "qg_rider_fatigue_status",
                    auth.session.accessToken,
                    JSONObject()
                )
            )
        }

    suspend fun submitMapFeedback(
        auth: QueueGoAuth,
        category: String,
        latitude: Double,
        longitude: Double,
        note: String?
    ) = withContext(Dispatchers.IO) {
        require(category in riderMapFeedbackCategoryKeys) { "ประเภทปัญหาแผนที่ไม่ถูกต้อง" }
        require(latitude in 5.0..21.0 && longitude in 97.0..106.0) { "ตำแหน่งอยู่นอกพื้นที่ที่รองรับ" }
        val cleanNote = riderNullableText(note)
        require(cleanNote == null || cleanNote.length <= 500) { "รายละเอียดต้องไม่เกิน 500 ตัวอักษร" }
        rpc(
            "qg_rider_submit_map_feedback",
            auth.session.accessToken,
            JSONObject()
                .put("p_category", category)
                .put("p_latitude", latitude)
                .put("p_longitude", longitude)
                .put("p_note", cleanNote)
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

    suspend fun ratingSummary(auth: QueueGoAuth): RiderRatingSummary =
        withContext(Dispatchers.IO) {
            riderRatingSummaryFromRpc(
                rpc(
                    "qg_rider_rating_summary",
                    auth.session.accessToken,
                    JSONObject()
                )
            )
        }

    suspend fun complimentSummary(auth: QueueGoAuth): RiderComplimentSummary =
        withContext(Dispatchers.IO) {
            riderComplimentSummaryFromRpc(
                rpc(
                    "qg_rider_compliment_summary",
                    auth.session.accessToken,
                    JSONObject()
                )
            )
        }

    suspend fun supplyPlan(auth: QueueGoAuth): RiderSupplyPlan =
        withContext(Dispatchers.IO) {
            riderSupplyPlanFromRpc(
                rpc(
                    "qg_rider_supply_plan",
                    auth.session.accessToken,
                    JSONObject()
                )
            )
        }

    suspend fun demandHeatmap(auth: QueueGoAuth): List<RiderDemandZone> =
        withContext(Dispatchers.IO) {
            riderDemandZonesFromRpc(
                rpc(
                    "qg_rider_demand_heatmap",
                    auth.session.accessToken,
                    JSONObject()
                )
            )
        }

    suspend fun destinationPreference(auth: QueueGoAuth): RiderDestinationPreference =
        withContext(Dispatchers.IO) {
            riderDestinationPreferenceFromRpc(
                rpc(
                    "qg_rider_destination_preference",
                    auth.session.accessToken,
                    JSONObject()
                )
            )
        }

    suspend fun setDestinationPreference(
        auth: QueueGoAuth,
        label: String,
        latitude: Double,
        longitude: Double,
        enabled: Boolean
    ): RiderDestinationPreference = withContext(Dispatchers.IO) {
        require(label.trim().isNotEmpty() && label.trim().length <= 160) { "กรุณาระบุชื่อจุดหมาย" }
        require(latitude in 5.0..21.0 && longitude in 97.0..106.0) { "จุดหมายอยู่นอกพื้นที่ที่รองรับ" }
        riderDestinationPreferenceFromRpc(
            rpc(
                "qg_rider_set_destination_preference",
                auth.session.accessToken,
                JSONObject()
                    .put("p_label", label.trim())
                    .put("p_latitude", latitude)
                    .put("p_longitude", longitude)
                    .put("p_enabled", enabled)
            )
        )
    }

    suspend fun reverseGeocodeDestination(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            if (latitude !in 5.0..21.0 || longitude !in 97.0..106.0) return@withContext null
            val connection = URL(
                "https://api.longdo.com/map/services/address?lon=$longitude&lat=$latitude&key=347649ca13f49db0d8a599ae35f8de2b"
            ).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            try {
                if (connection.responseCode !in 200..299) return@withContext null
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    riderFormatLongdoAddress(reader.readText())
                }
            } catch (_: Exception) {
                null
            } finally {
                connection.disconnect()
            }
        }

    suspend fun reverificationStatus(auth: QueueGoAuth): RiderReverificationStatus =
        withContext(Dispatchers.IO) {
            riderReverificationStatusFromRpc(
                rpc(
                    "qg_rider_reverification_status",
                    auth.session.accessToken,
                    JSONObject()
                )
            )
        }

    suspend fun submitReverification(
        context: Context,
        auth: QueueGoAuth,
        photoUri: Uri
    ): RiderReverificationStatus = withContext(Dispatchers.IO) {
        val path = uploadEvidence(context, auth, photoUri)
        riderReverificationStatusFromRpc(
            rpc(
                "qg_rider_submit_reverification",
                auth.session.accessToken,
                JSONObject().put("p_photo_path", path)
            )
        )
    }

    suspend fun vehicleInspectionStatus(auth: QueueGoAuth): RiderVehicleInspectionStatus =
        withContext(Dispatchers.IO) {
            riderVehicleInspectionStatusFromRpc(
                rpc(
                    "qg_rider_vehicle_inspection_status",
                    auth.session.accessToken,
                    JSONObject()
                )
            )
        }

    suspend fun submitVehicleInspection(
        context: Context,
        auth: QueueGoAuth,
        photoUri: Uri
    ): RiderVehicleInspectionStatus = withContext(Dispatchers.IO) {
        val path = uploadEvidence(context, auth, photoUri)
        riderVehicleInspectionStatusFromRpc(
            rpc(
                "qg_rider_submit_vehicle_inspection",
                auth.session.accessToken,
                JSONObject().put("p_photo_path", path)
            )
        )
    }

    suspend fun vehicles(auth: QueueGoAuth): List<RiderVehicle> =
        withContext(Dispatchers.IO) {
            riderVehiclesFromRpc(
                rpc(
                    "qg_rider_vehicle_list",
                    auth.session.accessToken,
                    JSONObject()
                )
            )
        }

    suspend fun addVehicle(
        context: Context,
        auth: QueueGoAuth,
        type: String,
        name: String?,
        plate: String,
        capacityKg: Double?,
        photoUri: Uri
    ) = withContext(Dispatchers.IO) {
        val path = uploadEvidence(context, auth, photoUri)
        rpc(
            "qg_rider_add_vehicle",
            auth.session.accessToken,
            JSONObject()
                .put("p_vehicle_type", type)
                .put(
                    "p_vehicle_name",
                    name?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL
                )
                .put("p_vehicle_plate", plate.trim())
                .putNullable("p_capacity_kg", capacityKg)
                .put("p_photo_path", path)
        )
        Unit
    }

    suspend fun selectVehicle(auth: QueueGoAuth, vehicleId: String) =
        withContext(Dispatchers.IO) {
            rpc(
                "qg_rider_select_vehicle",
                auth.session.accessToken,
                JSONObject().put("p_vehicle_id", vehicleId)
            )
            Unit
        }

    suspend fun documentReadiness(auth: QueueGoAuth): RiderDocumentReadiness =
        withContext(Dispatchers.IO) {
            riderDocumentReadinessFromRpc(
                rpc(
                    "qg_rider_document_readiness",
                    auth.session.accessToken,
                    JSONObject()
                )
            )
        }

    suspend fun insurancePolicies(auth: QueueGoAuth): List<RiderInsurancePolicy> =
        withContext(Dispatchers.IO) {
            riderInsurancePoliciesFromRpc(
                rpc("qg_rider_insurance_policies", auth.session.accessToken, JSONObject())
            )
        }

    suspend fun emergencyContact(auth: QueueGoAuth): RiderEmergencyContact =
        withContext(Dispatchers.IO) {
            riderEmergencyContactFromRpc(
                rpc(
                    "qg_rider_emergency_contact",
                    auth.session.accessToken,
                    JSONObject()
                )
            )
        }

    suspend fun setEmergencyContact(
        auth: QueueGoAuth,
        name: String,
        phone: String
    ): RiderEmergencyContact = withContext(Dispatchers.IO) {
        riderEmergencyContactFromRpc(
            rpc(
                "qg_rider_set_emergency_contact",
                auth.session.accessToken,
                JSONObject()
                    .put("p_name", name)
                    .put("p_phone", phone)
            )
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
                "/rest/v1/orders?select=id,order_number,pickup_address,delivery_address,delivery_fee,completed_at,updated_at" +
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
                            completedAt = r.optString("completed_at")
                                .takeIf { it.isNotBlank() && it != "null" }
                                ?: r.optString("updated_at").takeIf { it.isNotBlank() && it != "null" }
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

    suspend fun updateProfilePhoto(
        context: Context,
        auth: QueueGoAuth,
        photoUri: Uri
    ): String = withContext(Dispatchers.IO) {
        val mime = riderProfilePhotoMimeType(context.contentResolver.getType(photoUri))
            ?: error("รองรับเฉพาะรูป JPG, PNG หรือ WebP")
        val bytes = context.contentResolver.openInputStream(photoUri)?.use { it.readBytes() }
            ?: error("อ่านรูปโปรไฟล์ไม่ได้")
        if (bytes.isEmpty()) error("รูปโปรไฟล์ว่าง")
        if (bytes.size > 5 * 1024 * 1024) error("รูปโปรไฟล์ต้องมีขนาดไม่เกิน 5 MB")

        val path = auth.session.authUserId + "/profile"
        val connection = URL(BASE_URL + "/storage/v1/object/rider-profile/" + path)
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.doOutput = true
            connection.setRequestProperty("apikey", PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer " + auth.session.accessToken)
            connection.setRequestProperty("Content-Type", mime)
            connection.setRequestProperty("x-upsert", "true")
            connection.outputStream.use { it.write(bytes) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val text = connection.errorStream?.use {
                    BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText()
                }.orEmpty()
                val message = runCatching { JSONObject(text).optString("message") }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "อัปโหลดรูปโปรไฟล์ไม่สำเร็จ"
                throw RiderHttpException(code, message)
            }
        } finally {
            connection.disconnect()
        }

        val raw = rpc(
            "qg_rider_set_profile_photo",
            auth.session.accessToken,
            JSONObject().put("p_path", path)
        )
        val row = when (raw) {
            is JSONObject -> raw
            is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
            else -> JSONObject()
        }
        riderNullableText(row.optString("url"))
            ?: error("ระบบยังไม่ยืนยันรูปโปรไฟล์")
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

        val fields = RIDER_JOB_FIELDS
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
                "/rest/v1/market_orders?select=subtotal,delivery_fee,multi_shop_service_fee,rider_bonus,total_amount&id=eq." +
                    enc(active.marketOrderId) + "&limit=1",
                auth.session.accessToken
            )
            if (parents.length() > 0) {
                val parent = parents.getJSONObject(0)
                active = active.copy(
                    // deliveryFee remains Rider income; customer-facing fees stay separate below.
                    deliveryFee = parent.doubleOrZero("delivery_fee") +
                        parent.doubleOrZero("rider_bonus"),
                    shopCash = null,
                    customerCash = parent.doubleOrNull("total_amount"),
                    customerSubtotal = parent.doubleOrNull("subtotal"),
                    customerDeliveryFee = parent.doubleOrNull("delivery_fee"),
                    customerServiceFee = parent.doubleOrNull("multi_shop_service_fee")
                )
            }
        }

        var bundle = RiderRouteBundleState()
        if (online && active != null && active.marketOrderId == null) {
            bundle = runCatching { readRouteBundleState(auth, profileId) }
                .getOrDefault(RiderRouteBundleState())
            bundle.currentOrder()?.let { active = it.job }
        }

        var offered: RiderJob? = null
        if (active == null && online) {
            // Server is the single source of truth for sequential Rider offers.
            // Never expose a shared pool: only the Rider selected by qg_dispatch_rider_offers()
            // can see and accept this live 30-second offer.
            val offer = rpcArray("qg_get_my_rider_offer", auth.session.accessToken)
            val offeredId = firstOrderId(offer)
            val offerExpiry = firstOfferExpiry(offer)
            if (offeredId != null && riderOfferSecondsLeft(offerExpiry) > 0) {
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
        val laundry = runCatching { readLaundryState(auth.session.accessToken) }
            .getOrDefault(RiderLaundryState())

        val profileInfo = RiderProfileInfo(
            profileId = profileId,
            riderName = profile.optString("rider_name").takeIf { it.isNotBlank() },
            phone = profile.optString("phone").takeIf { it.isNotBlank() },
            vehicleType = profile.optString("vehicle_type").takeIf { it.isNotBlank() },
            vehiclePlate = profile.optString("vehicle_plate").takeIf { it.isNotBlank() },
            vehicleStatus = profile.optString("vehicle_status").takeIf { it.isNotBlank() },
            vehicleVerifiedAt = profile.optString("vehicle_verified_at").takeIf { it.isNotBlank() },
            profilePhoto = riderProfilePhoto(profile.optJSONObject("metadata"))
        )
        RiderSnapshot(
            online = online,
            activeJob = active,
            offeredJob = offered,
            marketPickups = marketPickups,
            riderProfileId = profileId,
            laundry = laundry,
            bundle = bundle,
            profile = profileInfo
        )
    }

    /** Exact reference lookup remains constrained to this Rider and existing RLS. */
    suspend fun notificationJob(auth: QueueGoAuth, referenceId: String, profileId: String): RiderJob? =
        withContext(Dispatchers.IO) {
            if (!referenceId.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")))
                return@withContext null
            val fields = "id,order_number,status,pickup_address,pickup_latitude,pickup_longitude," +
                "delivery_address,delivery_latitude,delivery_longitude,delivery_fee,subtotal,total_amount,market_order_id," +
                "rider_arrived_shop_at,rider_arrived_customer_at"
            val base = "/rest/v1/orders?select=" + fields + "&order_type=eq.shopping&rider_id=eq." + enc(profileId)
            val direct = requestAny("GET", base + "&id=eq." + enc(referenceId) + "&limit=1", auth.session.accessToken, null) as? JSONArray
                ?: error("รูปแบบข้อมูลการแจ้งเตือนไม่ถูกต้อง")
            if (direct.length() > 0) return@withContext parseJob(direct.getJSONObject(0))
            val children = requestAny("GET", base + "&market_order_id=eq." + enc(referenceId) +
                "&order=created_at.desc&limit=1", auth.session.accessToken, null) as? JSONArray
                ?: error("รูปแบบข้อมูลการแจ้งเตือนไม่ถูกต้อง")
            children.optJSONObject(0)?.let(::parseJob)
        }

    suspend fun offerShopDisplay(auth: QueueGoAuth, orderId: String): RiderShopDisplay =
        withContext(Dispatchers.IO) {
            val order = requestArray(
                "GET",
                "/rest/v1/orders?select=shop_id&id=eq." + enc(orderId) + "&limit=1",
                auth.session.accessToken
            ).optJSONObject(0) ?: return@withContext RiderShopDisplay(null, null)
            val shopId = riderNullableUuid(order.optString("shop_id"))
                ?: return@withContext RiderShopDisplay(null, null)
            val shop = requestArray(
                "GET",
                "/rest/v1/shop_profiles?select=shop_name,public_logo&id=eq." + enc(shopId) + "&limit=1",
                auth.session.accessToken
            ).optJSONObject(0)
            fun text(key: String): String? = shop?.optString(key)?.takeIf { it.isNotBlank() && it != "null" }
            RiderShopDisplay(text("shop_name"), text("public_logo"))
        }

    suspend fun claimRouteBundle(auth: QueueGoAuth, orderId: String) = withContext(Dispatchers.IO) {
        actionOnce(
            auth,
            "bundle_claim",
            JSONObject().put("p_order_id", orderId)
        )
        Unit
    }

    suspend fun setLaundryMode(auth: QueueGoAuth, enabled: Boolean) = withContext(Dispatchers.IO) {
        rpc(
            "queuego_set_laundry_rider_mode",
            auth.session.accessToken,
            JSONObject().put("p_enabled", enabled)
        )
        Unit
    }

    suspend fun respondLaundryInvite(auth: QueueGoAuth, inviteId: String, accept: Boolean) =
        withContext(Dispatchers.IO) {
            rpc(
                "queuego_laundry_rider_invite_action",
                auth.session.accessToken,
                JSONObject().put("p_invite_id", inviteId).put("p_accept", accept)
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

    suspend fun laundryJobAction(auth: QueueGoAuth, jobId: String, action: String) =
        withContext(Dispatchers.IO) {
            require(action in setOf("arrive", "collect", "deliver")) { "สถานะงานฝากซักไม่ถูกต้อง" }
            rpc(
                "queuego_laundry_rider_action",
                auth.session.accessToken,
                JSONObject().put("p_job_id", jobId).put("p_action", action)
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
            val guide = runCatching {
                riderPickupGuideFromRpc(
                    rpc(
                        "qg_rider_pickup_guide",
                        auth.session.accessToken,
                        JSONObject().put("p_order_id", orderId)
                    )
                )
            }.getOrDefault(RiderPickupGuide(null, null, null, null))
            fun text(row: JSONObject?, key: String): String? = row?.optString(key)?.takeIf { it.isNotBlank() && it != "null" }
            RiderVerificationDetails(
                text(shop, "shop_name"),
                text(shop, "public_logo"),
                text(customer, "name"),
                text(customer, "phone"),
                cleanRiderOrderNote(text(order, "note")),
                guide.pickupInstructions,
                guide.parkingInstructions,
                guide.marketZone,
                guide.marketStallNo
            )
        }

    suspend fun orderLoadSummary(auth: QueueGoAuth, orderId: String): RiderLoadSummary =
        withContext(Dispatchers.IO) {
            riderLoadSummaryFromRpc(
                rpc(
                    "qg_rider_order_load_summary",
                    auth.session.accessToken,
                    JSONObject().put("p_order_id", orderId)
                )
            )
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

    suspend fun liveOfferExpiry(auth: QueueGoAuth, orderId: String): Long? = withContext(Dispatchers.IO) {
        val offers = requestAny("POST", "/rest/v1/rpc/qg_get_my_rider_offer", auth.session.accessToken,
            JSONObject(), timeoutMillis = 3_000) as? JSONArray ?: error("รูปแบบข้อเสนองานไม่ถูกต้อง")
        (0 until offers.length()).mapNotNull { offers.optJSONObject(it) }
            .firstOrNull { it.optString("order_id") == orderId }
            ?.optString("expires_at")?.let(::riderOfferExpiryMillis)
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
        val guideByPickup = runCatching {
            val raw = rpc(
                "qg_rider_market_pickup_guides",
                auth.session.accessToken,
                JSONObject().put("p_market_order_id", marketOrderId)
            )
            val guides = when (raw) {
                is JSONArray -> raw
                is JSONObject -> JSONArray().put(raw)
                else -> JSONArray()
            }
            buildMap {
                for (index in 0 until guides.length()) {
                    val row = guides.optJSONObject(index) ?: continue
                    val pickupId = riderNullableText(row.optString("pickup_id")) ?: continue
                    put(pickupId, riderPickupGuideFromRpc(row))
                }
            }
        }.getOrDefault(emptyMap())
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
                        longitude = row.doubleOrNull("longitude"),
                        pickupInstructions = guideByPickup[row.optString("pickup_id")]?.pickupInstructions,
                        parkingInstructions = guideByPickup[row.optString("pickup_id")]?.parkingInstructions,
                        marketZone = guideByPickup[row.optString("pickup_id")]?.marketZone,
                        marketStallNo = guideByPickup[row.optString("pickup_id")]?.marketStallNo
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

    private fun readRouteBundleState(auth: QueueGoAuth, profileId: String): RiderRouteBundleState {
        val activeMeta = when (val raw = rpc(
            "queuego_route_bundle_active_orders",
            auth.session.accessToken,
            JSONObject()
        )) {
            is JSONArray -> raw
            is JSONObject -> JSONArray().put(raw)
            else -> JSONArray()
        }
        val candidatesRaw = when (val raw = rpc(
            "queuego_route_bundle_candidates",
            auth.session.accessToken,
            JSONObject()
        )) {
            is JSONArray -> raw
            is JSONObject -> JSONArray().put(raw)
            else -> JSONArray()
        }

        val ids = buildList {
            for (i in 0 until activeMeta.length()) {
                activeMeta.optJSONObject(i)?.optString("order_id")
                    ?.takeIf { it.isNotBlank() && it != "null" }
                    ?.let { add(it) }
            }
        }.distinct()

        val jobsById = if (ids.isEmpty()) {
            emptyMap()
        } else {
            val rows = requestArray(
                "GET",
                "/rest/v1/orders?select=" + RIDER_JOB_FIELDS +
                    "&rider_id=eq." + enc(profileId) +
                    "&id=in.(" + ids.joinToString(",") { enc(it) } + ")",
                auth.session.accessToken
            )
            buildMap {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    parseJob(row).let { put(it.id, it) }
                }
            }
        }

        val activeOrders = buildList {
            for (i in 0 until activeMeta.length()) {
                val row = activeMeta.optJSONObject(i) ?: continue
                val orderId = row.optString("order_id")
                val job = jobsById[orderId] ?: continue
                add(
                    RiderBundleOrder(
                        bundleId = row.optString("bundle_id"),
                        job = job,
                        bundleSequence = row.optInt("bundle_sequence", 99),
                        dropRank = row.optInt("drop_rank", row.optInt("bundle_sequence", 99)),
                        shopId = row.optString("shop_id").takeIf { it.isNotBlank() && it != "null" },
                        shopName = row.optString("shop_name").ifBlank { "ร้านค้า" },
                        originalDeliveryFee = row.doubleOrZero("original_delivery_fee"),
                        customerSavings = row.doubleOrZero("customer_savings"),
                        riderExtraFee = row.doubleOrZero("rider_extra_fee"),
                        detourKm = row.doubleOrZero("detour_km"),
                        addedMinutes = row.optInt("added_minutes", 0),
                        routeSequence = row.optString("route_sequence").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }.sortedBy { it.bundleSequence }

        val candidates = buildList {
            for (i in 0 until candidatesRaw.length()) {
                val row = candidatesRaw.optJSONObject(i) ?: continue
                val orderId = row.optString("order_id").takeIf { it.isNotBlank() && it != "null" } ?: continue
                add(
                    RiderBundleCandidate(
                        orderId = orderId,
                        orderNumber = row.optString("order_number").takeIf { it.isNotBlank() && it != "null" },
                        shopId = row.optString("shop_id").takeIf { it.isNotBlank() && it != "null" },
                        shopName = row.optString("shop_name").ifBlank { "ร้านค้า" },
                        pickupAddress = row.optString("pickup_address").takeIf { it.isNotBlank() && it != "null" },
                        deliveryAddress = row.optString("delivery_address").takeIf { it.isNotBlank() && it != "null" },
                        pickupLat = row.doubleOrNull("pickup_latitude"),
                        pickupLng = row.doubleOrNull("pickup_longitude"),
                        deliveryLat = row.doubleOrNull("delivery_latitude"),
                        deliveryLng = row.doubleOrNull("delivery_longitude"),
                        detourKm = row.doubleOrZero("detour_km"),
                        addedMinutes = row.optInt("added_minutes", 0),
                        standaloneDeliveryFee = row.doubleOrZero("standalone_delivery_fee"),
                        bundledDeliveryFee = row.doubleOrZero("bundled_delivery_fee"),
                        customerSavings = row.doubleOrZero("customer_savings"),
                        riderExtraFee = row.doubleOrZero("rider_extra_fee"),
                        routeSequence = row.optString("route_sequence").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }

        return RiderRouteBundleState(activeOrders = activeOrders, candidates = candidates)
    }

    private fun readLaundryState(token: String): RiderLaundryState {
        val root = rpc("queuego_laundry_rider_state", token, JSONObject()) as? JSONObject
            ?: return RiderLaundryState()

        val invitesJson = root.optJSONArray("invites") ?: JSONArray()
        val invites = buildList {
            for (i in 0 until invitesJson.length()) {
                val row = invitesJson.optJSONObject(i) ?: continue
                val inviteId = row.optString("invite_id")
                if (inviteId.isBlank()) continue
                add(
                    RiderLaundryInvite(
                        inviteId = inviteId,
                        hubId = row.optString("hub_id"),
                        hubName = row.optString("hub_name").ifBlank { "ร้านฝากซัก" },
                        shopName = row.optString("shop_name").ifBlank { "ร้านฝากซัก" },
                        status = row.optString("status"),
                        createdAt = row.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }

        fun jobs(name: String): List<RiderLaundryJob> {
            val rows = root.optJSONArray(name) ?: JSONArray()
            val fallbackStatus = if (name == "pool") "waiting" else "assigned"
            return buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    parseLaundryJob(row, fallbackStatus)?.let { add(it) }
                }
            }
        }

        return RiderLaundryState(
            featureEnabled = root.optBoolean("feature_enabled", false),
            modeEnabled = root.optBoolean("mode_enabled", false),
            invites = invites,
            activeJob = jobs("active_jobs").firstOrNull(),
            pool = jobs("pool")
        )
    }

    private fun parseLaundryJob(row: JSONObject, fallbackStatus: String): RiderLaundryJob? {
        val jobId = row.optString("job_id")
        val laundryOrderId = row.optString("laundry_order_id")
        if (jobId.isBlank() || laundryOrderId.isBlank()) return null
        fun text(name: String): String? = row.optString(name).takeIf { it.isNotBlank() && it != "null" }
        return RiderLaundryJob(
            jobId = jobId,
            laundryOrderId = laundryOrderId,
            orderNumber = text("order_number"),
            leg = row.optString("leg").ifBlank { "pickup" },
            jobStatus = row.optString("job_status").ifBlank { fallbackStatus },
            orderStatus = text("order_status"),
            hubId = text("hub_id"),
            hubName = text("hub_name"),
            shopName = text("shop_name"),
            serviceName = text("service_name"),
            actualQuantity = row.doubleOrNull("actual_quantity"),
            pricingType = text("pricing_type"),
            jobFee = row.doubleOrZero("job_fee"),
            fromAddress = text("from_address"),
            fromLatitude = row.doubleOrNull("from_latitude"),
            fromLongitude = row.doubleOrNull("from_longitude"),
            toAddress = text("to_address"),
            toLatitude = row.doubleOrNull("to_latitude"),
            toLongitude = row.doubleOrNull("to_longitude"),
            customerAmount = row.doubleOrNull("customer_amount"),
            actualKg = row.doubleOrNull("actual_kg"),
            createdAt = text("created_at")
        )
    }

    private fun parseJob(o: JSONObject) = RiderJob(
        id = o.getString("id"),
        orderNumber = riderNullableText(o.optString("order_number")),
        status = o.optString("status"),
        pickupAddress = riderNullableText(o.optString("pickup_address")),
        pickupLat = o.doubleOrNull("pickup_latitude"),
        pickupLng = o.doubleOrNull("pickup_longitude"),
        deliveryAddress = riderNullableText(o.optString("delivery_address")),
        deliveryLat = o.doubleOrNull("delivery_latitude"),
        deliveryLng = o.doubleOrNull("delivery_longitude"),
        deliveryFee = o.doubleOrNull("delivery_fee"),
        marketOrderId = riderNullableUuid(o.optString("market_order_id")),
        arrivedShopAt = riderNullableText(o.optString("rider_arrived_shop_at")),
        arrivedCustomerAt = riderNullableText(o.optString("rider_arrived_customer_at")),
        shopCash = o.doubleOrNull("subtotal"),
        customerCash = o.doubleOrNull("total_amount"),
        customerSubtotal = o.doubleOrNull("subtotal"),
        customerDeliveryFee = o.doubleOrNull("delivery_fee")
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
            val id = riderNullableUuid(rows.optJSONObject(i)?.optString("order_id"))
            if (id != null) return id
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

    private fun requestAny(method: String, path: String, token: String?, body: JSONObject?, timeoutMillis: Int = TIMEOUT): Any {
        val startedAt = SystemClock.elapsedRealtime()
        var success = false
        val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
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
            RiderServerClock.observeHttpDate(
                rawDate = connection.getHeaderField("Date"),
                responseElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                deviceWallEpochMs = System.currentTimeMillis()
            )
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText()
            }.orEmpty()

            if (code !in 200..299) {
                val message = runCatching { JSONObject(text).optString("message") }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "HTTP " + code
                throw RiderHttpException(code, message)
            }

            val result: Any = if (text.isBlank()) {
                JSONObject()
            } else {
                val value = text.trim()
                when {
                    value.startsWith("[") -> JSONArray(value)
                    value.startsWith("{") -> JSONObject(value)
                    value == "true" -> true
                    value == "false" -> false
                    else -> value.trim(34.toChar())
                }
            }
            success = true
            return result
        } finally {
            RiderRuntimeDiagnosticsStore.recordNetwork(
                durationMs = SystemClock.elapsedRealtime() - startedAt,
                success = success
            )
            connection.disconnect()
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
