package com.queuego.customer

import com.queuego.shared.NativeAuth
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
    val open: Boolean,
    val openTime: String? = null,
    val closeTime: String? = null,
    val subcategories: Set<String> = emptySet(),
    val description: String? = null
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
    val deliveryLongitude: Double? = null,
    val note: String? = null,
    val riderArrivedCustomerAt: String? = null,
    val bundleCustomerSavings: Double = 0.0
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
data class HomeBanner(
    val image: String,
    val title: String?,
    val subtitle: String?,
    val alt: String? = null,
    val link: String? = null,
    val rotationMs: Long = 5_000L
)
data class ServiceBanner(
    val key: String,
    val image: String?,
    val title: String?,
    val subtitle: String?,
    val active: Boolean,
    val link: String? = null
)
data class CartLine(val product: CustomerProduct, val quantity: Int)

class CustomerApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun loadShops(auth: NativeAuth): List<CustomerShop> =
        loadShopsWithToken(auth.session.accessToken)

    suspend fun loadShopsPublic(): List<CustomerShop> = loadShopsWithToken(null)

    private suspend fun loadShopsWithToken(token: String?): List<CustomerShop> {
        val rows = http.array(http.get(
            "shop_profiles?select=id,shop_name,public_category,public_subcategories,public_logo,public_cover,public_description,address,latitude,longitude,status,public_open_time,public_close_time&status=eq.active",
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
                    open[id] != false,
                    row.optNullable("public_open_time"),
                    row.optNullable("public_close_time"),
                    parseShopSubcategories(row.opt("public_subcategories")),
                    row.optNullable("public_description")
                ))
            }
        }
    }

    suspend fun loadProducts(auth: NativeAuth, shopId: String): List<CustomerProduct> =
        loadProductsWithToken(auth.session.accessToken, shopId)

    suspend fun loadProductsPublic(shopId: String): List<CustomerProduct> =
        loadProductsWithToken(null, shopId)

    private suspend fun loadProductsWithToken(token: String?, shopId: String): List<CustomerProduct> {
        val path = "products?select=id,shop_id,name,description,price,delivery_price,image,available,delivery_available" +
            "&shop_id=eq." + http.enc(shopId) + "&delivery_available=eq.true&order=name.asc"
        val rows = http.array(http.get(path, token))
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                add(CustomerProduct(
                    r.optString("id"),
                    r.optString("shop_id"),
                    r.optString("name").ifBlank { "สินค้า" },
                    r.optNullable("description"),
                    r.optDouble("price", 0.0),
                    r.optDouble("delivery_price", r.optDouble("price", 0.0)),
                    r.optNullable("image"),
                    r.optBoolean("available", true)
                ))
            }
        }
    }

    suspend fun loadOrders(auth: NativeAuth): List<CustomerOrder> {
        val rows = http.array(http.get(
            "orders?select=id,order_number,shop_id,status,subtotal,delivery_fee,total_amount,delivery_address,created_at,rider_id,completed_at,updated_at,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude,note,rider_arrived_customer_at,bundle_customer_savings" +
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
                    r.optDoubleOrNull("delivery_longitude"),
                    r.optNullable("note"),
                    r.optNullable("rider_arrived_customer_at"),
                    r.optDouble("bundle_customer_savings", 0.0)
                ))
            }
        }
    }

    suspend fun loadActiveOrder(auth: NativeAuth): CustomerOrder? {
        val rows = http.array(http.get(
            "orders?select=id,order_number,shop_id,status,subtotal,delivery_fee,total_amount,delivery_address,created_at,rider_id,completed_at,updated_at,pickup_latitude,pickup_longitude,delivery_latitude,delivery_longitude,note,rider_arrived_customer_at,bundle_customer_savings" +
                "&customer_id=eq." + http.enc(auth.user.id) +
                "&status=in.(pending,accepted,searching_rider,rider_assigned,preparing,ready,assigned,picked_up,in_progress)" +
                "&order=created_at.desc&limit=1",
            auth.session.accessToken
        ))
        val r = rows.optJSONObject(0) ?: return null
        return CustomerOrder(
            r.optString("id"),
            orderNumber(r.optString("order_number"), r.optString("id")),
            r.optNullable("shop_id"),
            r.optString("status"),
            r.optDouble("subtotal", 0.0),
            r.optDouble("delivery_fee", 0.0),
            r.optDouble("total_amount", 0.0),
            r.optNullable("delivery_address"),
            r.optNullable("created_at"),
            r.optNullable("rider_id"),
            r.optNullable("completed_at"),
            r.optNullable("updated_at"),
            r.optDoubleOrNull("pickup_latitude"),
            r.optDoubleOrNull("pickup_longitude"),
            r.optDoubleOrNull("delivery_latitude"),
            r.optDoubleOrNull("delivery_longitude"),
            r.optNullable("note"),
            r.optNullable("rider_arrived_customer_at"),
            r.optDouble("bundle_customer_savings", 0.0)
        )
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

    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
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
                formatLongdoAddress(reader.readText())
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    suspend fun loadHomeBanners(auth: NativeAuth): List<HomeBanner> =
        loadHomeBannersWithToken(auth.session.accessToken)

    suspend fun loadHomeBannersPublic(): List<HomeBanner> = loadHomeBannersWithToken(null)

    private suspend fun loadHomeBannersWithToken(token: String?): List<HomeBanner> {
        val rows = runCatching {
            http.array(http.get("system_settings?select=value&key=eq.home_service_banner&limit=1", token))
        }.getOrElse { return emptyList() }
        val value = rows.optJSONObject(0)?.optJSONObject("value") ?: return emptyList()
        val slides = value.optJSONArray("slides") ?: return emptyList()
        val rotationMs = value.optLong("rotation_ms", 5_000L).coerceIn(3_000L, 15_000L)
        return buildList {
            for (i in 0 until slides.length().coerceAtMost(3)) {
                val slide = slides.optJSONObject(i) ?: continue
                if (!slide.optBoolean("active", true)) continue
                val image = slide.optString("image_url").ifBlank { slide.optString("image_data") }.trim()
                if (!(image.startsWith("data:image/") || image.startsWith("http://", true) || image.startsWith("https://", true))) continue
                val linkValue = slide.optString("link").ifBlank { slide.optString("link_target") }.trim()
                val link = linkValue.takeIf {
                    it.startsWith("#") || it.startsWith("http://", true) || it.startsWith("https://", true)
                }
                add(
                    HomeBanner(
                        image = image,
                        title = slide.optNullable("title"),
                        subtitle = slide.optNullable("subtitle"),
                        alt = slide.optNullable("alt") ?: "QueueGo",
                        link = link,
                        rotationMs = rotationMs
                    )
                )
            }
        }
    }

    suspend fun loadServiceBanners(auth: NativeAuth): Map<String, ServiceBanner> =
        loadServiceBannersWithToken(auth.session.accessToken)

    suspend fun loadServiceBannersPublic(): Map<String, ServiceBanner> =
        loadServiceBannersWithToken(null)

    private suspend fun loadServiceBannersWithToken(token: String?): Map<String, ServiceBanner> {
        val rows = runCatching {
            http.array(http.get("system_settings?select=value&key=eq.service_banners&limit=1", token))
        }.getOrElse { return emptyMap() }
        val value = rows.optJSONObject(0)?.optJSONObject("value") ?: return emptyMap()
        return buildMap {
            value.keys().forEach { key ->
                val item = value.optJSONObject(key) ?: return@forEach
                val image = item.optString("image_url").ifBlank { item.optString("image_data") }
                    .takeIf { it.isNotBlank() }
                val rawLink = item.optString("link").ifBlank { item.optString("link_target") }.trim()
                val link = rawLink.takeIf {
                    it.startsWith("#") || it.startsWith("http://", true) || it.startsWith("https://", true)
                }
                put(key, ServiceBanner(
                    key,
                    image,
                    item.optNullable("title"),
                    item.optNullable("subtitle"),
                    item.optBoolean("active", true),
                    link
                ))
            }
        }
    }

    fun checkoutBody(
        auth: NativeAuth,
        requestId: String = UUID.randomUUID().toString(),
        shop: CustomerShop,
        lines: List<CartLine>,
        location: CustomerLocation,
        note: String?
    ): JSONObject {
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
        return body
    }

    suspend fun placeOrder(auth: NativeAuth, body: JSONObject): CustomerOrder {
        val raw = http.rpc("queuego_place_cash_order", auth.session.accessToken, body)
        val r = when (raw) {
            is JSONObject -> raw
            is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
            else -> JSONObject()
        }
        val id = r.optString("id")
        if (id != body.getString("p_order_id") || r.optString("order_number").isBlank()) error("ยังไม่ได้รับเลขยืนยันออเดอร์เดิม")
        return CustomerOrder(
            id,
            orderNumber(r.optString("order_number"), id),
            body.getString("p_shop_id"),
            r.optString("status").ifBlank { "pending" },
            r.optDouble("subtotal", body.getDouble("p_expected_subtotal")),
            r.optDouble("delivery_fee", body.getDouble("p_expected_delivery_fee")),
            r.optDouble("total_amount", body.getDouble("p_expected_subtotal") + body.getDouble("p_expected_delivery_fee")),
            body.getString("p_delivery_address"),
            r.optNullable("created_at")
        )
    }

    suspend fun findPlacedOrder(auth: NativeAuth, requestId: String): CustomerOrder? {
        val rows = http.array(http.get("orders?select=id,order_number,shop_id,status,subtotal,delivery_fee,total_amount,delivery_address,created_at&customer_id=eq." + http.enc(auth.user.id) + "&id=eq." + http.enc(requestId) + "&limit=1", auth.session.accessToken))
        val row = rows.optJSONObject(0) ?: return null
        if (row.optString("id") != requestId || row.optString("order_number").isBlank()) return null
        return CustomerOrder(requestId, orderNumber(row.getString("order_number"), requestId), row.optNullable("shop_id"), row.optString("status"), row.optDouble("subtotal"), row.optDouble("delivery_fee"), row.optDouble("total_amount"), row.optNullable("delivery_address"), row.optNullable("created_at"))
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


private val longdoAddressKeys =
    listOf("house_number", "soi", "road", "subdistrict", "district", "province", "postcode")

internal fun formatLongdoAddressParts(parts: Map<String, String?>): String? =
    longdoAddressKeys
        .mapNotNull { key -> parts[key]?.trim()?.takeIf { it.isNotBlank() && it != "null" } }
        .joinToString(" ")
        .takeIf { it.isNotBlank() }

internal fun formatLongdoAddress(raw: String): String? = runCatching {
    val address = JSONObject(raw)
    formatLongdoAddressParts(longdoAddressKeys.associateWith { key -> address.optString(key) })
}.getOrNull()


internal fun normalizeShopSubcategories(values: Iterable<String>): Set<String> =
    values.mapNotNull { value -> value.trim().lowercase().takeIf { it.isNotBlank() } }.toSet()

internal fun parseShopSubcategories(raw: Any?): Set<String> {
    val values = when (raw) {
        is JSONArray -> (0 until raw.length()).map { index -> raw.optString(index) }
        is String -> runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index -> array.optString(index) }
        }.getOrElse {
            raw.removePrefix("[").removeSuffix("]")
                .split(',')
                .map { it.trim().trim('"', '\'') }
        }
        else -> emptyList()
    }
    return normalizeShopSubcategories(values)
}
