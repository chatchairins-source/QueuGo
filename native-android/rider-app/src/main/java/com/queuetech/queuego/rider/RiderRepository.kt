package com.queuetech.queuego.rider

import com.queuetech.queuego.core.auth.SecureSessionStore
import com.queuetech.queuego.core.network.QueueGoApi
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

class RiderRepository(
    private val api: QueueGoApi,
    private val sessionStore: SecureSessionStore,
) {
    suspend fun loadProfile(userId: String): RiderProfileState {
        val token = accessToken()
        val encoded = encode(userId)
        val raw = api.rest(
            token,
            "rider_profiles?select=id,user_id,status,metadata,latitude,longitude,vehicle_type,vehicle_plate&user_id=eq." + encoded + "&limit=1",
        )
        val rows = JSONArray(raw)
        if (rows.length() == 0) throw IllegalStateException("ไม่พบโปรไฟล์ QueueGo Rider")
        return parseProfile(rows.getJSONObject(0))
    }

    suspend fun setOnline(
        userId: String,
        enabled: Boolean,
        coordinate: RiderCoordinate? = null,
    ): RiderProfileState {
        val token = accessToken()
        val current = loadProfile(userId)
        if (current.status != "active") {
            throw IllegalStateException("บัญชีไรเดอร์ยังไม่ผ่านการอนุมัติ")
        }

        val metadata = runCatching { JSONObject(current.metadataJson) }.getOrElse { JSONObject() }
            .put("online", enabled)
            .put("available", enabled)

        val body = JSONObject().put("metadata", metadata)
        if (enabled && coordinate != null) {
            body.put("latitude", coordinate.latitude)
            body.put("longitude", coordinate.longitude)
        }

        val encoded = encode(userId)
        val raw = api.rest(
            accessToken = token,
            path = "rider_profiles?user_id=eq." + encoded + "&select=id,user_id,status,metadata,latitude,longitude,vehicle_type,vehicle_plate",
            method = "PATCH",
            body = body,
            preferRepresentation = true,
        )
        val rows = JSONArray(raw)
        return if (rows.length() > 0) parseProfile(rows.getJSONObject(0)) else loadProfile(userId)
    }

    suspend fun loadOffer(): RiderOffer? {
        val token = accessToken()
        val offerRows = JSONArray(api.rpc(token, "qg_get_my_rider_offer"))
        if (offerRows.length() == 0) return null

        val offerMeta = offerRows.getJSONObject(0)
        val orderId = offerMeta.optString("order_id")
        if (orderId.isBlank()) return null

        val poolRows = JSONArray(api.rpc(token, "get_rider_delivery_pool"))
        var pool: JSONObject? = null
        for (index in 0 until poolRows.length()) {
            val item = poolRows.optJSONObject(index) ?: continue
            if (item.optString("order_id") == orderId) {
                pool = item
                break
            }
        }
        val delivery = pool ?: return null

        val orderRows = JSONArray(
            api.rest(
                token,
                "orders?select=id,market_order_id,fulfillment_vertical&id=eq." + encode(orderId) + "&limit=1",
            ),
        )
        val orderMeta = orderRows.optJSONObject(0)
        val expiresAt = RiderServerTime.parseMillis(offerMeta.optString("expires_at"))
            ?: throw IllegalStateException("Server ไม่ส่งเวลาหมดอายุของงาน")

        return RiderOffer(
            orderId = orderId,
            orderNumber = delivery.optString("order_number").ifBlank { orderId.takeLast(4) },
            shopName = delivery.optString("shop_name").ifBlank { "ร้านค้า" },
            pickupLatitude = delivery.optNullableDouble("pickup_latitude"),
            pickupLongitude = delivery.optNullableDouble("pickup_longitude"),
            deliveryAddress = delivery.optString("delivery_address"),
            deliveryLatitude = delivery.optNullableDouble("delivery_latitude"),
            deliveryLongitude = delivery.optNullableDouble("delivery_longitude"),
            distanceKm = delivery.optNullableDouble("distance_km"),
            deliveryFee = delivery.optDouble("delivery_fee", 0.0),
            expiresAtMillis = expiresAt,
            attempt = offerMeta.optInt("attempt", 1),
            marketOrderId = orderMeta?.optNullableString("market_order_id"),
            fulfillmentVertical = orderMeta?.optString("fulfillment_vertical").orEmpty(),
        )
    }

    suspend fun loadActiveJob(profileId: String): RiderActiveJob? {
        val token = accessToken()
        val statuses = "rider_assigned,preparing,ready,assigned,picked_up,in_progress"
        val path = buildString {
            append("orders?select=id,order_number,status,shop_id,pickup_address,pickup_latitude,pickup_longitude,")
            append("delivery_address,delivery_latitude,delivery_longitude,total_amount,delivery_fee,market_order_id,fulfillment_vertical")
            append("&rider_id=eq.")
            append(encode(profileId))
            append("&status=in.(")
            append(statuses)
            append(")&order=created_at.desc&limit=1")
        }
        val rows = JSONArray(api.rest(token, path))
        if (rows.length() == 0) return null
        val order = rows.getJSONObject(0)

        val shopId = order.optString("shop_id")
        val shopName = if (shopId.isBlank()) {
            "ร้านค้า"
        } else {
            val shops = JSONArray(
                api.rest(
                    token,
                    "shop_profiles?select=id,shop_name&id=eq." + encode(shopId) + "&limit=1",
                ),
            )
            shops.optJSONObject(0)?.optString("shop_name").orEmpty().ifBlank { "ร้านค้า" }
        }

        return RiderActiveJob(
            orderId = order.getString("id"),
            orderNumber = order.optString("order_number").ifBlank { order.getString("id").takeLast(4) },
            status = order.optString("status"),
            shopName = shopName,
            pickupAddress = order.optString("pickup_address"),
            pickupLatitude = order.optNullableDouble("pickup_latitude"),
            pickupLongitude = order.optNullableDouble("pickup_longitude"),
            deliveryAddress = order.optString("delivery_address"),
            deliveryLatitude = order.optNullableDouble("delivery_latitude"),
            deliveryLongitude = order.optNullableDouble("delivery_longitude"),
            totalAmount = order.optDouble("total_amount", 0.0),
            deliveryFee = order.optDouble("delivery_fee", 0.0),
            marketOrderId = order.optNullableString("market_order_id"),
            fulfillmentVertical = order.optString("fulfillment_vertical"),
        )
    }

    suspend fun acceptOffer(userId: String, offer: RiderOffer) {
        val token = accessToken()
        val kind = if (offer.marketOrderId.isNullOrBlank()) "claim" else "market_claim"
        val payload = JSONObject().put("p_order_id", offer.orderId)
        val requestId = RiderActionIds.stable(userId, kind, offer.orderId)
        api.rpc(
            token,
            "qg_rider_action_once",
            JSONObject()
                .put("p_request_id", requestId)
                .put("p_kind", kind)
                .put("p_payload", payload),
        )
    }

    suspend fun declineOffer(orderId: String) {
        val token = accessToken()
        api.rpc(
            token,
            "qg_rider_decline_offer",
            JSONObject().put("p_order_id", orderId),
        )
    }

    private fun parseProfile(item: JSONObject): RiderProfileState {
        val metadata = item.optJSONObject("metadata") ?: JSONObject()
        return RiderProfileState(
            id = item.getString("id"),
            userId = item.getString("user_id"),
            status = item.optString("status"),
            metadataJson = metadata.toString(),
            online = metadata.optBoolean("online", false),
            available = metadata.optBoolean("available", false),
            latitude = item.optNullableDouble("latitude"),
            longitude = item.optNullableDouble("longitude"),
            vehicleType = item.optString("vehicle_type"),
            vehiclePlate = item.optString("vehicle_plate"),
        )
    }

    private fun accessToken(): String =
        sessionStore.read()?.accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("กรุณาเข้าสู่ระบบใหม่")

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key).takeIf(Double::isFinite)
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf(String::isNotBlank)
    }
}
