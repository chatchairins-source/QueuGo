package com.queuego.customer

import com.queuego.shared.NativeAuth
import com.queuego.shared.QueueGoNativeApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MarketInfo(
    val id: String,
    val name: String,
    val province: String?,
    val district: String?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?
)

data class MarketShop(
    val id: String,
    val marketId: String,
    val marketName: String,
    val name: String,
    val category: String,
    val logo: String?,
    val cover: String?,
    val description: String?,
    val deliveryEnabled: Boolean
)

data class MarketProduct(
    val id: String,
    val shopId: String,
    val marketId: String,
    val marketName: String,
    val shopName: String,
    val name: String,
    val category: String,
    val description: String?,
    val image: String?,
    val price: Double,
    val unit: String?,
    val availablePacks: Int
)

data class MarketCartLine(val product: MarketProduct, val quantity: Int)

data class ActiveMarketTrip(
    val id: String,
    val marketId: String,
    val status: String,
    val locked: Boolean,
    val shopIds: Set<String>
)

class CustomerMarketApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun loadMarkets(auth: NativeAuth): List<MarketInfo> {
        val rows = http.array(http.rpc("market_public_markets_v1", auth.session.accessToken))
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val id = r.optString("market_id")
                if (id.isBlank()) continue
                add(MarketInfo(
                    id,
                    r.optString("name").ifBlank { "ตลาดสด" },
                    r.optString("province").takeIf { it.isNotBlank() && it != "null" },
                    r.optString("district").takeIf { it.isNotBlank() && it != "null" },
                    r.optString("address").takeIf { it.isNotBlank() && it != "null" },
                    r.optDoubleOrNull("latitude"),
                    r.optDoubleOrNull("longitude")
                ))
            }
        }
    }

    suspend fun loadShops(auth: NativeAuth): List<MarketShop> {
        val rows = http.array(http.rpc("market_public_shops_v2", auth.session.accessToken))
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val id = r.optString("shop_id")
                if (id.isBlank()) continue
                add(MarketShop(
                    id,
                    r.optString("market_id"),
                    r.optString("market_name"),
                    r.optString("shop_name").ifBlank { "ร้านค้า" },
                    r.optString("shop_category"),
                    r.optString("shop_logo").takeIf { it.isNotBlank() && it != "null" },
                    r.optString("shop_cover").takeIf { it.isNotBlank() && it != "null" },
                    r.optString("description").takeIf { it.isNotBlank() && it != "null" },
                    r.optBoolean("delivery_enabled", false)
                ))
            }
        }
    }

    suspend fun loadCatalog(auth: NativeAuth): List<MarketProduct> {
        val rows = http.array(http.rpc("market_public_catalog_v2", auth.session.accessToken))
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val id = r.optString("product_id")
                if (id.isBlank()) continue
                add(MarketProduct(
                    id,
                    r.optString("shop_id"),
                    r.optString("market_id"),
                    r.optString("market_name"),
                    r.optString("shop_name").ifBlank { "ร้านค้า" },
                    r.optString("name").ifBlank { "สินค้า" },
                    r.optString("category"),
                    r.optString("description").takeIf { it.isNotBlank() && it != "null" },
                    r.optString("image").takeIf { it.isNotBlank() && it != "null" },
                    r.optDouble("price", 0.0),
                    r.optString("unit").takeIf { it.isNotBlank() && it != "null" },
                    kotlin.math.floor(r.optDouble("available_packs", 0.0)).toInt().coerceAtLeast(0)
                ))
            }
        }
    }

    suspend fun activeTrip(auth: NativeAuth): ActiveMarketTrip? {
        val parents = http.array(http.get(
            "market_orders?select=id,market_id,status&customer_id=eq." + http.enc(auth.user.id) +
                "&order=created_at.desc&limit=5",
            auth.session.accessToken
        ))
        var parent: JSONObject? = null
        for (i in 0 until parents.length()) {
            val row = parents.optJSONObject(i) ?: continue
            val status = row.optString("status").uppercase()
            if (status !in setOf("COMPLETED", "CANCELLED")) {
                parent = row
                break
            }
        }
        val p = parent ?: return null
        val id = p.optString("id")
        val children = http.array(http.get(
            "orders?select=shop_id,status&market_order_id=eq." + http.enc(id),
            auth.session.accessToken
        ))
        val shops = buildSet {
            for (i in 0 until children.length()) {
                val row = children.optJSONObject(i) ?: continue
                if (row.optString("status").lowercase() != "cancelled") {
                    row.optString("shop_id").takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }
        val status = p.optString("status").uppercase()
        return ActiveMarketTrip(
            id,
            p.optString("market_id"),
            status,
            status in setOf("IN_PROGRESS", "COMPLETED", "CANCELLED"),
            shops
        )
    }

    suspend fun place(
        auth: NativeAuth,
        lines: List<MarketCartLine>,
        location: CustomerLocation,
        note: String?
    ): Any {
        require(lines.isNotEmpty()) { "ตะกร้าตลาดสดว่าง" }
        val marketId = lines.first().product.marketId
        require(lines.all { it.product.marketId == marketId }) { "ตะกร้าตลาดสดซื้อข้ามตลาดไม่ได้" }
        val active = activeTrip(auth)
        if (active?.locked == true) error("Rider ออกจากตลาดแล้ว ไม่สามารถเพิ่มร้านได้")
        if (active != null && active.marketId != marketId) error("มี Market Trip อีกตลาดที่ยังไม่จบ")

        val items = JSONArray()
        lines.forEach { line ->
            items.put(JSONObject().put("product_id", line.product.id).put("qty", line.quantity))
        }

        return if (active == null) {
            http.rpc(
                "queuego_place_market_order",
                auth.session.accessToken,
                JSONObject()
                    .put("p_market_order_id", UUID.randomUUID().toString())
                    .put("p_items", items)
                    .put("p_delivery_lat", location.latitude)
                    .put("p_delivery_lng", location.longitude)
                    .put("p_delivery_address", location.address)
                    .put("p_note", note?.trim()?.takeIf { it.isNotBlank() })
            )
        } else {
            val incomingShops = lines.map { it.product.shopId }.toSet()
            if (incomingShops.any { it in active.shopIds }) {
                error("ร้านนี้อยู่ใน Market Trip แล้ว")
            }
            http.rpc(
                "queuego_add_market_order_shops",
                auth.session.accessToken,
                JSONObject()
                    .put("p_request_id", UUID.randomUUID().toString())
                    .put("p_market_order_id", active.id)
                    .put("p_items", items)
                    .put("p_note", note?.trim()?.takeIf { it.isNotBlank() })
            )
        }
    }

    fun nearest(markets: List<MarketInfo>, location: CustomerLocation?): MarketInfo? {
        if (markets.isEmpty()) return null
        if (location == null) return markets.firstOrNull()
        return markets.filter { it.latitude != null && it.longitude != null }
            .minByOrNull { haversine(location.latitude, location.longitude, it.latitude!!, it.longitude!!) }
            ?: markets.firstOrNull()
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
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
