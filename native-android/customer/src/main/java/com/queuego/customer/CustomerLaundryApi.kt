package com.queuego.customer

import com.queuego.shared.nativeOrderNumber

import com.queuego.shared.NativeAuth
import com.queuego.shared.QueueGoNativeApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LaundryHub(
    val id: String,
    val name: String,
    val shopId: String?
)

data class LaundrySettings(
    val hubId: String,
    val enabled: Boolean,
    val minimumOrder: Double,
    val pickupFee: Double,
    val returnFee: Double,
    val roundTripFee: Double,
    val deliveryFeeMode: String
) {
    val deliveryFee: Double
        get() = if (deliveryFeeMode == "round_trip") roundTripFee else pickupFee + returnFee
}

private fun JSONObject.optDoubleNullable(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

data class LaundryService(
    val id: String,
    val hubId: String,
    val name: String,
    val description: String?,
    val price: Double,
    val pricingType: String,
    val estimatedMinutes: Int?
)

data class LaundryCatalog(
    val featureEnabled: Boolean,
    val hubs: List<LaundryHub>,
    val settings: Map<String, LaundrySettings>,
    val servicesByHub: Map<String, List<LaundryService>> = emptyMap()
)

data class LaundryOrderSummary(
    val id: String,
    val number: String,
    val status: String,
    val serviceName: String,
    val estimatedTotal: Double?,
    val finalTotal: Double?,
    val pickupAddress: String?,
    val createdAt: String?
)

data class LaundryEvent(
    val status: String,
    val note: String?,
    val createdAt: String?
)

internal fun parseCustomerLaundryCatalog(raw: JSONObject): LaundryCatalog {
    if (!raw.optBoolean("enabled", false)) return LaundryCatalog(false, emptyList(), emptyMap())
    val rows = raw.optJSONArray("hubs") ?: JSONArray()
    val hubs = mutableListOf<LaundryHub>()
    val settings = linkedMapOf<String, LaundrySettings>()
    val servicesByHub = linkedMapOf<String, List<LaundryService>>()
    for (index in 0 until rows.length()) {
        val row = rows.optJSONObject(index) ?: continue
        val id = row.optString("id")
        val shopId = row.optString("shop_id")
        val config = row.optJSONObject("settings") ?: continue
        if (id.isBlank() || shopId.isBlank() || shopId == "null" || id in settings) continue
        val serviceRows = row.optJSONArray("services") ?: continue
        val services = (0 until serviceRows.length()).mapNotNull { serviceIndex ->
            val service = serviceRows.optJSONObject(serviceIndex) ?: return@mapNotNull null
            val serviceId = service.optString("id")
            val price = service.optDouble("price", 0.0)
            val pricingType = service.optString("pricing_type")
            if (serviceId.isBlank() || !price.isFinite() || price < 0 ||
                pricingType !in setOf("per_kg", "per_item", "per_set", "fixed")) return@mapNotNull null
            LaundryService(serviceId, id, service.optString("name").ifBlank { "บริการฝากซัก" },
                service.optString("description").takeIf { it.isNotBlank() && it != "null" },
                price, pricingType,
                if (service.has("estimated_minutes") && !service.isNull("estimated_minutes")) service.optInt("estimated_minutes") else null)
        }
        val configRow = LaundrySettings(id, true, config.optDouble("minimum_order", 0.0),
            config.optDouble("pickup_fee", 0.0), config.optDouble("return_fee", 0.0),
            config.optDouble("round_trip_fee", 0.0), config.optString("delivery_fee_mode").ifBlank { "separate" })
        if (services.isEmpty() || listOf(configRow.minimumOrder, configRow.pickupFee,
                configRow.returnFee, configRow.roundTripFee).any { !it.isFinite() || it < 0 }) continue
        hubs += LaundryHub(id, row.optString("name").ifBlank {
            row.optString("shop_name").ifBlank { "ร้านฝากซัก" }
        }, shopId)
        settings[id] = configRow
        servicesByHub[id] = services
    }
    return LaundryCatalog(true, hubs, settings, servicesByHub)
}

internal fun LaundryCatalog.servicesForCustomerHub(hubId: String): List<LaundryService> {
    check(featureEnabled && hubs.any { it.id == hubId }) {
        "ร้านฝากซักนี้ยังไม่พร้อมให้บริการ กรุณาเลือกร้านใหม่"
    }
    return servicesByHub[hubId].orEmpty().also {
        check(it.isNotEmpty()) { "ร้านฝากซักนี้ยังไม่พร้อมให้บริการ กรุณาเลือกร้านใหม่" }
    }
}

class CustomerLaundryApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun catalog(auth: NativeAuth): LaundryCatalog =
        parseCustomerLaundryCatalog(http.obj(http.rpc("queuego_laundry_customer_catalog",
            auth.session.accessToken, JSONObject())))

    suspend fun services(auth: NativeAuth, hubId: String): List<LaundryService> {
        return catalog(auth).servicesForCustomerHub(hubId)
    }

    suspend fun orders(auth: NativeAuth): List<LaundryOrderSummary> {
        val rows = http.array(http.get(
            "laundry_orders?select=id,order_number,status,service_name_snapshot,estimated_total_amount,final_total_amount,pickup_address,created_at" +
                "&customer_id=eq." + http.enc(auth.user.id) + "&order=created_at.desc&limit=50", auth.session.accessToken))
        return (0 until rows.length()).mapNotNull { rows.optJSONObject(it)?.let(::parseLaundryOrderDetails)?.summary }
    }

    internal suspend fun order(auth: NativeAuth, orderId: String): LaundryOrderDetails? {
        val rows = http.array(http.get("laundry_orders?select=id,order_number,status,service_name_snapshot,service_type,estimated_total_amount,final_total_amount,pickup_address,created_at,pricing_type_snapshot,unit_price_snapshot,actual_quantity,actual_kg,estimated_quantity,estimated_kg,final_amount,estimated_amount,pickup_fee_snapshot,return_fee_snapshot,delivery_fee_total_snapshot" +
            "&id=eq." + http.enc(orderId) + "&customer_id=eq." + http.enc(auth.user.id) + "&limit=1", auth.session.accessToken))
        return rows.optJSONObject(0)?.let(::parseLaundryOrderDetails)
    }

    suspend fun events(auth: NativeAuth, orderId: String): List<LaundryEvent> {
        val rows = http.array(http.get(
            "laundry_order_events?select=to_status,note,created_at&laundry_order_id=eq." +
                http.enc(orderId) + "&order=created_at.asc",
            auth.session.accessToken
        ))
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                add(LaundryEvent(
                    r.optString("to_status"),
                    r.optString("note").takeIf { it.isNotBlank() && it != "null" },
                    r.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
                ))
            }
        }
    }

    internal fun prepareCheckout(
        hub: LaundryHub,
        service: LaundryService,
        location: CustomerLocation,
        estimatedQuantity: Double?,
        note: String?
    ) = PendingLaundryCheckout(JSONObject()
        .put("p_request_id", UUID.randomUUID().toString())
        .put("p_hub_id", hub.id).put("p_service_id", service.id)
        .put("p_pickup_address", location.address.trim())
        .put("p_pickup_latitude", location.latitude).put("p_pickup_longitude", location.longitude)
        .put("p_estimated_quantity", estimatedQuantity ?: JSONObject.NULL)
        .put("p_note", note?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL))

    internal suspend fun sendCheckout(auth: NativeAuth, pending: PendingLaundryCheckout): JSONObject {
        val raw = http.rpc("queuego_place_laundry_order_v2", auth.session.accessToken, pending.body)
        val receipt = when (raw) {
            is JSONObject -> raw
            is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
            else -> JSONObject()
        }
        runCatching { UUID.fromString(receipt.getString("id")) }
            .getOrElse { error("ระบบยังไม่ยืนยันคำขอฝากซัก กรุณาลองตรวจผลอีกครั้ง") }
        return receipt
    }

    internal suspend fun recoverCheckout(auth: NativeAuth, pending: PendingLaundryCheckout): JSONObject? {
        val rows = http.array(http.get("laundry_orders?select=id,order_number,status&customer_id=eq." +
            http.enc(auth.user.id) + "&request_key=eq." + http.enc(pending.body.getString("p_request_id")) + "&limit=1", auth.session.accessToken))
        return rows.optJSONObject(0)?.also { UUID.fromString(it.getString("id")) }
    }

}


private fun laundryFormatNumber(raw: String?, id: String): String = nativeOrderNumber(raw)

internal data class LaundryOrderDetails(val summary: LaundryOrderSummary, val pricingType: String,
    val unitPrice: Double?, val quantity: Double?, val actualQuantity: Boolean,
    val serviceAmount: Double?, val pickupFee: Double, val returnFee: Double)

internal fun parseLaundryOrderDetails(row: JSONObject): LaundryOrderDetails {
    val id = row.getString("id")
    val pickup = row.optDoubleNullable("pickup_fee_snapshot") ?: 0.0
    val returned = row.optDoubleNullable("return_fee_snapshot") ?: 0.0
    val service = row.optDoubleNullable("final_amount") ?: row.optDoubleNullable("estimated_amount")
    val delivery = row.optDoubleNullable("delivery_fee_total_snapshot") ?: (pickup + returned)
    val finalTotal = row.optDoubleNullable("final_total_amount")
    val estimatedTotal = row.optDoubleNullable("estimated_total_amount") ?: service?.plus(delivery)
    return LaundryOrderDetails(LaundryOrderSummary(id, laundryFormatNumber(row.optString("order_number"), id),
        row.optString("status"), row.optString("service_name_snapshot").takeIf { it.isNotBlank() && it != "null" }
            ?: row.optString("service_type").takeIf { it.isNotBlank() && it != "null" } ?: "บริการฝากซัก",
        estimatedTotal, finalTotal, row.optString("pickup_address").takeIf { it.isNotBlank() && it != "null" },
        row.optString("created_at").takeIf { it.isNotBlank() && it != "null" }),
        row.optString("pricing_type_snapshot"), row.optDoubleNullable("unit_price_snapshot"),
        row.optDoubleNullable("actual_quantity") ?: row.optDoubleNullable("actual_kg")
            ?: row.optDoubleNullable("estimated_quantity") ?: row.optDoubleNullable("estimated_kg"),
        row.optDoubleNullable("actual_quantity") != null || row.optDoubleNullable("actual_kg") != null,
        service, pickup, returned)
}
