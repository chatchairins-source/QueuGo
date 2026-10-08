package com.queuego.customer

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
    val settings: Map<String, LaundrySettings>
)

class CustomerLaundryApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun catalog(auth: NativeAuth): LaundryCatalog {
        val enabledRaw = http.rpc(
            "queuego_feature_enabled",
            auth.session.accessToken,
            JSONObject().put("p_feature", "laundry").put("p_at", JSONObject.NULL)
        )
        val enabled = when (enabledRaw) {
            is Boolean -> enabledRaw
            is JSONArray -> enabledRaw.optBoolean(0, false)
            else -> enabledRaw.toString().toBooleanStrictOrNull() ?: false
        }

        val hubsRaw = http.array(http.get(
            "laundry_hubs?select=id,name,shop_id&active=eq.true&order=name.asc",
            auth.session.accessToken
        ))
        val settingsRaw = http.array(http.get(
            "laundry_shop_settings?select=hub_id,enabled,accepts_pickup,accepts_return,minimum_order,base_pickup_fee,return_fee,round_trip_fee,delivery_fee_mode" +
                "&enabled=eq.true&accepts_pickup=eq.true&accepts_return=eq.true",
            auth.session.accessToken
        ))

        val settings = buildMap {
            for (i in 0 until settingsRaw.length()) {
                val r = settingsRaw.optJSONObject(i) ?: continue
                val id = r.optString("hub_id")
                if (id.isBlank()) continue
                put(id, LaundrySettings(
                    id,
                    r.optBoolean("enabled", true),
                    r.optDouble("minimum_order", 0.0),
                    r.optDouble("base_pickup_fee", 0.0),
                    r.optDouble("return_fee", 0.0),
                    r.optDouble("round_trip_fee", 0.0),
                    r.optString("delivery_fee_mode").ifBlank { "separate" }
                ))
            }
        }

        val hubs = buildList {
            for (i in 0 until hubsRaw.length()) {
                val r = hubsRaw.optJSONObject(i) ?: continue
                val id = r.optString("id")
                if (id.isBlank() || !settings.containsKey(id)) continue
                add(LaundryHub(
                    id,
                    r.optString("name").ifBlank { "ร้านฝากซัก" },
                    r.optString("shop_id").takeIf { it.isNotBlank() && it != "null" }
                ))
            }
        }

        return LaundryCatalog(enabled, hubs, settings)
    }

    suspend fun services(auth: NativeAuth, hubId: String): List<LaundryService> {
        val rows = http.array(http.get(
            "laundry_services?select=id,hub_id,name,price,pricing_type,description,estimated_minutes" +
                "&hub_id=eq." + http.enc(hubId) + "&active=eq.true&order=sort_order.asc",
            auth.session.accessToken
        ))
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                add(LaundryService(
                    r.optString("id"),
                    r.optString("hub_id"),
                    r.optString("name").ifBlank { "บริการฝากซัก" },
                    r.optString("description").takeIf { it.isNotBlank() && it != "null" },
                    r.optDouble("price", 0.0),
                    r.optString("pricing_type").ifBlank { "fixed" },
                    if (r.has("estimated_minutes") && !r.isNull("estimated_minutes")) r.optInt("estimated_minutes") else null
                ))
            }
        }
    }

    suspend fun place(
        auth: NativeAuth,
        hub: LaundryHub,
        service: LaundryService,
        location: CustomerLocation,
        estimatedQuantity: Double?,
        note: String?
    ): JSONObject {
        val raw = http.rpc(
            "queuego_place_laundry_order_v2",
            auth.session.accessToken,
            JSONObject()
                .put("p_request_id", UUID.randomUUID().toString())
                .put("p_hub_id", hub.id)
                .put("p_service_id", service.id)
                .put("p_pickup_address", location.address)
                .put("p_pickup_latitude", location.latitude)
                .put("p_pickup_longitude", location.longitude)
                .put("p_estimated_quantity", estimatedQuantity)
                .put("p_note", note?.trim()?.takeIf { it.isNotBlank() })
        )
        return when (raw) {
            is JSONObject -> raw
            is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
            else -> JSONObject()
        }.also {
            if (it.length() == 0) error("ระบบยังไม่ยืนยันคำขอฝากซัก")
        }
    }
}
