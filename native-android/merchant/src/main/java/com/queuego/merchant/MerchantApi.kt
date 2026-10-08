package com.queuego.merchant

import com.queuego.shared.NativeAuth
import com.queuego.shared.QueueGoNativeApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MerchantShop(
    val id: String,
    val name: String,
    val category: String,
    val logo: String?,
    val cover: String?,
    val address: String?,
    val status: String,
    val onboardingStatus: String,
    val deliveryEnabled: Boolean
)

data class MerchantTodayRevenue(
    val orderCount: Int,
    val grossSales: Double,
    val gpDue: Double,
    val cashReceived: Double
)

data class MerchantOrder(
    val id: String,
    val number: String,
    val status: String,
    val subtotal: Double,
    val deliveryFee: Double,
    val total: Double,
    val note: String?,
    val createdAt: String?,
    val preparingAt: String?,
    val readyAt: String?
)

data class MerchantOrderItem(
    val name: String,
    val description: String?,
    val quantity: Int,
    val totalPrice: Double,
    val image: String?
)

data class MerchantProduct(
    val id: String,
    val name: String,
    val description: String?,
    val price: Double,
    val deliveryPrice: Double,
    val image: String?,
    val available: Boolean
)

data class MerchantSupportMessage(
    val id: String,
    val senderUserId: String,
    val body: String,
    val createdAt: String?
)

data class ShopReadiness(val complete: Boolean, val checks: JSONObject, val catalogKind: String?)

class MerchantApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun loadShop(auth: NativeAuth): MerchantShop? {
        val rows = http.array(http.get(
            "shop_profiles?select=id,shop_name,public_category,public_logo,public_cover,address,status,onboarding_status,delivery_enabled" +
                "&user_id=eq." + http.enc(auth.user.id) + "&archived_at=is.null&order=created_at.desc&limit=1",
            auth.session.accessToken
        ))
        val r = rows.optJSONObject(0) ?: return null
        return MerchantShop(
            r.optString("id"),
            r.optString("shop_name").ifBlank { "ร้านค้า" },
            r.optString("public_category").ifBlank { "other" },
            r.optNullable("public_logo"),
            r.optNullable("public_cover"),
            r.optNullable("address"),
            r.optString("status"),
            r.optString("onboarding_status").ifBlank { "draft" },
            r.optBoolean("delivery_enabled", false)
        )
    }

    suspend fun shopOpenState(auth: NativeAuth, shopId: String): Boolean {
        val rows = http.array(http.get(
            "shop_open_states?select=shop_id,is_open&shop_id=eq." + http.enc(shopId) + "&limit=1",
            auth.session.accessToken
        ))
        return rows.optJSONObject(0)?.optBoolean("is_open", true) ?: true
    }

    suspend fun setShopOpen(auth: NativeAuth, shop: MerchantShop, open: Boolean) {
        if (open && !shop.deliveryEnabled) {
            http.rpc("pos_enable_delivery", auth.session.accessToken, JSONObject())
            return
        }
        val path = "shop_open_states?shop_id=eq." + http.enc(shop.id)
        val rows = http.array(http.get(
            "shop_open_states?select=shop_id&shop_id=eq." + http.enc(shop.id) + "&limit=1",
            auth.session.accessToken
        ))
        val body = JSONObject()
            .put("shop_id", shop.id)
            .put("is_open", open)
            .put("resume_at", JSONObject.NULL)
        if (rows.length() > 0) http.patch(path, auth.session.accessToken, body)
        else http.post("shop_open_states", auth.session.accessToken, body)
    }

    suspend fun todayRevenue(auth: NativeAuth, date: String): MerchantTodayRevenue {
        val raw = http.rpc(
            "merchant_revenue_days",
            auth.session.accessToken,
            JSONObject().put("p_from", date).put("p_to", date)
        )
        val rows = http.array(raw)
        val r = rows.optJSONObject(0) ?: return MerchantTodayRevenue(0, 0.0, 0.0, 0.0)
        return MerchantTodayRevenue(
            r.optInt("order_count", 0),
            r.optDouble("gross_sales", 0.0),
            r.optDouble("gp_due", 0.0),
            r.optDouble("cash_received", 0.0)
        )
    }

    suspend fun loadOrders(auth: NativeAuth): List<MerchantOrder> {
        val raw = http.rpc("get_my_shop_orders", auth.session.accessToken, JSONObject())
        val rows = http.array(raw)
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val id = r.optString("id").ifBlank { r.optString("order_id") }
                if (id.isBlank()) continue
                add(
                    MerchantOrder(
                        id,
                        orderNumber(r.optString("order_number"), id),
                        r.optString("status"),
                        r.optDouble("subtotal", r.optDouble("items_total", 0.0)),
                        r.optDouble("delivery_fee", 0.0),
                        r.optDouble("total_amount", r.optDouble("subtotal", 0.0) + r.optDouble("delivery_fee", 0.0)),
                        r.optNullable("note"),
                        r.optNullable("created_at"),
                        r.optNullable("preparing_at"),
                        r.optNullable("ready_at")
                    )
                )
            }
        }
    }

    suspend fun loadOrderItems(auth: NativeAuth, orderId: String): List<MerchantOrderItem> {
        val rows = http.array(http.get(
            "order_items?select=item_name,description,quantity,total_price,item_image" +
                "&order_id=eq." + http.enc(orderId) + "&order=created_at.asc",
            auth.session.accessToken
        ))
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                add(MerchantOrderItem(
                    r.optString("item_name").ifBlank { "สินค้า" },
                    r.optNullable("description"),
                    r.optInt("quantity", 1),
                    r.optDouble("total_price", 0.0),
                    r.optNullable("item_image")
                ))
            }
        }
    }

    suspend fun action(auth: NativeAuth, orderId: String, action: String, reason: String? = null): MerchantOrder {
        val payload = JSONObject()
            .put("p_order_id", orderId)
            .put("p_action", action)
            .put("p_reason", reason?.trim()?.takeIf { it.isNotBlank() })
        val raw = http.rpc(
            "qg_merchant_action_once",
            auth.session.accessToken,
            JSONObject()
                .put("p_request_id", UUID.nameUUIDFromBytes(("merchant:" + auth.user.id + ":" + orderId + ":" + action).toByteArray()).toString())
                .put("p_kind", "order")
                .put("p_payload", payload)
        )
        val r = when (raw) {
            is JSONObject -> raw
            is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
            else -> JSONObject()
        }
        val id = r.optString("order_id").ifBlank { orderId }
        val status = r.optString("status")
        if (status.isBlank()) error("Server ยังไม่ยืนยันสถานะออเดอร์")
        return MerchantOrder(
            id, "", status, 0.0, 0.0, 0.0, null, null,
            r.optNullable("preparing_at"), r.optNullable("ready_at")
        )
    }

    suspend fun loadProducts(auth: NativeAuth, shopId: String): List<MerchantProduct> {
        val rows = http.array(http.get(
            "products?select=id,name,description,price,delivery_price,image,available&shop_id=eq." +
                http.enc(shopId) + "&order=created_at.desc",
            auth.session.accessToken
        ))
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                add(MerchantProduct(
                    r.optString("id"),
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

    suspend fun effectiveGp(auth: NativeAuth, shopId: String): Double {
        val raw = http.rpc(
            "effective_gp_rate",
            auth.session.accessToken,
            JSONObject().put("p_shop_profile_id", shopId)
        )
        return when (raw) {
            is Number -> raw.toDouble()
            is JSONArray -> {
                val first = raw.opt(0)
                when (first) {
                    is Number -> first.toDouble()
                    is JSONObject -> first.optDouble("effective_gp_rate", 0.0)
                    else -> first?.toString()?.toDoubleOrNull() ?: 0.0
                }
            }
            else -> raw.toString().toDoubleOrNull() ?: 0.0
        }
    }

    suspend fun setProductAvailable(auth: NativeAuth, productId: String, available: Boolean) {
        val rows = http.patch(
            "products?id=eq." + http.enc(productId),
            auth.session.accessToken,
            JSONObject().put("available", available)
        )
        if (rows is JSONArray && rows.length() == 0) error("ฐานข้อมูลยังไม่ยืนยันสินค้า")
    }

    suspend fun createProduct(
        auth: NativeAuth,
        shopId: String,
        name: String,
        description: String?,
        price: Double,
        gpRate: Double
    ) {
        val deliveryPrice = if (gpRate > 0) price / (1.0 - gpRate / 100.0) else price
        val body = JSONObject()
            .put("shop_id", shopId)
            .put("name", name.trim())
            .put("description", description?.trim()?.takeIf { it.isNotBlank() })
            .put("price", price)
            .put("delivery_price", kotlin.math.ceil(deliveryPrice))
            .put("available", true)
            .put("delivery_available", true)
        val raw = http.post("products", auth.session.accessToken, body)
        if (raw is JSONArray && raw.length() == 0) error("ฐานข้อมูลยังไม่ยืนยันสินค้า")
    }

    suspend fun loadSupportMessages(auth: NativeAuth): List<MerchantSupportMessage> {
        val rows = http.array(
            http.get(
                "shop_support_messages?select=id,sender_user_id,body,created_at" +
                    "&shop_user_id=eq." + http.enc(auth.user.id) +
                    "&order=created_at.asc",
                auth.session.accessToken
            )
        )
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                add(
                    MerchantSupportMessage(
                        id = r.optString("id"),
                        senderUserId = r.optString("sender_user_id"),
                        body = r.optString("body"),
                        createdAt = r.optNullable("created_at")
                    )
                )
            }
        }
    }

    suspend fun sendSupportMessage(auth: NativeAuth, body: String) {
        val text = body.trim()
        require(text.isNotBlank()) { "กรุณาพิมพ์ข้อความ" }
        val raw = http.post(
            "shop_support_messages",
            auth.session.accessToken,
            JSONObject()
                .put("shop_user_id", auth.user.id)
                .put("sender_user_id", auth.user.id)
                .put("body", text)
        )
        if (raw is JSONArray && raw.length() == 0) error("ระบบยังไม่ยืนยันข้อความ")
    }

    suspend fun readiness(auth: NativeAuth, shopId: String?): ShopReadiness? {
        val raw = runCatching {
            http.rpc(
                "queuego_shop_readiness",
                auth.session.accessToken,
                JSONObject().put("p_shop_id", shopId)
            )
        }.getOrNull() ?: return null
        val r = when (raw) {
            is JSONObject -> raw
            is JSONArray -> raw.optJSONObject(0) ?: return null
            else -> return null
        }
        return ShopReadiness(
            r.optBoolean("complete", false),
            r.optJSONObject("checks") ?: JSONObject(),
            r.optNullable("catalog_kind")
        )
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
