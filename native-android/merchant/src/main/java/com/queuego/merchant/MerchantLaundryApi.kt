package com.queuego.merchant

import com.queuego.shared.nativeOrderNumber

import com.queuego.shared.QueueGoHttpException
import com.queuego.shared.NativeAuth
import com.queuego.shared.QueueGoNativeApi
import org.json.JSONArray
import org.json.JSONObject

private const val laundryOrderColumns =
    "id,order_number,status,service_name_snapshot,pricing_type_snapshot,estimated_quantity,actual_quantity," +
        "estimated_total_amount,final_total_amount,pickup_address,note,pickup_latitude,pickup_longitude"
private const val laundryOptionalColumns =
    "pickup_staff_name,return_staff_name,gp_rate_snapshot,platform_fee_amount,shop_net_amount"

internal suspend fun readLaundryOrdersWithSchemaCompatibility(
    scope: String,
    read: suspend (String) -> JSONArray
): JSONArray {
    val base = "laundry_orders?select=$laundryOrderColumns"
    return try {
        read("$base,$laundryOptionalColumns$scope")
    } catch (error: QueueGoHttpException) {
        val message = error.message.orEmpty()
        val missingOptionalColumn = laundryOptionalColumns.split(',').any { column ->
            Regex("column (?:laundry_orders\\.)?" + column + " does not exist", RegexOption.IGNORE_CASE)
                .containsMatchIn(message) ||
                message.equals("Could not find the '$column' column of 'laundry_orders' in the schema cache", ignoreCase = true)
        }
        if (error.statusCode != 400 || !missingOptionalColumn) throw error
        // Keep the identical owner hub, ordering and limit; never synthesize finance values.
        read("$base$scope")
    }
}

internal fun merchantLaundryGpRate(row: JSONObject): Double? =
    if (!row.has("gp_rate_snapshot") || row.isNull("gp_rate_snapshot")) null
    else row.optDouble("gp_rate_snapshot", Double.NaN).takeIf { it.isFinite() && it in 0.0..1.0 }

data class MerchantLaundrySettings(
    val enabled: Boolean,
    val minimumOrder: Double,
    val pickupFee: Double,
    val returnFee: Double,
    val roundTripFee: Double,
    val deliveryFeeMode: String
)

data class MerchantLaundryService(
    val id: String,
    val name: String,
    val description: String?,
    val pricingType: String,
    val price: Double,
    val estimatedMinutes: Int?,
    val active: Boolean
)

data class MerchantLaundryOrder(
    val id: String,
    val number: String,
    val status: String,
    val serviceName: String,
    val pricingType: String?,
    val estimatedQuantity: Double?,
    val actualQuantity: Double?,
    val estimatedTotal: Double?,
    val finalTotal: Double?,
    val pickupAddress: String?,
    val note: String?,
    val pickupStaffName: String? = null,
    val returnStaffName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val gpRate: Double? = null,
    val platformFee: Double? = null,
    val shopNet: Double? = null
)

data class MerchantLaundryState(
    val featureEnabled: Boolean,
    val hubId: String?,
    val hubName: String?,
    val settings: MerchantLaundrySettings,
    val services: List<MerchantLaundryService>,
    val orders: List<MerchantLaundryOrder>
)

class MerchantLaundryApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun load(auth: NativeAuth): MerchantLaundryState {
        val featureRaw = http.rpc(
            "queuego_feature_enabled",
            auth.session.accessToken,
            JSONObject().put("p_feature", "laundry").put("p_at", JSONObject.NULL)
        )
        val feature = when (featureRaw) {
            is Boolean -> featureRaw
            is JSONArray -> featureRaw.optBoolean(0, false)
            else -> featureRaw.toString().toBooleanStrictOrNull() ?: false
        }

        val rawState = http.rpc("queuego_laundry_merchant_state", auth.session.accessToken, JSONObject())
        val state = when (rawState) {
            is JSONObject -> rawState
            is JSONArray -> rawState.optJSONObject(0) ?: JSONObject()
            else -> JSONObject()
        }
        val hub = state.optJSONObject("hub")
        val settingsRaw = state.optJSONObject("settings") ?: JSONObject()
        val servicesRaw = state.optJSONArray("services") ?: JSONArray()
        val hubId = hub?.optString("id")?.takeIf { it.isNotBlank() && it != "null" }

        val services = buildList {
            for (i in 0 until servicesRaw.length()) {
                val r = servicesRaw.optJSONObject(i) ?: continue
                add(MerchantLaundryService(
                    r.optString("id"),
                    r.optString("name").ifBlank { "บริการฝากซัก" },
                    r.optString("description").takeIf { it.isNotBlank() && it != "null" },
                    r.optString("pricing_type").ifBlank { "fixed" },
                    r.optDouble("price", 0.0),
                    if (r.has("estimated_minutes") && !r.isNull("estimated_minutes")) r.optInt("estimated_minutes") else null,
                    r.optBoolean("active", true)
                ))
            }
        }

        val orders = if (hubId == null) emptyList() else loadOrders(auth, hubId)

        return MerchantLaundryState(
            feature,
            hubId,
            hub?.optString("name")?.takeIf { it.isNotBlank() && it != "null" },
            MerchantLaundrySettings(
                settingsRaw.optBoolean("enabled", false),
                settingsRaw.optDouble("minimum_order", 0.0),
                settingsRaw.optDouble("base_pickup_fee", 0.0),
                settingsRaw.optDouble("return_fee", 0.0),
                settingsRaw.optDouble("round_trip_fee", 0.0),
                settingsRaw.optString("delivery_fee_mode").ifBlank { "separate" }
            ),
            services,
            orders
        )
    }

    suspend fun loadOrders(auth: NativeAuth, hubId: String): List<MerchantLaundryOrder> {
        if (hubId.isBlank()) return emptyList()
        val contacts = http.array(
            http.rpc(
                "queuego_laundry_shop_contacts",
                auth.session.accessToken,
                JSONObject().put("p_hub_id", hubId)
            )
        )
        val contactByOrder = (0 until contacts.length()).mapNotNull { contacts.optJSONObject(it) }
            .associateBy { it.optString("order_id") }
        val rows = readLaundryOrdersWithSchemaCompatibility(
            "&hub_id=eq." + http.enc(hubId) + "&order=created_at.desc&limit=100"
        ) { path -> http.array(http.get(path, auth.session.accessToken)) }
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val id = r.optString("id")
                if (id.isBlank()) continue
                add(
                    MerchantLaundryOrder(
                        id = id,
                        number = merchantLaundryNumber(r.optString("order_number"), id),
                        status = r.optString("status"),
                        serviceName = r.optString("service_name_snapshot").ifBlank { "บริการฝากซัก" },
                        pricingType = r.optString("pricing_type_snapshot").takeIf { it.isNotBlank() && it != "null" },
                        estimatedQuantity = r.optDoubleNullable("estimated_quantity"),
                        actualQuantity = r.optDoubleNullable("actual_quantity"),
                        estimatedTotal = r.optDoubleNullable("estimated_total_amount"),
                        finalTotal = r.optDoubleNullable("final_total_amount"),
                        pickupAddress = r.optString("pickup_address").takeIf { it.isNotBlank() && it != "null" },
                        note = r.optString("note").takeIf { it.isNotBlank() && it != "null" },
                        pickupStaffName = r.optString("pickup_staff_name").takeIf { it.isNotBlank() && it != "null" },
                        returnStaffName = r.optString("return_staff_name").takeIf { it.isNotBlank() && it != "null" },
                        latitude = r.optDoubleNullable("pickup_latitude"),
                        longitude = r.optDoubleNullable("pickup_longitude"),
                        customerName = contactByOrder[id]?.optString("customer_name")?.takeIf { it.isNotBlank() && it != "null" },
                        customerPhone = contactByOrder[id]?.optString("customer_phone")?.takeIf { it.isNotBlank() && it != "null" },
                        gpRate = merchantLaundryGpRate(r),
                        platformFee = r.optDoubleNullable("platform_fee_amount"),
                        shopNet = r.optDoubleNullable("shop_net_amount")
                    )
                )
            }
        }
    }

    suspend fun setup(auth: NativeAuth) {
        http.rpc("queuego_laundry_merchant_setup", auth.session.accessToken, JSONObject().put("p_name", JSONObject.NULL))
    }

    suspend fun saveSettings(auth: NativeAuth, settings: MerchantLaundrySettings) {
        http.rpc(
            "queuego_laundry_save_settings",
            auth.session.accessToken,
            JSONObject()
                .put("p_enabled", settings.enabled)
                .put("p_minimum_order", settings.minimumOrder)
                .put("p_pickup_fee", settings.pickupFee)
                .put("p_return_fee", settings.returnFee)
                .put("p_round_trip_fee", settings.roundTripFee)
                .put("p_delivery_fee_mode", settings.deliveryFeeMode)
        )
    }

    suspend fun saveService(
        auth: NativeAuth,
        serviceId: String?,
        name: String,
        description: String?,
        pricingType: String,
        price: Double,
        estimatedMinutes: Int?,
        active: Boolean
    ) {
        http.rpc(
            "queuego_laundry_save_service",
            auth.session.accessToken,
            JSONObject()
                .put("p_service_id", serviceId ?: JSONObject.NULL)
                .put("p_name", name.trim())
                .put("p_description", description?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("p_pricing_type", pricingType)
                .put("p_price", price)
                .put("p_estimated_minutes", estimatedMinutes ?: JSONObject.NULL)
                .put("p_active", active)
        )
    }

    suspend fun orderAction(
        auth: NativeAuth,
        orderId: String,
        action: String,
        actualQuantity: Double? = null,
        note: String? = null,
        expectedStatus: String,
        staffName: String? = null
    ) {
        http.rpc(
            "queuego_laundry_shop_manage",
            auth.session.accessToken,
            JSONObject()
                .put("p_order_id", orderId)
                .put("p_action", action)
                .put("p_actual_quantity", actualQuantity ?: JSONObject.NULL)
                .put("p_note", note?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("p_expected_status", expectedStatus)
                .put("p_staff_name", staffName?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        )
    }
}

private fun JSONObject.optDoubleNullable(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

private fun merchantLaundryNumber(raw: String?, id: String): String = nativeOrderNumber(raw)
