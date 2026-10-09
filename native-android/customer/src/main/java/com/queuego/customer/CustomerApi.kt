package com.queuego.customer

import com.queuego.shared.NativeAuth
import com.queuego.shared.QueueGoNativeApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CustomerShop(
    val id: String,
    val name: String,
    val category: String,
    val logo: String?,
    val cover: String?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val open: Boolean
)

data class CustomerProduct(
    val id: String,
    val shopId: String,
    val name: String,
    val description: String?,
    val price: Double,
    val deliveryPrice: Double,
    val image: String?,
    val available: Boolean
)

data class CustomerOrder(
    val id: String,
    val number: String,
    val shopId: String?,
    val status: String,
    val subtotal: Double,
    val deliveryFee: Double,
    val total: Double,
    val deliveryAddress: String?,
    val createdAt: String?,
    val riderId: String? = null,
    val completedAt: String? = null,
    val updatedAt: String? = null,
    val pickupLatitude: Double? = null,
    val pickupLongitude: Double? = null,
    val deliveryLatitude: Double? = null,
    val deliveryLongitude: Double? = null
)

data class CustomerTrackingShop(
    val name: String,
    val phone: String?
)

data class CustomerTrackingRider(
    val name: String,
    val phone: String?,
    val photo: String?,
    val vehicleType: String?,
    val vehiclePlate: String?,
    val latitude: Double?,
    val longitude: Double?,
    val updatedAt: String?
)

data class CustomerOrderContext(
    val shop: CustomerTrackingShop?,
    val rider: CustomerTrackingRider?
)

data class CustomerOrderItem(
    val name: String,
    val quantity: Int,
    val totalPrice: Double,
    val image: String?
)

data class CustomerLocation(val latitude: Double, val longitude: Double, val address: String)
data class HomeBanner(val image: String, val title: String?, val subtitle: String?)
data class ServiceBanner(val key: String, val image: String?, val title: String?, val subtitle: String?, val active: Boolean)
data class CartLine(val product: CustomerProduct, val quantity: Int)

class CustomerApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun loadShops(auth: NativeAuth): List<CustomerShop> {
        val token = auth.session.accessToken
        val rows = http.array(http.get(
            "shop_profiles?select=id,shop_name,public_category,public_logo,public_cover,address,latitude,longitude,status&status=eq.active",
            token
        ))
        val states = runCatching {
            http.array(http.get("shop_open_states?select=shop_id,is_open,resume_at", token))
        }.getOrElse { JSONArray() }
        val open = HashMap<String, Boolean>()
        for (i in 0 until states.length()) {
            val row = states.optJSONObject(i) ?: continue
            open[row.optString("shop_id")] = row.optBoolean("is_open", true)
        }
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val id = row.optString("id")
                if (id.isBlank()) continue
                add(CustomerShop(
                    id,
                    row.optString("shop_name").ifBlank { "ร้านค้า" },
                    row.optString("public_category").ifBlank { "other" }.lowercase(),
                    row.optNullable("public_logo"),
                    row.optNullable("public_cover"),
                    row.optNullable("address"),
                    row.optDoubleOrNull("latitude"),
                    row.optDoubleOrNull("longitude"),
                    open[id] != false
                ))
            }
        }
    }

    suspend fun loadProducts(auth: NativeAuth, shopId: String): List<CustomerProduct> {
        val path = "products?select=id,shop_id,name,description,price,delivery_price,image,available,delivery_available" +
            "&shop_id=eq." + http.enc(shopId) + "&delivery_available=eq.true&order=name.asc"
        val rows = http.array(http.get(path, auth.session.accessToken))
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                if (!r.optBoolean("available", true)) continue
                add(CustomerProduct(
                    r.optString("id"),
                    r.optString("shop_id"),
                    r.optString("name").ifBlank { "สินค้า" },
                    r.optNullable("description"),
                    r.optDouble("price", 0.0),
                    r.optDouble("delivery_price", r.optDouble("price", 0.0)),
                    r.optNullable("image"),
                    true
                ))
            }
        }
    }

    suspend fun loadOrders(auth: NativeAuth): List<CustomerOrder> {
        val rows = http.array(http.get(
            "orders?select=id,order_number,shop_id,status,subtotal,delivery_fee,total_amount,delivery_address,created_at,rider_id,completed_at,updated_at,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude" +
                "&customer_id=eq." + http.enc(auth.user.id) + "&order=created_at.desc&limit=100",
            auth.session.accessToken
        ))
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                add(CustomerOrder(
                    r.optString("id"),
                    orderNumber(r.optString("order_number"), r.optString("id")),
                    r.optNullable("shop_id"),
                    r.optString("status"),
                    r.optDouble("subtotal", 0.0),
                    r.optDouble("delivery_fee", 0.0),
                    r.optDouble("total_amount", r.optDouble("subtotal", 0.0) + r.optDouble("delivery_fee", 0.0)),
                    r.optNullable("delivery_address"),
                    r.optNullable("created_at"),
                    r.optNullable("rider_id"),
                    r.optNullable("completed_at"),
                    r.optNullable("updated_at"),
                    r.optDoubleOrNull("pickup_latitude"),
                    r.optDoubleOrNull("pickup_longitude"),
                    r.optDoubleOrNull("delivery_latitude"),
                    r.optDoubleOrNull("delivery_longitude")
                ))
            }
        }
    }

    suspend fun cancelOrder(auth: NativeAuth, orderId: String) {
        http.rpc(
            "qg_customer_cancel_order",
            auth.session.accessToken,
            JSONObject().put("p_order_id", orderId)
        )
    }

    suspend fun loadOrderContext(auth: NativeAuth, orderId: String): CustomerOrderContext {
        val raw = http.rpc(
            "qg_customer_order_context",
            auth.session.accessToken,
            JSONObject().put("p_order_id", orderId)
        )
        val root = when (raw) {
            is JSONObject -> raw
            is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
            else -> JSONObject()
        }
        val shopObj = root.optJSONObject("shop")
        val riderObj = root.optJSONObject("rider")
        return CustomerOrderContext(
            shop = shopObj?.let {
                CustomerTrackingShop(
                    name = it.optString("name").ifBlank { "ร้านค้า" },
                    phone = it.optNullable("phone")
                )
            },
            rider = riderObj?.let {
                CustomerTrackingRider(
                    name = it.optString("name").ifBlank { "Rider" },
                    phone = it.optNullable("phone"),
                    photo = it.optNullable("photo"),
                    vehicleType = it.optNullable("vehicle_type"),
                    vehiclePlate = it.optNullable("vehicle_plate"),
                    latitude = it.optDoubleOrNull("latitude"),
                    longitude = it.optDoubleOrNull("longitude"),
                    updatedAt = it.optNullable("updated_at")
                )
            }
        )
    }

    suspend fun loadOrderItems(auth: NativeAuth, orderId: String): List<CustomerOrderItem> {
        val rows = http.array(http.get(
            "order_items?select=item_name,quantity,total_price,item_image&order_id=eq." +
                http.enc(orderId) + "&order=created_at.asc",
            auth.session.accessToken
        ))
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                add(CustomerOrderItem(
                    r.optString("item_name").ifBlank { "สินค้า" },
                    r.optInt("quantity", 1),
                    r.optDouble("total_price", 0.0),
                    r.optNullable("item_image")
                ))
            }
        }
    }

    suspend fun loadSavedLocation(auth: NativeAuth): CustomerLocation? {
        val rows = http.array(http.get(
            "users?select=metadata&id=eq." + http.enc(auth.user.id) + "&limit=1",
            auth.session.accessToken
        ))
        val metadata = rows.optJSONObject(0)?.optJSONObject("metadata") ?: return null
        val lat = metadata.optDoubleOrNull("lat") ?: return null
        val lng = metadata.optDoubleOrNull("lng") ?: return null
        return CustomerLocation(lat, lng, metadata.optString("address"))
    }

    suspend fun saveLocation(auth: NativeAuth, location: CustomerLocation) {
        val rows = http.array(http.get(
            "users?select=metadata&id=eq." + http.enc(auth.user.id) + "&limit=1",
            auth.session.accessToken
        ))
        val metadata = rows.optJSONObject(0)?.optJSONObject("metadata") ?: JSONObject()
        metadata.put("lat", location.latitude).put("lng", location.longitude).put("address", location.address)
        http.patch(
            "users?id=eq." + http.enc(auth.user.id),
            auth.session.accessToken,
            JSONObject().put("metadata", metadata)
        )
    }

    suspend fun loadHomeBanners(auth: NativeAuth): List<HomeBanner> {
        val rows = runCatching {
            http.array(http.get("system_settings?select=value&key=eq.home_service_banner&limit=1", auth.session.accessToken))
        }.getOrElse { return emptyList() }
        val value = rows.optJSONObject(0)?.optJSONObject("value") ?: return emptyList()
        val slides = value.optJSONArray("slides") ?: return emptyList()
        return buildList {
            for (i in 0 until slides.length().coerceAtMost(3)) {
                val slide = slides.optJSONObject(i) ?: continue
                if (!slide.optBoolean("active", true)) continue
                val image = slide.optString("image_url").ifBlank { slide.optString("image_data") }
                if (image.isNotBlank()) add(HomeBanner(image, slide.optNullable("title"), slide.optNullable("subtitle")))
            }
        }
    }

    suspend fun loadServiceBanners(auth: NativeAuth): Map<String, ServiceBanner> {
        val rows = runCatching {
            http.array(http.get("system_settings?select=value&key=eq.service_banners&limit=1", auth.session.accessToken))
        }.getOrElse { return emptyMap() }
        val value = rows.optJSONObject(0)?.optJSONObject("value") ?: return emptyMap()
        return buildMap {
            value.keys().forEach { key ->
                val item = value.optJSONObject(key) ?: return@forEach
                val image = item.optString("image_url").ifBlank { item.optString("image_data") }
                    .takeIf { it.isNotBlank() }
                put(key, ServiceBanner(
                    key,
                    image,
                    item.optNullable("title"),
                    item.optNullable("subtitle"),
                    item.optBoolean("active", true)
                ))
            }
        }
    }

    suspend fun placeOrder(
        auth: NativeAuth,
        requestId: String = UUID.randomUUID().toString(),
        shop: CustomerShop,
        lines: List<CartLine>,
        location: CustomerLocation,
        note: String?
    ): CustomerOrder {
        require(lines.isNotEmpty()) { "ตะกร้าว่าง" }
        val subtotal = lines.sumOf { it.product.deliveryPrice * it.quantity }
        val fee = deliveryFee(shop, location)
        val items = JSONArray()
        lines.forEach { items.put(JSONObject().put("product_id", it.product.id).put("qty", it.quantity)) }
        val body = JSONObject()
            .put("p_order_id", requestId)
            .put("p_shop_id", shop.id)
            .put("p_items", items)
            .put("p_delivery_lat", location.latitude)
            .put("p_delivery_lng", location.longitude)
            .put("p_delivery_address", location.address)
            .put("p_expected_subtotal", subtotal)
            .put("p_expected_delivery_fee", fee)
            .put("p_note", note?.trim()?.takeIf { it.isNotBlank() })
        val raw = http.rpc("queuego_place_cash_order", auth.session.accessToken, body)
        val r = when (raw) {
            is JSONObject -> raw
            is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
            else -> JSONObject()
        }
        val id = r.optString("id")
        if (id.isBlank()) error("ระบบยังไม่ยืนยันออเดอร์")
        return CustomerOrder(
            id,
            orderNumber(r.optString("order_number"), id),
            shop.id,
            r.optString("status").ifBlank { "pending" },
            r.optDouble("subtotal", subtotal),
            r.optDouble("delivery_fee", fee),
            r.optDouble("total_amount", subtotal + fee),
            location.address,
            r.optNullable("created_at")
        )
    }

    fun deliveryFee(shop: CustomerShop, location: CustomerLocation): Double {
        val lat = shop.latitude ?: error("ร้านยังไม่มีพิกัด")
        val lng = shop.longitude ?: error("ร้านยังไม่มีพิกัด")
        val km = haversine(lat, lng, location.latitude, location.longitude)
        return when {
            km <= 5.0 -> 30.0
            km <= 6.0 -> 40.0
            km <= 7.0 -> 50.0
            else -> 50.0 + kotlin.math.ceil(km - 7.0) * 10.0
        }
    }

    private fun haversine(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(bLat - aLat)
        val dLng = Math.toRadians(bLng - aLng)
        val aa = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(aLat)) * kotlin.math.cos(Math.toRadians(bLat)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        return r * 2 * kotlin.math.atan2(kotlin.math.sqrt(aa), kotlin.math.sqrt(1 - aa))
    }

    private fun orderNumber(raw: String?, id: String): String {
        val clean = raw.orEmpty().trim()
        if (clean.startsWith("QT-", true)) return clean.uppercase()
        val digits = clean.filter { it.isDigit() }.takeLast(4)
        if (digits.length == 4) return "QT-" + digits
        return "QT-" + kotlin.math.abs(id.hashCode() % 10000).toString().padStart(4, '0')
    }
}

private fun JSONObject.optNullable(key: String): String? =
    optString(key).takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
