package com.queuego.merchant

import com.queuego.shared.sendNativeChatOnce
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
    val readyAt: String?,
    val itemCount: Int = 0
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
    val available: Boolean,
    val category: String = "",
    val stock: Int = 0,
    val variantsJson: String = "[]",
    val posAvailable: Boolean = true,
    val deliveryAvailable: Boolean = true,
    val posPrice: Double? = null,
    val deliveryRestriction: String = "none"
)

data class MerchantProductDraft(
    val requestId: String,
    val name: String,
    val description: String?,
    val category: String,
    val price: Double,
    val stock: Int,
    val image: String?,
    val available: Boolean,
    val posAvailable: Boolean,
    val deliveryAvailable: Boolean,
    val variantsJson: String
)

data class MerchantMarketProduct(
    val productId: String,
    val name: String,
    val category: String,
    val price: Double,
    val deliveryPrice: Double,
    val image: String?,
    val available: Boolean,
    val unit: String,
    val packSize: Double,
    val weightKg: Double?,
    val stockQuantity: Double,
    val minStock: Double,
    val costPrice: Double
)

data class MerchantStockMovement(
    val type: String,
    val quantity: Double,
    val note: String?,
    val createdAt: String?
)

data class MerchantMarketStockState(
    val products: List<MerchantMarketProduct>,
    val movements: List<MerchantStockMovement>
)

data class MerchantMarketProductDraft(
    val productId: String?,
    val name: String,
    val category: String,
    val price: Double,
    val image: String?,
    val unit: String,
    val packSize: Double,
    val weightKg: Double,
    val costPrice: Double,
    val initialStock: Double,
    val minStock: Double,
    val available: Boolean
)

data class MerchantSupportMessage(
    val id: String,
    val senderUserId: String,
    val body: String,
    val createdAt: String?,
    val slipPath: String? = null
)

data class MerchantNotification(
    val id: String,
    val title: String,
    val message: String,
    val type: String,
    val referenceId: String?,
    val isRead: Boolean,
    val createdAt: String?
)

data class MerchantModule(
    val id: String?,
    val key: String,
    val status: String,
    val startedAt: String?,
    val expiresAt: String?
)

data class MerchantPromotion(
    val id: String,
    val title: String,
    val status: String,
    val maxBudget: Double,
    val periodDays: Int,
    val paymentStatus: String,
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
        val rows = http.array(
            http.get(
                "shop_open_states?select=shop_id,is_open,resume_at&shop_id=eq." +
                    http.enc(shopId) + "&limit=1",
                auth.session.accessToken
            )
        )
        val row = rows.optJSONObject(0) ?: return true
        if (row.optBoolean("is_open", true)) return true
        val resumeAt = row.optNullable("resume_at") ?: return false
        val resume = runCatching { java.time.Instant.parse(resumeAt) }.getOrNull() ?: return false
        return !resume.isAfter(java.time.Instant.now())
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

    suspend fun pauseShop(auth: NativeAuth, shopId: String, minutes: Int) {
        require(minutes in setOf(30, 60, 1440)) { "ระยะเวลาพักร้านไม่ถูกต้อง" }
        val resumeAt = java.time.Instant.now().plusSeconds(minutes.toLong() * 60L).toString()
        val body = JSONObject()
            .put("shop_id", shopId)
            .put("is_open", false)
            .put("resume_at", resumeAt)
            .put("updated_at", java.time.Instant.now().toString())
        http.upsert(
            "shop_open_states?on_conflict=shop_id",
            auth.session.accessToken,
            body
        )
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
                        r.optNullable("ready_at"),
                        r.optJSONArray("items")?.length() ?: 0
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
        val rows = http.array(
            http.get(
                "products?select=id,name,description,price,delivery_price,image,category,stock,variants,available,pos_available,delivery_available,pos_price,delivery_restriction,metadata" +
                    "&shop_id=eq." + http.enc(shopId) + "&order=created_at.desc",
                auth.session.accessToken
            )
        )
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                merchantProductFromRow(row)?.let(::add)
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

    suspend fun saveProduct(
        auth: NativeAuth,
        shopId: String,
        product: MerchantProduct?,
        draft: MerchantProductDraft,
        gpRate: Double
    ): MerchantProduct {
        val cleanName = draft.name.trim()
        val cleanDescription = draft.description?.trim()?.takeIf { it.isNotBlank() }
        val cleanCategory = draft.category.trim()
        require(cleanName.isNotBlank()) { "กรุณากรอกชื่อสินค้า" }
        require(cleanCategory.isNotBlank()) { "กรุณาเลือกหมวดหมู่สินค้า" }
        require(draft.price >= 0.0 && draft.price.isFinite()) { "ราคาขายหน้าร้านไม่ถูกต้อง" }
        require(draft.stock >= 0) { "จำนวนสินค้าในสต็อกไม่ถูกต้อง" }
        require(gpRate in 0.0..100.0 && gpRate.isFinite()) { "อัตรา GP ไม่ถูกต้อง" }
        val variants = runCatching { JSONArray(draft.variantsJson.ifBlank { "[]" }) }
            .getOrElse { throw IllegalArgumentException("ตัวเลือกสินค้าไม่ถูกต้อง") }
        val restriction = merchantDeliveryRestriction(cleanName, cleanCategory, cleanDescription)
        val deliveryAvailable = restriction == "none" && draft.deliveryAvailable
        val deliveryPrice = merchantDeliveryPriceFromStore(draft.price, gpRate)
        val metadata = JSONObject()
            .put("stock", draft.stock)
            .put("category", cleanCategory)
            .put("variants", variants)
            .put("delivery_restriction", restriction)
        val body = JSONObject()
            .put("shop_id", shopId)
            .put("name", cleanName)
            .put("description", cleanDescription ?: JSONObject.NULL)
            .put("price", draft.price)
            .put("image", draft.image?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("category", cleanCategory)
            .put("stock", draft.stock)
            .put("variants", variants)
            .put("available", draft.available)
            .put("pos_available", draft.posAvailable)
            .put("delivery_available", deliveryAvailable)
            .put("delivery_restriction", restriction)
            .put("pos_price", draft.price)
            .put("delivery_price", deliveryPrice)
            .put("metadata", metadata)

        val raw = if (product != null) {
            http.patch(
                "products?id=eq." + http.enc(product.id) + "&shop_id=eq." + http.enc(shopId),
                auth.session.accessToken,
                body
            )
        } else {
            require(draft.requestId.matches(Regex("^[0-9a-fA-F-]{36}$"))) {
                "รหัสคำขอบันทึกสินค้าไม่ถูกต้อง"
            }
            body.put("id", draft.requestId)
            http.upsert(
                "products?select=*&on_conflict=id",
                auth.session.accessToken,
                body
            )
        }
        val rows = http.array(raw)
        val row = rows.optJSONObject(0) ?: error("ฐานข้อมูลยังไม่ยืนยันการบันทึกสินค้า")
        return merchantProductFromRow(row) ?: error("ข้อมูลสินค้าที่บันทึกไม่สมบูรณ์")
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

    suspend fun loadModules(auth: NativeAuth): List<MerchantModule> {
        val rows = http.array(
            http.get(
                "shop_modules?select=id,shop_user_id,module_key,status,started_at,expires_at,created_at,updated_at" +
                    "&shop_user_id=eq." + http.enc(auth.user.id) +
                    "&order=created_at.asc",
                auth.session.accessToken
            )
        )
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val key = row.optString("module_key")
                if (key.isBlank()) continue
                add(
                    MerchantModule(
                        id = row.optNullable("id"),
                        key = key,
                        status = row.optString("status").ifBlank { "disabled" },
                        startedAt = row.optNullable("started_at"),
                        expiresAt = row.optNullable("expires_at")
                    )
                )
            }
        }
    }

    suspend fun cycleModule(
        auth: NativeAuth,
        key: String,
        module: MerchantModule?
    ): MerchantModule {
        require(key in merchantModuleCatalog.map { it.first }.toSet()) { "ไม่พบโมดูล" }
        val statuses = listOf("disabled", "trial", "enabled")
        val current = module?.status?.takeIf { it in statuses } ?: "disabled"
        val next = statuses[(statuses.indexOf(current) + 1) % statuses.size]
        val now = java.time.Instant.now().toString()
        val body = JSONObject()
            .put("status", next)
            .put("updated_at", now)
        val moduleId = module?.id
        if (moduleId.isNullOrBlank()) {
            body
                .put("shop_user_id", auth.user.id)
                .put("module_key", key)
                .put("started_at", if (next == "disabled") JSONObject.NULL else now)
                .put("expires_at", JSONObject.NULL)
            http.upsert(
                "shop_modules?on_conflict=shop_user_id,module_key",
                auth.session.accessToken,
                body
            )
        } else {
            if (next != "disabled" && module?.startedAt.isNullOrBlank()) body.put("started_at", now)
            if (next == "disabled") body.put("expires_at", JSONObject.NULL)
            http.patch(
                "shop_modules?id=eq." + http.enc(moduleId),
                auth.session.accessToken,
                body
            )
        }
        return loadModules(auth).find { it.key == key }
            ?: MerchantModule(module?.id, key, next, module?.startedAt, null)
    }

    suspend fun loadPromotions(auth: NativeAuth, shopId: String): List<MerchantPromotion> {
        val rows = http.array(
            http.get(
                "promotions?select=id,category,status,max_budget,period_days,payment_status,metadata,created_at" +
                    "&shop_id=eq." + http.enc(shopId) +
                    "&order=created_at.desc",
                auth.session.accessToken
            )
        )
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val meta = r.optJSONObject("metadata")
                add(
                    MerchantPromotion(
                        id = r.optString("id"),
                        title = meta?.optString("title").orEmpty()
                            .ifBlank { r.optString("category").ifBlank { "โปรโมชั่น" } },
                        status = r.optString("status").ifBlank { "pending" },
                        maxBudget = r.optDouble("max_budget", 0.0),
                        periodDays = r.optInt("period_days", 7),
                        paymentStatus = r.optString("payment_status").ifBlank { "pending" },
                        createdAt = r.optNullable("created_at")
                    )
                )
            }
        }
    }

    suspend fun createPromotion(
        auth: NativeAuth,
        shop: MerchantShop,
        title: String,
        maxBudget: Double,
        periodDays: Int
    ) {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotBlank()) { "กรุณากรอกชื่อ/ประเภทโปรโมชั่น" }
        require(maxBudget >= 0.0 && maxBudget.isFinite()) { "งบสูงสุดไม่ถูกต้อง" }
        require(periodDays >= 1) { "จำนวนวันต้องอย่างน้อย 1 วัน" }
        val raw = http.post(
            "promotions",
            auth.session.accessToken,
            JSONObject()
                .put("shop_id", shop.id)
                .put("shop_name", shop.name)
                .put("category", cleanTitle)
                .put("area", shop.address.orEmpty())
                .put("bid_price", 0)
                .put("max_budget", maxBudget)
                .put("period_days", periodDays)
                .put("status", "pending")
                .put("payment_status", "pending")
                .put("metadata", JSONObject().put("title", cleanTitle))
        )
        if (raw is JSONArray && raw.length() == 0) {
            error("ฐานข้อมูลยังไม่ยืนยันคำขอโปรโมชั่น")
        }
    }

    suspend fun loadMarketStock(
        auth: NativeAuth,
        shopId: String
    ): MerchantMarketStockState {
        val productRows = http.array(
            http.get(
                "products?select=id,name,category,price,delivery_price,image,available" +
                    "&shop_id=eq." + http.enc(shopId),
                auth.session.accessToken
            )
        )
        val marketRows = http.array(
            http.get(
                "market_products?select=product_id,unit,pack_size,item_weight_kg,stock_quantity,min_stock,cost_price" +
                    "&shop_id=eq." + http.enc(shopId),
                auth.session.accessToken
            )
        )
        val movementRows = http.array(
            http.get(
                "market_stock_movements?select=type,quantity,note,created_at" +
                    "&shop_id=eq." + http.enc(shopId) +
                    "&order=created_at.desc&limit=30",
                auth.session.accessToken
            )
        )
        val productsById = HashMap<String, JSONObject>()
        for (i in 0 until productRows.length()) {
            val row = productRows.optJSONObject(i) ?: continue
            val id = row.optString("id")
            if (id.isNotBlank()) productsById[id] = row
        }
        val products = buildList {
            for (i in 0 until marketRows.length()) {
                val market = marketRows.optJSONObject(i) ?: continue
                val id = market.optString("product_id")
                val product = productsById[id] ?: continue
                val pack = market.optDouble("pack_size", 1.0).takeIf { it > 0.0 } ?: 1.0
                add(
                    MerchantMarketProduct(
                        productId = id,
                        name = product.optString("name").ifBlank { "สินค้า" },
                        category = product.optString("category"),
                        price = product.optDouble("price", 0.0),
                        deliveryPrice = product.optDouble(
                            "delivery_price",
                            product.optDouble("price", 0.0)
                        ),
                        image = product.optNullable("image"),
                        available = product.optBoolean("available", true),
                        unit = market.optString("unit").ifBlank { "ชิ้น" },
                        packSize = pack,
                        weightKg = market.optDoubleOrNull("item_weight_kg"),
                        stockQuantity = market.optDouble("stock_quantity", 0.0),
                        minStock = market.optDouble("min_stock", 0.0),
                        costPrice = market.optDouble("cost_price", 0.0)
                    )
                )
            }
        }
        val movements = buildList {
            for (i in 0 until movementRows.length()) {
                val row = movementRows.optJSONObject(i) ?: continue
                add(
                    MerchantStockMovement(
                        type = row.optString("type"),
                        quantity = row.optDouble("quantity", 0.0),
                        note = row.optNullable("note"),
                        createdAt = row.optNullable("created_at")
                    )
                )
            }
        }
        return MerchantMarketStockState(products, movements)
    }

    suspend fun saveMarketProduct(auth: NativeAuth, draft: MerchantMarketProductDraft) {
        val name = draft.name.trim()
        val category = draft.category.trim()
        require(name.isNotBlank()) { "กรุณากรอกชื่อสินค้า" }
        require(category in merchantMarketProductCategories) { "กรุณาเลือกหมวดหมู่สินค้าจากรายการ" }
        require(draft.price.isFinite() && draft.price >= 0.0) { "ราคาสินค้าไม่ถูกต้อง" }
        require(draft.packSize.isFinite() && draft.packSize > 0.0) { "ปริมาณต่อชุดไม่ถูกต้อง" }
        require(draft.weightKg.isFinite() && draft.weightKg > 0.0) { "น้ำหนักต่อชุดไม่ถูกต้อง" }
        require(draft.costPrice.isFinite() && draft.costPrice >= 0.0) { "ต้นทุนไม่ถูกต้อง" }
        require(draft.initialStock.isFinite() && draft.initialStock >= 0.0) { "สต๊อกเริ่มต้นไม่ถูกต้อง" }
        require(draft.minStock.isFinite() && draft.minStock >= 0.0) { "ระดับแจ้งเตือนสต๊อกไม่ถูกต้อง" }
        http.rpc(
            "market_save_product",
            auth.session.accessToken,
            JSONObject()
                .put("p_product", draft.productId ?: JSONObject.NULL)
                .put("p_name", name)
                .put("p_category", category)
                .put("p_price", draft.price)
                .put("p_image", draft.image?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("p_unit", draft.unit)
                .put("p_pack_size", draft.packSize)
                .put("p_weight_kg", draft.weightKg)
                .put("p_cost", draft.costPrice)
                .put("p_initial_stock", if (draft.productId == null) draft.initialStock else 0.0)
                .put("p_min_stock", draft.minStock)
                .put("p_available", draft.available)
        )
    }

    suspend fun adjustMarketStock(
        auth: NativeAuth,
        productId: String,
        delta: Double,
        note: String
    ) {
        require(delta.isFinite() && delta != 0.0) { "ระบุจำนวนที่ไม่เป็นศูนย์" }
        val cleanNote = note.trim()
        require(cleanNote.isNotBlank()) { "กรุณาระบุเหตุผล" }
        http.rpc(
            "market_adjust_stock",
            auth.session.accessToken,
            JSONObject()
                .put("p_product", productId)
                .put("p_delta", delta)
                .put("p_type", if (delta > 0.0) "purchase" else "adjustment")
                .put("p_note", cleanNote)
        )
    }

    suspend fun loadNotifications(auth: NativeAuth): List<MerchantNotification> {
        val rows = http.array(
            http.get(
                "notifications?select=id,title,message,type,reference_id,is_read,created_at" +
                    "&user_id=eq." + http.enc(auth.user.id) +
                    "&order=created_at.desc&limit=200",
                auth.session.accessToken
            )
        )
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val id = r.optString("id")
                if (id.isBlank()) continue
                add(
                    MerchantNotification(
                        id = id,
                        title = r.optString("title").ifBlank { "การแจ้งเตือน" },
                        message = r.optString("message"),
                        type = r.optString("type").ifBlank { "system" },
                        referenceId = r.optNullable("reference_id"),
                        isRead = r.optBoolean("is_read", false),
                        createdAt = r.optNullable("created_at")
                    )
                )
            }
        }
    }

    suspend fun markNotificationsRead(auth: NativeAuth) {
        http.patch(
            "notifications?user_id=eq." + http.enc(auth.user.id) + "&is_read=eq.false",
            auth.session.accessToken,
            JSONObject().put("is_read", true)
        )
    }

    suspend fun loadSupportMessages(auth: NativeAuth): List<MerchantSupportMessage> {
        val rows = http.array(
            http.get(
                "shop_support_messages?select=id,sender_user_id,body,created_at,slip_path" +
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
                        createdAt = r.optNullable("created_at"),
                        slipPath = r.optNullable("slip_path")
                    )
                )
            }
        }
    }

    suspend fun sendSupportMessage(auth: NativeAuth, body: String, requestId: String = UUID.randomUUID().toString()) {
        val text = body.trim()
        require(text.isNotBlank()) { "กรุณาพิมพ์ข้อความ" }
        require(text.length <= 2000) { "ข้อความยาวเกิน 2000 ตัวอักษร" }
        val path = "shop_support_messages?select=id&id=eq." + http.enc(requestId) +
            "&shop_user_id=eq." + http.enc(auth.user.id) + "&sender_user_id=eq." + http.enc(auth.user.id)
        sendNativeChatOnce(
            exists = { http.array(http.get(path, auth.session.accessToken)).length() > 0 },
            insert = {
                val raw = http.post("shop_support_messages", auth.session.accessToken,
                    JSONObject().put("id", requestId).put("shop_user_id", auth.user.id)
                        .put("sender_user_id", auth.user.id).put("body", text))
                val rows = http.array(raw)
                (0 until rows.length()).any { rows.optJSONObject(it)?.optString("id") == requestId }
            }
        )
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


private fun merchantProductFromRow(r: JSONObject): MerchantProduct? {
    val id = r.optString("id")
    if (id.isBlank()) return null
    val metadata = r.optJSONObject("metadata")
    val category = r.optString("category")
        .ifBlank { metadata?.optString("category").orEmpty() }
    val stock = if (r.has("stock") && !r.isNull("stock")) {
        r.optInt("stock", 0)
    } else metadata?.optInt("stock", 0) ?: 0
    val variantsRaw = if (r.has("variants") && !r.isNull("variants")) {
        r.opt("variants")
    } else metadata?.opt("variants")
    val restriction = r.optString("delivery_restriction")
        .ifBlank { metadata?.optString("delivery_restriction").orEmpty() }
        .ifBlank { "none" }
    val price = r.optDouble("price", 0.0)
    return MerchantProduct(
        id = id,
        name = r.optString("name").ifBlank { "สินค้า" },
        description = r.optNullable("description"),
        price = price,
        deliveryPrice = r.optDouble("delivery_price", price),
        image = r.optNullable("image"),
        available = r.optBoolean("available", true),
        category = category,
        stock = stock.coerceAtLeast(0),
        variantsJson = merchantVariantsJson(variantsRaw),
        posAvailable = r.optBoolean("pos_available", true),
        deliveryAvailable = r.optBoolean("delivery_available", true),
        posPrice = r.optDoubleOrNull("pos_price") ?: price,
        deliveryRestriction = restriction
    )
}

internal fun merchantVariantsJson(raw: Any?): String = when (raw) {
    is JSONArray -> raw.toString()
    is String -> runCatching { JSONArray(raw).toString() }.getOrDefault("[]")
    else -> "[]"
}

internal fun merchantDeliveryPriceFromStore(price: Double, gpRate: Double): Double {
    if (!price.isFinite() || price < 0.0) return 0.0
    val rate = gpRate.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    return kotlin.math.round(price * (1.0 + rate / 100.0) * 100.0) / 100.0
}

internal fun merchantDeliveryRestriction(
    name: String?,
    category: String?,
    description: String?
): String {
    val text = listOf(name, category, description).joinToString(" ") { it.orEmpty() }
        .lowercase()
    val tobaccoThai = Regex("(บุหรี่|ยาสูบ|ซิการ์|บุหรี่ไฟฟ้า)")
    val tobaccoEnglish = Regex("\\b(cigarette|cigar|tobacco|vape)\\b", RegexOption.IGNORE_CASE)
    if (tobaccoThai.containsMatchIn(text) || tobaccoEnglish.containsMatchIn(text)) return "tobacco"
    val alcoholThai = Regex("(เบียร์|เหล้า|ไวน์|วิสกี้|วอดก้า|บรั่นดี|สุราขาว|สุราพื้นบ้าน)")
    val alcoholEnglish = Regex("\\b(beer|wine|vodka|rum|gin|brandy|whisky|whiskey)\\b", RegexOption.IGNORE_CASE)
    if (alcoholThai.containsMatchIn(text) || alcoholEnglish.containsMatchIn(text)) return "alcohol"
    return "none"
}

internal fun merchantDeliveryRestrictionLabel(kind: String): String = when (kind) {
    "tobacco" -> "ยาสูบ/บุหรี่"
    "alcohol" -> "แอลกอฮอล์"
    else -> ""
}

internal fun merchantMarketStockPermitted(category: String?): Boolean =
    category?.lowercase() in setOf("market", "meat", "fish", "vegetable", "fruit", "grocery")

internal val merchantModuleCatalog = listOf(
    "storefront" to "หน้าร้าน",
    "products" to "หน้าสินค้า",
    "orders" to "ระบบรับออเดอร์",
    "delivery" to "ระบบจัดส่ง",
    "management" to "ระบบจัดการร้าน",
    "promote" to "ระบบโปรโมต"
)

internal fun merchantModuleStatusLabel(status: String): String = when (status) {
    "enabled" -> "เปิดใช้งาน"
    "trial" -> "ทดลองใช้"
    "disabled" -> "ปิดใช้งาน"
    else -> "ยังไม่ได้ตั้งค่า"
}

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
