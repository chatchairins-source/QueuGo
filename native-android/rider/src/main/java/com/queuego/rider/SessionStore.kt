package com.queuego.rider

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("queuego_rider_native", Context.MODE_PRIVATE)
    private val alias = "queuego_rider_native_session"

    fun deviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: "android-device"

    @Synchronized
    fun pushDeviceId(): String {
        val existing = prefs.getString("push_device_id", null).orEmpty()
        if (existing.matches(Regex("^[0-9a-fA-F-]{36}$"))) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("push_device_id", created).apply()
        return created
    }

    fun save(auth: QueueGoAuth) {
        val json = JSONObject()
            .put("authUserId", auth.session.authUserId)
            .put("accessToken", auth.session.accessToken)
            .put("refreshToken", auth.session.refreshToken)
            .put("expiresAtMs", auth.session.expiresAtMs)
            .put("sessionId", auth.session.sessionId)
            .put("userId", auth.user.id)
            .put("name", auth.user.name)
            .put("role", auth.user.role)
            .put("status", auth.user.status)
            .put("phone", auth.user.phone)
            .put("suspendedAt", auth.user.suspendedAt)
            .put("suspensionReason", auth.user.suspensionReason)
            .toString()
        prefs.edit().putString("session", encrypt(json)).apply()
    }

    fun load(): QueueGoAuth? = runCatching {
        val raw = prefs.getString("session", null) ?: return null
        val o = JSONObject(decrypt(raw))
        QueueGoAuth(
            QueueGoSession(
                authUserId = o.getString("authUserId"),
                accessToken = o.getString("accessToken"),
                refreshToken = o.optString("refreshToken").takeIf { it.isNotBlank() && it != "null" },
                expiresAtMs = o.optLong("expiresAtMs"),
                sessionId = o.getString("sessionId")
            ),
            QueueGoUser(
                id = o.getString("userId"),
                name = o.optString("name").ifBlank { "ไรเดอร์" },
                role = o.getString("role"),
                status = o.optString("status"),
                phone = o.optString("phone").takeIf { it.isNotBlank() && it != "null" },
                suspendedAt = o.optString("suspendedAt").takeIf { it.isNotBlank() && it != "null" },
                suspensionReason = o.optString("suspensionReason").takeIf { it.isNotBlank() && it != "null" }
            )
        )
    }.getOrNull()

    fun clear() = prefs.edit()
        .remove("session")
        .remove("home_snapshot")
        .remove("ui_state")
        .remove("map_camera")
        .remove("map_view_mode")
        .apply()

    @Synchronized
    fun saveActiveTab(userId: String, tab: String) {
        val safeTab = tab.takeIf { it in setOf("home", "earn", "chat", "profile") } ?: "home"
        val root = JSONObject().put("userId", userId).put("activeTab", safeTab)
        prefs.edit().putString("ui_state", encrypt(root.toString())).apply()
    }

    @Synchronized
    fun loadActiveTab(userId: String): String = runCatching {
        val raw = prefs.getString("ui_state", null) ?: return "home"
        val root = JSONObject(decrypt(raw))
        if (root.optString("userId") != userId) return "home"
        when (val saved = root.optString("activeTab")) {
            "earnings" -> "earn" // migrate the old key without bouncing the Rider back Home
            in setOf("home", "earn", "chat", "profile") -> saved
            else -> "home"
        }
    }.getOrDefault("home")

    @Synchronized
    fun saveMapCamera(userId: String, camera: DoubleArray) {
        if (camera.size < 4 || camera.any { !it.isFinite() }) return
        val root = JSONObject()
            .put("userId", userId)
            .put("lon", camera[0])
            .put("lat", camera[1])
            .put("zoom", camera[2])
            .put("centered", camera[3])
        prefs.edit().putString("map_camera", encrypt(root.toString())).apply()
    }

    @Synchronized
    fun loadMapCamera(userId: String): DoubleArray? = runCatching {
        val raw = prefs.getString("map_camera", null) ?: return null
        val root = JSONObject(decrypt(raw))
        if (root.optString("userId") != userId) return null
        doubleArrayOf(
            root.getDouble("lon"),
            root.getDouble("lat"),
            root.getDouble("zoom"),
            root.optDouble("centered", 0.0)
        ).takeIf { values -> values.all { it.isFinite() } }
    }.getOrNull()

    @Synchronized
    fun saveMapViewMode(userId: String, mode: String) {
        val safeMode = mode.takeIf { it == "FOLLOW" || it == "FREE_PAN" } ?: "FOLLOW"
        val root = JSONObject()
            .put("userId", userId)
            .put("mode", safeMode)
        prefs.edit().putString("map_view_mode", encrypt(root.toString())).apply()
    }

    @Synchronized
    fun loadMapViewMode(userId: String): String = runCatching {
        val raw = prefs.getString("map_view_mode", null) ?: return "FOLLOW"
        val root = JSONObject(decrypt(raw))
        if (root.optString("userId") != userId) return "FOLLOW"
        when (root.optString("mode")) {
            "FREE_PAN" -> "FREE_PAN"
            else -> "FOLLOW"
        }
    }.getOrDefault("FOLLOW")

    @Synchronized
    fun saveHomeSnapshot(userId: String, snapshot: RiderSnapshot) {
        val root = JSONObject()
            .put("userId", userId)
            .put("online", snapshot.online)
            .put("riderProfileId", snapshot.riderProfileId)
        snapshot.activeJob?.let { root.put("activeJob", riderJobToJson(it)) }
        snapshot.laundry.activeJob?.let { root.put("laundryActiveJob", laundryJobToJson(it)) }
        root.put(
            "marketPickups",
            JSONArray().apply {
                snapshot.marketPickups.forEach { put(marketPickupToJson(it)) }
            }
        )
        root.put(
            "bundleActiveOrders",
            JSONArray().apply {
                snapshot.bundle.activeOrders.forEach { put(bundleOrderToJson(it)) }
            }
        )
        prefs.edit().putString("home_snapshot", encrypt(root.toString())).apply()
    }

    @Synchronized
    fun loadHomeSnapshot(userId: String): RiderSnapshot? = runCatching {
        val raw = prefs.getString("home_snapshot", null) ?: return null
        val root = JSONObject(decrypt(raw))
        if (root.optString("userId") != userId) return null
        val activeJob = root.optJSONObject("activeJob")?.let(::riderJobFromJson)
        val laundryActive = root.optJSONObject("laundryActiveJob")?.let(::laundryJobFromJson)
        val marketPickups = root.optJSONArray("marketPickups").jsonObjects()
            .mapNotNull { item -> runCatching { marketPickupFromJson(item) }.getOrNull() }
        val bundleActiveOrders = root.optJSONArray("bundleActiveOrders").jsonObjects()
            .mapNotNull { item -> runCatching { bundleOrderFromJson(item) }.getOrNull() }
        RiderSnapshot(
            online = root.optBoolean("online", false),
            activeJob = activeJob,
            offeredJob = null,
            marketPickups = marketPickups,
            riderProfileId = root.optString("riderProfileId")
                .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
            laundry = RiderLaundryState(activeJob = laundryActive),
            bundle = RiderRouteBundleState(activeOrders = bundleActiveOrders)
        )
    }.getOrNull()

    private fun marketPickupToJson(pickup: MarketPickup): JSONObject = JSONObject()
        .put("pickupId", pickup.pickupId)
        .put("status", pickup.status)
        .put("shopName", pickup.shopName)
        .put("shopAddress", pickup.shopAddress)
        .put("shopAmount", pickup.shopAmount)
        .put("latitude", pickup.latitude)
        .put("longitude", pickup.longitude)

    private fun marketPickupFromJson(o: JSONObject): MarketPickup = MarketPickup(
        pickupId = o.getString("pickupId"),
        status = o.getString("status"),
        shopName = o.optString("shopName"),
        shopAddress = o.optNullableString("shopAddress"),
        shopAmount = o.optDouble("shopAmount", 0.0),
        latitude = o.optNullableDouble("latitude"),
        longitude = o.optNullableDouble("longitude")
    )

    private fun bundleOrderToJson(order: RiderBundleOrder): JSONObject = JSONObject()
        .put("bundleId", order.bundleId)
        .put("job", riderJobToJson(order.job))
        .put("bundleSequence", order.bundleSequence)
        .put("dropRank", order.dropRank)
        .put("shopId", order.shopId)
        .put("shopName", order.shopName)
        .put("originalDeliveryFee", order.originalDeliveryFee)
        .put("customerSavings", order.customerSavings)
        .put("riderExtraFee", order.riderExtraFee)
        .put("detourKm", order.detourKm)
        .put("addedMinutes", order.addedMinutes)
        .put("routeSequence", order.routeSequence)

    private fun bundleOrderFromJson(o: JSONObject): RiderBundleOrder = RiderBundleOrder(
        bundleId = o.getString("bundleId"),
        job = riderJobFromJson(o.getJSONObject("job")),
        bundleSequence = o.optInt("bundleSequence", 0),
        dropRank = o.optInt("dropRank", 0),
        shopId = o.optNullableString("shopId"),
        shopName = o.optString("shopName"),
        originalDeliveryFee = o.optDouble("originalDeliveryFee", 0.0),
        customerSavings = o.optDouble("customerSavings", 0.0),
        riderExtraFee = o.optDouble("riderExtraFee", 0.0),
        detourKm = o.optDouble("detourKm", 0.0),
        addedMinutes = o.optInt("addedMinutes", 0),
        routeSequence = o.optNullableString("routeSequence")
    )

    private fun riderJobToJson(job: RiderJob): JSONObject = JSONObject()
        .put("id", job.id)
        .put("orderNumber", job.orderNumber)
        .put("status", job.status)
        .put("pickupAddress", job.pickupAddress)
        .put("pickupLat", job.pickupLat)
        .put("pickupLng", job.pickupLng)
        .put("deliveryAddress", job.deliveryAddress)
        .put("deliveryLat", job.deliveryLat)
        .put("deliveryLng", job.deliveryLng)
        .put("deliveryFee", job.deliveryFee)
        .put("marketOrderId", job.marketOrderId)
        .put("offerExpiresAt", job.offerExpiresAt)
        .put("arrivedShopAt", job.arrivedShopAt)
        .put("arrivedCustomerAt", job.arrivedCustomerAt)
        .put("shopCash", job.shopCash)
        .put("customerCash", job.customerCash)
        .put("customerSubtotal", job.customerSubtotal)
        .put("customerDeliveryFee", job.customerDeliveryFee)
        .put("customerServiceFee", job.customerServiceFee)

    private fun riderJobFromJson(o: JSONObject): RiderJob = RiderJob(
        id = o.getString("id"),
        orderNumber = o.optNullableString("orderNumber"),
        status = o.getString("status"),
        pickupAddress = o.optNullableString("pickupAddress"),
        pickupLat = o.optNullableDouble("pickupLat"),
        pickupLng = o.optNullableDouble("pickupLng"),
        deliveryAddress = o.optNullableString("deliveryAddress"),
        deliveryLat = o.optNullableDouble("deliveryLat"),
        deliveryLng = o.optNullableDouble("deliveryLng"),
        deliveryFee = o.optNullableDouble("deliveryFee"),
        marketOrderId = o.optNullableString("marketOrderId"),
        offerExpiresAt = o.optNullableString("offerExpiresAt"),
        arrivedShopAt = o.optNullableString("arrivedShopAt"),
        arrivedCustomerAt = o.optNullableString("arrivedCustomerAt"),
        shopCash = o.optNullableDouble("shopCash"),
        customerCash = o.optNullableDouble("customerCash"),
        customerSubtotal = o.optNullableDouble("customerSubtotal"),
        customerDeliveryFee = o.optNullableDouble("customerDeliveryFee"),
        customerServiceFee = o.optNullableDouble("customerServiceFee")
    )

    private fun laundryJobToJson(job: RiderLaundryJob): JSONObject = JSONObject()
        .put("jobId", job.jobId)
        .put("laundryOrderId", job.laundryOrderId)
        .put("orderNumber", job.orderNumber)
        .put("leg", job.leg)
        .put("jobStatus", job.jobStatus)
        .put("orderStatus", job.orderStatus)
        .put("hubId", job.hubId)
        .put("hubName", job.hubName)
        .put("shopName", job.shopName)
        .put("serviceName", job.serviceName)
        .put("actualQuantity", job.actualQuantity)
        .put("pricingType", job.pricingType)
        .put("jobFee", job.jobFee)
        .put("fromAddress", job.fromAddress)
        .put("fromLatitude", job.fromLatitude)
        .put("fromLongitude", job.fromLongitude)
        .put("toAddress", job.toAddress)
        .put("toLatitude", job.toLatitude)
        .put("toLongitude", job.toLongitude)
        .put("customerAmount", job.customerAmount)
        .put("actualKg", job.actualKg)
        .put("createdAt", job.createdAt)

    private fun laundryJobFromJson(o: JSONObject): RiderLaundryJob = RiderLaundryJob(
        jobId = o.getString("jobId"),
        laundryOrderId = o.getString("laundryOrderId"),
        orderNumber = o.optNullableString("orderNumber"),
        leg = o.getString("leg"),
        jobStatus = o.getString("jobStatus"),
        orderStatus = o.optNullableString("orderStatus"),
        hubId = o.optNullableString("hubId"),
        hubName = o.optNullableString("hubName"),
        shopName = o.optNullableString("shopName"),
        serviceName = o.optNullableString("serviceName"),
        actualQuantity = o.optNullableDouble("actualQuantity"),
        pricingType = o.optNullableString("pricingType"),
        jobFee = o.optDouble("jobFee", 0.0),
        fromAddress = o.optNullableString("fromAddress"),
        fromLatitude = o.optNullableDouble("fromLatitude"),
        fromLongitude = o.optNullableDouble("fromLongitude"),
        toAddress = o.optNullableString("toAddress"),
        toLatitude = o.optNullableDouble("toLatitude"),
        toLongitude = o.optNullableDouble("toLongitude"),
        customerAmount = o.optNullableDouble("customerAmount"),
        actualKg = o.optNullableDouble("actualKg"),
        createdAt = o.optNullableString("createdAt")
    )

    private fun JSONArray?.jsonObjects(): List<JSONObject> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.let(::add)
            }
        }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key).takeIf { it.isFinite() }
    }

    fun pendingRegistrationUserId(): String? = prefs.getString("pending_registration_user", null)

    fun saveRegistrationCheckpoint(auth: QueueGoAuth) {
        save(auth)
        check(prefs.edit().putString("pending_registration_user", auth.session.authUserId).commit())
    }

    fun finishRegistration() {
        check(prefs.edit().remove("pending_registration_user").commit())
        java.io.File(context.noBackupFilesDir, "rider-registration.enc").delete()
    }

    @Synchronized
    fun saveRegistrationDraft(value: JSONObject) {
        val encrypted = encrypt(value.toString())
        val target = java.io.File(context.noBackupFilesDir, "rider-registration.enc")
        val temporary = java.io.File(context.noBackupFilesDir, "rider-registration.tmp")
        java.io.FileOutputStream(temporary).use { stream ->
            stream.write(encrypted.toByteArray(Charsets.UTF_8)); stream.fd.sync()
        }
        check(temporary.renameTo(target)) { "บันทึกใบสมัครในเครื่องไม่สำเร็จ" }
    }

    @Synchronized
    fun loadRegistrationDraft(): JSONObject? {
        val target = java.io.File(context.noBackupFilesDir, "rider-registration.enc")
        if (!target.exists()) return null
        require(target.length() <= 100 * 1024 * 1024) { "ใบสมัครในเครื่องไม่ถูกต้อง" }
        return JSONObject(decrypt(target.readText()))
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 12)
        val iv = bytes.copyOfRange(0, 12)
        val ciphertext = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }
}
