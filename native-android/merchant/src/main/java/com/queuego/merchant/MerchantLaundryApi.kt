package com.queuego.merchant

import com.queuego.shared.NativeAuth
import com.queuego.shared.QueueGoNativeApi
import org.json.JSONArray
import org.json.JSONObject

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
    val note: String?
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

        val orders = if (hubId == null) emptyList() else {
            val rows = http.array(http.get(
                "laundry_orders?select=id,order_number,status,service_name_snapshot,pricing_type_snapshot,estimated_quantity,actual_quantity,estimated_total_amount,final_total_amount,pickup_address,note" +
                    "&hub_id=eq." + http.enc(hubId) + "&order=created_at.desc&limit=100",
                auth.session.accessToken
            ))
            buildList {
                for (i in 0 until rows.length()) {
                    val r = rows.optJSONObject(i) ?: continue
                    val id = r.optString("id")
                    if (id.isBlank()) continue
                    add(MerchantLaundryOrder(
                        id,
                        merchantLaundryNumber(r.optString("order_number"), id),
                        r.optString("status"),
                        r.optString("service_name_snapshot").ifBlank { "บริการฝากซัก" },
                        r.optString("pricing_type_snapshot").takeIf { it.isNotBlank() && it != "null" },
                        r.optDoubleNullable("estimated_quantity"),
                        r.optDoubleNullable("actual_quantity"),
                        r.optDoubleNullable("estimated_total_amount"),
                        r.optDoubleNullable("final_total_amount"),
                        r.optString("pickup_address").takeIf { it.isNotBlank() && it != "null" },
                        r.optString("note").takeIf { it.isNotBlank() && it != "null" }
                    ))
                }
            }
        }

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
        note: String? = null
    ) {
        http.rpc(
            "queuego_laundry_shop_action_v2",
            auth.session.accessToken,
            JSONObject()
                .put("p_order_id", orderId)
                .put("p_action", action)
                .put("p_actual_quantity", actualQuantity)
                .put("p_note", note?.trim()?.takeIf { it.isNotBlank() })
        )
    }
}

private fun JSONObject.optDoubleNullable(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

private fun merchantLaundryNumber(raw: String?, id: String): String {
    val clean = raw.orEmpty().trim()
    if (clean.startsWith("QT-", true)) return clean.uppercase()
    val digits = clean.filter { it.isDigit() }.takeLast(4)
    if (digits.length == 4) return "QT-" + digits
    return "QT-" + kotlin.math.abs(id.hashCode() % 10000).toString().padStart(4, '0')
}
