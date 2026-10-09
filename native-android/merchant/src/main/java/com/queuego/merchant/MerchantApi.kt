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
    val deliveryEnabled: Boolean,
    val phone: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val openTime: String? = null,
    val closeTime: String? = null,
    val description: String? = null,
    val shoppingSubcategories: Set<String> = emptySet()
)

data class MerchantShopSetupDraft(
    val shopName: String,
    val contactName: String,
    val phone: String,
    val category: String,
    val shoppingSubcategories: Set<String>,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val openTime: String,
    val closeTime: String,
    val description: String,
    val logo: String?,
    val cover: String?
)

data class MerchantTodayRevenue(
    val orderCount: Int,
    val grossSales: Double,
    val gpDue: Double,
    val cashReceived: Double
)

data class MerchantRevenueDay(
    val dateKey: String,
    val orderCount: Int,
    val grossSales: Double,
    val gpDue: Double,
    val cashReceived: Double,
    val gpStatus: String
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

data class MerchantBusinessDay(
    val weekday: Int,
    val opensAt: String,
    val closesAt: String,
    val isClosed: Boolean
)

data class MerchantSpecialHours(
    val day: String?,
    val opensAt: String,
    val closesAt: String,
    val isClosed: Boolean
)

data class MerchantHoursState(
    val days: List<MerchantBusinessDay>,
    val special: MerchantSpecialHours?
)

data class ShopReadiness(val complete: Boolean, val checks: JSONObject, val catalogKind: String?)

class MerchantApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun loadShop(auth: NativeAuth): MerchantShop? {
        val rows = http.array(http.get(
            "shop_profiles?select=id,shop_name,public_category,public_subcategories,public_logo,public_cover,public_description,phone,address,latitude,longitude,public_open_time,public_close_time,status,onboarding_status,delivery_enabled" +
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
            r.optBoolean("delivery_enabled", false),
            r.optNullable("phone"),
            r.optDoubleOrNull("latitude"),
            r.optDoubleOrNull("longitude"),
            r.optNullable("public_open_time"),
            r.optNullable("public_close_time"),
            r.optNullable("public_description"),
            parseMerchantSubcategories(r.opt("public_subcategories"))
        )
    }

    suspend fun saveShopSetup(
        auth: NativeAuth,
        shop: MerchantShop,
        draft: MerchantShopSetupDraft
    ): MerchantShop {
        val shopName = draft.shopName.trim()
        val contactName = draft.contactName.trim()
        val phone = draft.phone.trim()
        val address = draft.address.trim()
        val category = draft.category.trim().lowercase()
        require(shopName.isNotBlank()) { "กรุณากรอกชื่อร้าน" }
        require(contactName.isNotBlank()) { "กรุณากรอกชื่อผู้ติดต่อ" }
        require(category in setOf("food", "grocery", "cafe", "laundry", "market", "shopping", "other")) {
            "หมวดหมู่ร้านไม่ถูกต้อง"
        }
        if (category == "shopping") {
            require(draft.shoppingSubcategories.isNotEmpty()) {
                "กรุณาเลือกหมวดย่อยของร้านช้อปปิ้งอย่างน้อย 1 หมวด"
            }
        }
        val lat = draft.latitude
        val lng = draft.longitude
        if (lat != null || lng != null) {
            require(lat != null && lng != null && merchantCoordinateValid(lat, lng)) {
                "พิกัดร้านไม่ถูกต้อง กรุณาปักตำแหน่งร้านใหม่"
            }
        }

        val existingShopMetadata = runCatching {
            http.array(
                http.get(
                    "shop_profiles?select=metadata&id=eq." + http.enc(shop.id) + "&limit=1",
                    auth.session.accessToken
                )
            ).optJSONObject(0)?.optJSONObject("metadata")
        }.getOrNull() ?: JSONObject()
        val existingUserMetadata = runCatching {
            http.array(
                http.get(
                    "users?select=metadata&id=eq." + http.enc(auth.user.id) + "&limit=1",
                    auth.session.accessToken
                )
            ).optJSONObject(0)?.optJSONObject("metadata")
        }.getOrNull() ?: JSONObject()

        fun mergeMetadata(base: JSONObject): JSONObject =
            JSONObject(base.toString())
                .put("shopName", shopName)
                .put("name", contactName)
                .put("phone", phone)
                .put("category", category)
                .put("shoppingSubcategories", JSONArray(draft.shoppingSubcategories.toList()))
                .put("address", address)
                .put("openTime", draft.openTime.trim())
                .put("closeTime", draft.closeTime.trim())
                .put("description", draft.description.trim())
                .put("logo", draft.logo ?: shop.logo ?: JSONObject.NULL)
                .put("cover", draft.cover ?: shop.cover ?: JSONObject.NULL)
                .also { metadata ->
                    if (lat != null && lng != null) metadata.put("lat", lat).put("lng", lng)
                }

        val shopMetadata = mergeMetadata(existingShopMetadata)
        val userMetadata = mergeMetadata(existingUserMetadata)
        val shopBody = JSONObject()
            .put("shop_name", shopName)
            .put("phone", phone)
            .put("address", address)
            .put("metadata", shopMetadata)
        if (lat != null && lng != null) {
            shopBody.put("latitude", lat).put("longitude", lng)
        }

        val saved = http.patch(
            "shop_profiles?id=eq." + http.enc(shop.id),
            auth.session.accessToken,
            shopBody
        )
        if (saved is JSONArray && saved.length() == 0) error("ฐานข้อมูลไม่ยืนยันข้อมูลร้าน")

        val userSaved = http.patch(
            "users?id=eq." + http.enc(auth.user.id),
            auth.session.accessToken,
            JSONObject()
                .put("name", contactName)
                .put("phone", phone)
                .put("metadata", userMetadata)
        )
        if (userSaved is JSONArray && userSaved.length() == 0) error("ฐานข้อมูลไม่ยืนยันข้อมูลผู้ใช้")

        return loadShop(auth) ?: error("ไม่พบข้อมูลร้านหลังบันทึก")
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

    suspend fun loadBusinessHours(
        auth: NativeAuth,
        shopId: String,
        fromDay: String
    ): MerchantHoursState {
        val dayRows = http.array(
            http.get(
                "shop_business_hours?select=weekday,opens_at,closes_at,is_closed" +
                    "&shop_id=eq." + http.enc(shopId) + "&order=weekday.asc",
                auth.session.accessToken
            )
        )
        val byDay = HashMap<Int, MerchantBusinessDay>()
        for (i in 0 until dayRows.length()) {
            val r = dayRows.optJSONObject(i) ?: continue
            val weekday = r.optInt("weekday", -1)
            if (weekday !in 0..6) continue
            byDay[weekday] = MerchantBusinessDay(
                weekday = weekday,
                opensAt = normalizeMerchantTime(r.optString("opens_at"), "06:00"),
                closesAt = normalizeMerchantTime(r.optString("closes_at"), "22:00"),
                isClosed = r.optBoolean("is_closed", false)
            )
        }
        val days = (0..6).map { weekday ->
            byDay[weekday] ?: MerchantBusinessDay(weekday, "06:00", "22:00", false)
        }

        val specialRows = runCatching {
            http.array(
                http.get(
                    "shop_special_hours?select=day,is_closed,opens_at,closes_at" +
                        "&shop_id=eq." + http.enc(shopId) +
                        "&day=gte." + http.enc(fromDay) +
                        "&order=day.asc&limit=1",
                    auth.session.accessToken
                )
            )
        }.getOrElse { JSONArray() }
        val special = specialRows.optJSONObject(0)?.let { r ->
            MerchantSpecialHours(
                day = r.optNullable("day"),
                opensAt = normalizeMerchantTime(r.optString("opens_at"), "06:00"),
                closesAt = normalizeMerchantTime(r.optString("closes_at"), "22:00"),
                isClosed = r.optBoolean("is_closed", true)
            )
        }
        return MerchantHoursState(days, special)
    }

    suspend fun saveBusinessHours(
        auth: NativeAuth,
        days: List<MerchantBusinessDay>,
        special: MerchantSpecialHours?
    ) {
        require(days.map { it.weekday }.toSet() == (0..6).toSet()) {
            "กรุณาตั้งค่าเวลาทำการให้ครบ 7 วัน"
        }
        val rows = JSONArray()
        days.sortedBy { it.weekday }.forEach { day ->
            require(day.weekday in 0..6) { "วันทำการไม่ถูกต้อง" }
            require(merchantTimeValid(day.opensAt) && merchantTimeValid(day.closesAt)) {
                "เวลาทำการไม่ถูกต้อง"
            }
            rows.put(
                JSONObject()
                    .put("weekday", day.weekday)
                    .put("opens_at", day.opensAt)
                    .put("closes_at", day.closesAt)
                    .put("is_closed", day.isClosed)
            )
        }
        val specialDay = special?.day?.trim()?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        if (special != null) {
            require(merchantTimeValid(special.opensAt) && merchantTimeValid(special.closesAt)) {
                "เวลาพิเศษไม่ถูกต้อง"
            }
        }
        http.rpc(
            "merchant_save_hours",
            auth.session.accessToken,
            JSONObject()
                .put("p_days", rows)
                .put("p_special_day", specialDay ?: JSONObject.NULL)
                .put("p_special_closed", special?.isClosed ?: true)
                .put("p_special_open", special?.opensAt ?: "06:00")
                .put("p_special_close", special?.closesAt ?: "22:00")
        )
    }

    suspend fun revenueDays(auth: NativeAuth, from: String, to: String): List<MerchantRevenueDay> {
        require(from.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "วันที่เริ่มต้นไม่ถูกต้อง" }
        require(to.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "วันที่สิ้นสุดไม่ถูกต้อง" }
        val raw = http.rpc(
            "merchant_revenue_days",
            auth.session.accessToken,
            JSONObject().put("p_from", from).put("p_to", to)
        )
        val rows = http.array(raw)
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                add(
                    MerchantRevenueDay(
                        dateKey = r.optString("date_key"),
                        orderCount = r.optInt("order_count", 0),
                        grossSales = r.optDouble("gross_sales", 0.0),
                        gpDue = r.optDouble("gp_due", 0.0),
                        cashReceived = r.optDouble("cash_received", 0.0),
                        gpStatus = r.optString("gp_status").ifBlank { "pending" }
                    )
                )
            }
        }
    }

    suspend fun reportGpSlip(auth: NativeAuth, date: String, slipPath: String) {
        require(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "วันที่รายงานไม่ถูกต้อง" }
        require(slipPath.isNotBlank()) { "ไม่พบไฟล์สลิป" }
        http.rpc(
            "report_shop_gp",
            auth.session.accessToken,
            JSONObject().put("p_date", date).put("p_slip_path", slipPath)
        )
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

    suspend fun updateProduct(
        auth: NativeAuth,
        shopId: String,
        product: MerchantProduct,
        name: String,
        description: String?,
        price: Double,
        gpRate: Double
    ) {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "กรุณากรอกชื่อสินค้า" }
        require(price >= 0.0 && price.isFinite()) { "ราคาสินค้าไม่ถูกต้อง" }
        val deliveryPrice = if (gpRate > 0 && gpRate < 100) {
            price / (1.0 - gpRate / 100.0)
        } else price
        val raw = http.patch(
            "products?id=eq." + http.enc(product.id) +
                "&shop_id=eq." + http.enc(shopId),
            auth.session.accessToken,
            JSONObject()
                .put("name", cleanName)
                .put("description", description?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("price", price)
                .put("delivery_price", kotlin.math.ceil(deliveryPrice))
        )
        if (raw is JSONArray && raw.length() == 0) error("ฐานข้อมูลยังไม่ยืนยันการแก้ไขสินค้า")
    }

    suspend fun deleteOrArchiveProduct(auth: NativeAuth, productId: String) {
        val raw = http.rpc(
            "queuego_delete_or_archive_product",
            auth.session.accessToken,
            JSONObject().put("p_product_id", productId)
        )
        val result = when (raw) {
            is String -> raw
            is JSONArray -> raw.optString(0)
            is JSONObject -> raw.optString("queuego_delete_or_archive_product")
            else -> raw.toString()
        }
        if (result.isBlank()) error("Server ยังไม่ยืนยันการนำสินค้าออก")
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


internal fun merchantCoordinateValid(latitude: Double, longitude: Double): Boolean =
    latitude in 5.0..21.0 && longitude in 97.0..106.0 && !(latitude == 0.0 && longitude == 0.0)

internal fun parseMerchantSubcategories(raw: Any?): Set<String> {
    val values = when (raw) {
        is JSONArray -> (0 until raw.length()).map { raw.optString(it) }
        is String -> runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.optString(it) }
        }.getOrElse {
            raw.removePrefix("[").removeSuffix("]")
                .split(',')
                .map { it.trim().trim('"', '\'') }
        }
        else -> emptyList()
    }
    return values.mapNotNull { it.trim().lowercase().takeIf(String::isNotBlank) }.toSet()
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }


internal fun normalizeMerchantTime(value: String?, fallback: String): String {
    val clean = value.orEmpty().trim().take(5)
    return if (merchantTimeValid(clean)) clean else fallback
}

internal fun merchantTimeValid(value: String): Boolean {
    val match = Regex("^(\\d{2}):(\\d{2})$").matchEntire(value.trim()) ?: return false
    val hour = match.groupValues[1].toIntOrNull() ?: return false
    val minute = match.groupValues[2].toIntOrNull() ?: return false
    return hour in 0..23 && minute in 0..59
}
