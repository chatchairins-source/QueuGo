package com.queuego.merchant

import com.queuego.shared.nativeOrderNumber

import com.queuego.shared.NativeAuth
import com.queuego.shared.nativePosPermissionAllowed
import com.queuego.shared.QueueGoNativeApi
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID

data class PosProduct(
    val id: String,
    val name: String,
    val price: Double,
    val image: String?,
    val available: Boolean,
    val deliveryAvailable: Boolean = true,
    val deliveryPrice: Double? = null
)

data class PosTable(
    val id: String,
    val label: String,
    val active: Boolean,
    val qrToken: String? = null
)

data class PosBill(
    val id: String,
    val number: String,
    val type: String,
    val tableId: String?,
    val kitchenStatus: String,
    val paymentStatus: String,
    val total: Double,
    val discount: Double,
    val status: String = "pending",
    val paymentMethod: String? = null,
    val billStatus: String? = null,
    val cashTendered: Double? = null,
    val cashChange: Double? = null,
    val paidAt: String? = null,
    val subtotal: Double = 0.0,
    val createdAt: String? = null,
    val staffId: String? = null
) {
    val open: Boolean get() = paymentStatus == "UNPAID" && status != "cancelled"
}

data class PosLine(
    val productId: String?,
    val name: String,
    val description: String?,
    val quantity: Int,
    val total: Double,
    val kitchenStatus: String? = null,
    val batch: Int = 0
)

data class PosStaff(
    val userId: String,
    val displayName: String,
    val role: String,
    val permissions: Set<String>,
    val active: Boolean,
    val deniedPermissions: Set<String> = emptySet()
) {
    fun allows(permission: String): Boolean = nativePosPermissionAllowed(
        active, role, permission, permissions, deniedPermissions
    )
}

data class PosDeliveryOrder(
    val id: String,
    val number: String,
    val status: String,
    val riderId: String?,
    val createdAt: String?
)

data class PosOwnerDashboard(
    val dineIn: Double,
    val takeaway: Double,
    val delivery: Double,
    val gp: Double,
    val orders: Int
)

data class PosHistoryRow(
    val id: String,
    val number: String,
    val type: String,
    val salesChannel: String?,
    val status: String,
    val total: Double,
    val createdAt: String?,
    val staffId: String?
)

data class PosDeliveryReadiness(
    val shopName: Boolean,
    val location: Boolean,
    val menu: Boolean,
    val hours: Boolean,
    val approved: Boolean,
    val deliveryEnabled: Boolean,
    val shopOpen: Boolean
) {
    val complete: Boolean get() = shopName && location && menu && hours && approved
}

data class PosSnapshot(
    val shopId: String,
    val shopName: String,
    val owner: Boolean,
    val currentStaff: PosStaff?,
    val staff: List<PosStaff>,
    val products: List<PosProduct>,
    val tables: List<PosTable>,
    val bills: List<PosBill>,
    val linesByOrder: Map<String, List<PosLine>>,
    val deliveryOrders: List<PosDeliveryOrder>,
    val deliveryLinesByOrder: Map<String, List<PosLine>>
)

class PosAccessDeniedException(message: String) : IllegalStateException(message)

class MerchantPosApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun snapshot(auth: NativeAuth): PosSnapshot {
        val rawShop = http.rpc("pos_my_shop", auth.session.accessToken, JSONObject())
        val shopId = scalarText(rawShop)
        if (shopId.isBlank() || shopId == "null") {
            throw PosAccessDeniedException("บัญชีนี้ยังไม่มีสิทธิ์ POS ของร้าน")
        }

        val owner = scalarBoolean(
            http.rpc("pos_is_owner", auth.session.accessToken, JSONObject()),
            "pos_is_owner"
        )
        val profileRaw = http.array(http.get(
            "shop_profiles?select=shop_name&id=eq." + http.enc(shopId) + "&limit=1",
            auth.session.accessToken
        ))
        val shopName = profileRaw.optJSONObject(0)?.optString("shop_name")
            ?.takeIf { it.isNotBlank() } ?: "ร้านค้า"

        val tablesRaw = http.array(http.get(
            "pos_tables?select=id,label,active,qr_token&shop_id=eq." + http.enc(shopId) + "&order=label.asc",
            auth.session.accessToken
        ))
        val productsRaw = http.array(http.get(
            "products?select=id,name,price,pos_price,delivery_price,image,available,pos_available,delivery_available" +
                "&shop_id=eq." + http.enc(shopId) + "&order=name.asc",
            auth.session.accessToken
        ))
        val billsRaw = http.array(http.get(
            "orders?select=id,order_number,order_type,table_id,staff_id,status,kitchen_status,payment_status," +
                "payment_method,bill_status,cash_tendered,cash_change,paid_at,subtotal,total_amount,discount_amount,created_at" +
                "&shop_id=eq." + http.enc(shopId) +
                "&sales_channel=eq.POS&order=created_at.desc&limit=200",
            auth.session.accessToken
        ))
        val openBillsRaw = http.array(http.get(
            "orders?select=id,order_number,order_type,table_id,staff_id,status,kitchen_status,payment_status," +
                "payment_method,bill_status,cash_tendered,cash_change,paid_at,subtotal,total_amount,discount_amount,created_at" +
                "&shop_id=eq." + http.enc(shopId) +
                "&sales_channel=eq.POS&payment_status=eq.UNPAID&status=neq.cancelled&order=created_at.desc",
            auth.session.accessToken
        ))
        val mergedBillsRaw = JSONArray()
        val seenBillIds = mutableSetOf<String>()
        for (source in listOf(openBillsRaw, billsRaw)) {
            for (i in 0 until source.length()) {
                val row = source.optJSONObject(i) ?: continue
                val id = row.optString("id")
                if (id.isBlank() || !seenBillIds.add(id)) continue
                mergedBillsRaw.put(row)
            }
        }
        val staffRaw = http.array(http.get(
            "pos_staff?select=user_id,shop_id,display_name,staff_role,permissions,active" +
                "&shop_id=eq." + http.enc(shopId),
            auth.session.accessToken
        ))
        val deliveryRaw = http.array(http.get(
            "orders?select=id,order_number,order_type,sales_channel,status,rider_id,shop_id,created_at" +
                "&shop_id=eq." + http.enc(shopId) +
                "&or=(sales_channel.eq.QUEUEGO_DELIVERY,and(sales_channel.is.null,order_type.eq.shopping))" +
                "&status=in.(pending,accepted,searching_rider,rider_assigned,preparing,ready,assigned)" +
                "&order=created_at.desc&limit=100",
            auth.session.accessToken
        ))

        val billIds = idsOf(mergedBillsRaw)
        val deliveryIds = idsOf(deliveryRaw)
        val itemsRaw = if (billIds.isEmpty()) JSONArray() else http.array(http.get(
            "order_items?select=order_id,product_id,item_name,description,quantity,total_price,pos_kitchen_status,pos_batch" +
                "&order_id=in.(" + billIds.joinToString(",") + ")&order=created_at.asc",
            auth.session.accessToken
        ))
        val deliveryItemsRaw = if (deliveryIds.isEmpty()) JSONArray() else http.array(http.get(
            "order_items?select=order_id,product_id,item_name,description,quantity,total_price,pos_kitchen_status,pos_batch" +
                "&order_id=in.(" + deliveryIds.joinToString(",") + ")&order=created_at.asc",
            auth.session.accessToken
        ))

        val staff = parseStaff(staffRaw)
        val currentStaff = staff.firstOrNull { it.userId == auth.session.authUserId }
        if (!owner && currentStaff?.active != true) {
            throw PosAccessDeniedException("บัญชีพนักงานนี้ไม่มีสิทธิ์ POS ที่เปิดใช้งาน")
        }
        val products = buildList {
            for (i in 0 until productsRaw.length()) {
                val r = productsRaw.optJSONObject(i) ?: continue
                val posAvailable = if (r.has("pos_available") && !r.isNull("pos_available")) {
                    r.optBoolean("pos_available")
                } else true
                val normalAvailable = r.optBoolean("available", true)
                if (!normalAvailable || !posAvailable) continue
                add(
                    PosProduct(
                        id = r.optString("id"),
                        name = r.optString("name").ifBlank { "สินค้า" },
                        price = if (r.has("pos_price") && !r.isNull("pos_price")) {
                            r.optDouble("pos_price")
                        } else r.optDouble("price", 0.0),
                        image = nullableString(r, "image"),
                        available = true,
                        deliveryAvailable = if (r.has("delivery_available") && !r.isNull("delivery_available")) {
                            r.optBoolean("delivery_available")
                        } else true,
                        deliveryPrice = if (r.has("delivery_price") && !r.isNull("delivery_price")) {
                            r.optDouble("delivery_price")
                        } else null
                    )
                )
            }
        }
        val tables = buildList {
            for (i in 0 until tablesRaw.length()) {
                val r = tablesRaw.optJSONObject(i) ?: continue
                add(
                    PosTable(
                        id = r.optString("id"),
                        label = r.optString("label").ifBlank { "โต๊ะ" },
                        active = r.optBoolean("active", true),
                        qrToken = nullableString(r, "qr_token")
                    )
                )
            }
        }
        val bills = buildList {
            for (i in 0 until mergedBillsRaw.length()) {
                val r = mergedBillsRaw.optJSONObject(i) ?: continue
                val id = r.optString("id")
                if (id.isBlank()) continue
                add(parseBill(r, id))
            }
        }
        val deliveryOrders = buildList {
            for (i in 0 until deliveryRaw.length()) {
                val r = deliveryRaw.optJSONObject(i) ?: continue
                val id = r.optString("id")
                if (id.isBlank()) continue
                add(
                    PosDeliveryOrder(
                        id = id,
                        number = orderNumber(r.optString("order_number"), id),
                        status = r.optString("status").ifBlank { "pending" },
                        riderId = nullableString(r, "rider_id"),
                        createdAt = nullableString(r, "created_at")
                    )
                )
            }
        }
        return PosSnapshot(
            shopId = shopId,
            shopName = shopName,
            owner = owner,
            currentStaff = currentStaff,
            staff = staff,
            products = products,
            tables = tables,
            bills = bills,
            linesByOrder = parseLines(itemsRaw),
            deliveryOrders = deliveryOrders,
            deliveryLinesByOrder = parseLines(deliveryItemsRaw)
        )
    }

    suspend fun addProduct(
        auth: NativeAuth,
        currentBillId: String?,
        type: String,
        tableId: String?,
        productId: String,
        note: String,
        requestId: String = UUID.randomUUID().toString()
    ): String {
        val raw = if (currentBillId.isNullOrBlank()) {
            http.rpc(
                "pos_create_bill_once",
                auth.session.accessToken,
                JSONObject()
                    .put("p_request", requestId)
                    .put("p_type", type)
                    .put("p_table", tableId ?: JSONObject.NULL)
                    .put("p_product", productId)
                    .put("p_note", note.trim())
            )
        } else {
            http.rpc(
                "pos_edit_bill",
                auth.session.accessToken,
                JSONObject()
                    .put("p_order", currentBillId)
                    .put("p_type", type)
                    .put("p_table", tableId ?: JSONObject.NULL)
                    .put("p_product", productId)
                    .put("p_quantity", 1)
                    .put("p_note", note.trim())
            )
        }
        return scalarId(raw) ?: currentBillId ?: error("Server ยังไม่ยืนยันบิล")
    }

    suspend fun reduceProduct(
        auth: NativeAuth,
        billId: String,
        type: String,
        tableId: String?,
        productId: String,
        note: String?
    ) {
        http.rpc(
            "pos_edit_bill",
            auth.session.accessToken,
            JSONObject()
                .put("p_order", billId)
                .put("p_type", type)
                .put("p_table", tableId ?: JSONObject.NULL)
                .put("p_product", productId)
                .put("p_quantity", -1)
                .put("p_note", note ?: "")
        )
    }

    suspend fun billAction(auth: NativeAuth, billId: String, action: String) {
        http.rpc(
            "pos_bill_action",
            auth.session.accessToken,
            JSONObject()
                .put("p_order", billId)
                .put("p_action", action)
                .put("p_method", JSONObject.NULL)
        )
    }

    suspend fun cancelBill(auth: NativeAuth, billId: String, reason: String) {
        require(reason.trim().length in 3..500) { "กรุณาระบุเหตุผลที่ยกเลิกอย่างน้อย 3 ตัวอักษร" }
        http.rpc(
            "pos_cancel_bill",
            auth.session.accessToken,
            JSONObject().put("p_order", billId).put("p_reason", reason.trim())
        )
    }

    suspend fun takePayment(
        auth: NativeAuth,
        billId: String,
        method: String,
        cashReceived: Double? = null
    ): String {
        require(method in setOf("cash", "bank_transfer", "promptpay", "card", "other")) {
            "วิธีชำระเงินไม่ถูกต้อง"
        }
        val raw = http.rpc(
            "pos_take_payment",
            auth.session.accessToken,
            JSONObject()
                .put("p_order", billId)
                .put("p_method", method)
                .put("p_cash_received", if (method == "cash") cashReceived ?: JSONObject.NULL else JSONObject.NULL)
        )
        return scalarId(raw) ?: billId
    }

    suspend fun takeCash(auth: NativeAuth, billId: String, received: Double): String =
        takePayment(auth, billId, "cash", received)

    suspend fun applyDiscount(auth: NativeAuth, billId: String, amount: Double) {
        require(amount >= 0.0) { "ส่วนลดต้องไม่ติดลบ" }
        http.rpc(
            "pos_apply_discount",
            auth.session.accessToken,
            JSONObject().put("p_order", billId).put("p_amount", amount)
        )
    }

    suspend fun refundBill(auth: NativeAuth, billId: String, reason: String) {
        require(reason.trim().length in 5..500) { "กรุณาระบุเหตุผลคืนเงินอย่างน้อย 5 ตัวอักษร" }
        http.rpc(
            "pos_refund_bill",
            auth.session.accessToken,
            JSONObject().put("p_order", billId).put("p_reason", reason.trim())
        )
    }

    suspend fun changePrice(auth: NativeAuth, productId: String, price: Double) {
        require(price in 0.0..999999.0) { "ราคาหน้าร้านไม่ถูกต้อง" }
        http.rpc(
            "pos_change_price",
            auth.session.accessToken,
            JSONObject().put("p_product", productId).put("p_price", price)
        )
    }

    suspend fun saveTable(
        auth: NativeAuth,
        id: String?,
        label: String,
        active: Boolean
    ): String {
        require(label.trim().length in 1..40) { "กรุณากรอกชื่อโต๊ะ" }
        val raw = http.rpc(
            "pos_save_table",
            auth.session.accessToken,
            JSONObject()
                .put("p_id", id ?: JSONObject.NULL)
                .put("p_label", label.trim())
                .put("p_active", active)
        )
        return scalarId(raw) ?: id ?: error("Server ยังไม่ยืนยันโต๊ะ")
    }

    suspend fun deleteTable(auth: NativeAuth, id: String) {
        http.rpc(
            "pos_delete_table",
            auth.session.accessToken,
            JSONObject().put("p_id", id)
        )
    }

    suspend fun rotateTableQr(auth: NativeAuth, tableId: String): String {
        val raw = http.rpc(
            "qg_table_rotate_qr",
            auth.session.accessToken,
            JSONObject().put("p_table", tableId)
        )
        return scalarId(raw) ?: error("สร้าง QR Code ไม่สำเร็จ")
    }

    suspend fun createStaffInvite(auth: NativeAuth, role: String): String {
        require(role in setOf("WAITER", "CASHIER", "KITCHEN")) { "หน้าที่พนักงานไม่ถูกต้อง" }
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val secret = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        http.rpc(
            "pos_create_role_invite",
            auth.session.accessToken,
            JSONObject().put("p_secret", secret).put("p_staff_role", role)
        )
        return secret
    }

    suspend fun setStaffRole(
        auth: NativeAuth,
        userId: String,
        role: String,
        active: Boolean
    ) {
        require(role in setOf("WAITER", "CASHIER", "KITCHEN")) { "หน้าที่พนักงานไม่ถูกต้อง" }
        http.rpc(
            "pos_set_staff_role",
            auth.session.accessToken,
            JSONObject()
                .put("p_user", userId)
                .put("p_role", role)
                .put("p_active", active)
        )
    }

    suspend fun ownerDashboard(auth: NativeAuth, days: Int): PosOwnerDashboard {
        require(days in setOf(1, 7, 30)) { "ช่วงรายงานไม่ถูกต้อง" }
        val raw = http.rpc(
            "pos_owner_dashboard",
            auth.session.accessToken,
            JSONObject().put("p_days", days)
        )
        val o = objectValue(raw)
        return PosOwnerDashboard(
            dineIn = o.optDouble("DINE_IN", 0.0),
            takeaway = o.optDouble("TAKEAWAY", 0.0),
            delivery = o.optDouble("DELIVERY", 0.0),
            gp = o.optDouble("GP", 0.0),
            orders = o.optInt("orders", 0)
        )
    }

    suspend fun history(
        auth: NativeAuth,
        shopId: String,
        startIso: String,
        endIso: String,
        type: String = "ALL"
    ): List<PosHistoryRow> {
        val rows = http.array(http.get(
            "orders?select=id,order_number,order_type,sales_channel,status,total_amount,created_at,staff_id" +
                "&shop_id=eq." + http.enc(shopId) +
                "&created_at=gte." + http.enc(startIso) +
                "&created_at=lt." + http.enc(endIso) +
                "&order=created_at.desc&limit=200",
            auth.session.accessToken
        ))
        return buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val rowType = r.optString("order_type")
                val sales = nullableString(r, "sales_channel")
                val include = when (type) {
                    "ALL" -> true
                    "DELIVERY" -> sales == "QUEUEGO_DELIVERY" || rowType == "shopping"
                    else -> rowType == type
                }
                if (!include) continue
                val id = r.optString("id")
                if (id.isBlank()) continue
                add(
                    PosHistoryRow(
                        id = id,
                        number = orderNumber(r.optString("order_number"), id),
                        type = rowType,
                        salesChannel = sales,
                        status = r.optString("status"),
                        total = r.optDouble("total_amount", 0.0),
                        createdAt = nullableString(r, "created_at"),
                        staffId = nullableString(r, "staff_id")
                    )
                )
            }
        }
    }

    suspend fun deliveryAction(auth: NativeAuth, orderId: String, action: String) {
        require(action in setOf("accepted", "preparing", "ready")) { "สถานะ Delivery ไม่ถูกต้อง" }
        http.rpc(
            "pos_delivery_kitchen_action",
            auth.session.accessToken,
            JSONObject().put("p_order", orderId).put("p_action", action)
        )
    }

    suspend fun deliveryReadiness(auth: NativeAuth, snapshot: PosSnapshot): PosDeliveryReadiness {
        val profile = http.array(http.get(
            "shop_profiles?select=shop_name,address,latitude,longitude,status,delivery_enabled,public_open_time,public_close_time" +
                "&id=eq." + http.enc(snapshot.shopId) + "&limit=1",
            auth.session.accessToken
        )).optJSONObject(0) ?: JSONObject()
        val hours = http.array(http.get(
            "shop_business_hours?select=weekday,opens_at,closes_at,is_closed&shop_id=eq." + http.enc(snapshot.shopId),
            auth.session.accessToken
        ))
        val open = http.array(http.get(
            "shop_open_states?select=is_open&shop_id=eq." + http.enc(snapshot.shopId) + "&limit=1",
            auth.session.accessToken
        )).optJSONObject(0)
        val menu = snapshot.products.any {
            it.available && it.deliveryAvailable && (it.deliveryPrice ?: it.price) >= 0.0
        }
        var businessHours = false
        for (i in 0 until hours.length()) {
            val row = hours.optJSONObject(i) ?: continue
            if (!row.optBoolean("is_closed", false) &&
                nullableString(row, "opens_at") != null &&
                nullableString(row, "closes_at") != null
            ) {
                businessHours = true
                break
            }
        }
        if (!businessHours) {
            businessHours = nullableString(profile, "public_open_time") != null &&
                nullableString(profile, "public_close_time") != null
        }
        return PosDeliveryReadiness(
            shopName = profile.optString("shop_name").isNotBlank(),
            location = profile.optString("address").isNotBlank() &&
                profile.has("latitude") && !profile.isNull("latitude") &&
                profile.has("longitude") && !profile.isNull("longitude"),
            menu = menu,
            hours = businessHours,
            approved = profile.optString("status") == "active",
            deliveryEnabled = profile.optBoolean("delivery_enabled", false),
            shopOpen = open?.optBoolean("is_open", true) ?: true
        )
    }

    suspend fun enableDelivery(auth: NativeAuth) {
        http.rpc("pos_enable_delivery", auth.session.accessToken, JSONObject())
    }

    private fun idsOf(rows: JSONArray): List<String> = buildList {
        for (i in 0 until rows.length()) {
            rows.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun parseLines(itemsRaw: JSONArray): Map<String, List<PosLine>> {
        val lines = mutableMapOf<String, MutableList<PosLine>>()
        for (i in 0 until itemsRaw.length()) {
            val r = itemsRaw.optJSONObject(i) ?: continue
            val orderId = r.optString("order_id")
            if (orderId.isBlank()) continue
            lines.getOrPut(orderId) { mutableListOf() }.add(
                PosLine(
                    productId = nullableString(r, "product_id"),
                    name = r.optString("item_name").ifBlank { "สินค้า" },
                    description = nullableString(r, "description"),
                    quantity = r.optInt("quantity", 1).coerceAtLeast(1),
                    total = r.optDouble("total_price", 0.0),
                    kitchenStatus = nullableString(r, "pos_kitchen_status"),
                    batch = r.optInt("pos_batch", 0)
                )
            )
        }
        return lines
    }

    private fun parseStaff(rows: JSONArray): List<PosStaff> = buildList {
        for (i in 0 until rows.length()) {
            val r = rows.optJSONObject(i) ?: continue
            val id = r.optString("user_id")
            if (id.isBlank()) continue
            val permissions = mutableSetOf<String>()
            val deniedPermissions = mutableSetOf<String>()
            val p = r.optJSONObject("permissions") ?: JSONObject()
            val keys = p.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (p.optBoolean(key, false)) permissions += key
                else deniedPermissions += key
            }
            add(
                PosStaff(
                    userId = id,
                    displayName = r.optString("display_name").ifBlank { "พนักงาน" },
                    role = r.optString("staff_role").ifBlank { "WAITER" },
                    permissions = permissions,
                    active = r.optBoolean("active", false),
                    deniedPermissions = deniedPermissions
                )
            )
        }
    }

    private fun parseBill(r: JSONObject, id: String): PosBill = PosBill(
        id = id,
        number = orderNumber(r.optString("order_number"), id),
        type = r.optString("order_type").ifBlank { "TAKEAWAY" },
        tableId = nullableString(r, "table_id"),
        kitchenStatus = r.optString("kitchen_status").ifBlank { "NEW" },
        paymentStatus = r.optString("payment_status").ifBlank { "UNPAID" },
        total = r.optDouble("total_amount", 0.0),
        discount = r.optDouble("discount_amount", 0.0),
        status = r.optString("status").ifBlank { "pending" },
        paymentMethod = nullableString(r, "payment_method"),
        billStatus = nullableString(r, "bill_status"),
        cashTendered = nullableDouble(r, "cash_tendered"),
        cashChange = nullableDouble(r, "cash_change"),
        paidAt = nullableString(r, "paid_at"),
        subtotal = r.optDouble("subtotal", 0.0),
        createdAt = nullableString(r, "created_at"),
        staffId = nullableString(r, "staff_id")
    )

    private fun nullableString(o: JSONObject, key: String): String? =
        if (!o.has(key) || o.isNull(key)) null
        else o.optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun nullableDouble(o: JSONObject, key: String): Double? =
        if (!o.has(key) || o.isNull(key)) null else o.optDouble(key).takeIf { it.isFinite() }

    private fun scalarBoolean(raw: Any, key: String): Boolean = when (raw) {
        is Boolean -> raw
        is JSONObject -> raw.optBoolean(key, false)
        is JSONArray -> when (val v = raw.opt(0)) {
            is Boolean -> v
            is JSONObject -> v.optBoolean(key, false)
            else -> v.toString().toBooleanStrictOrNull() ?: false
        }
        else -> raw.toString().toBooleanStrictOrNull() ?: false
    }

    private fun objectValue(raw: Any): JSONObject = when (raw) {
        is JSONObject -> raw
        is JSONArray -> raw.optJSONObject(0) ?: JSONObject()
        is String -> runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        else -> JSONObject()
    }

    private fun scalarText(raw: Any): String = when (raw) {
        is String -> raw.trim().trim('"')
        is JSONObject -> raw.optString("pos_my_shop").ifBlank { raw.optString("id") }
        is JSONArray -> raw.opt(0)?.toString()?.trim()?.trim('"').orEmpty()
        else -> raw.toString().trim().trim('"')
    }

    private fun scalarId(raw: Any): String? = when (raw) {
        is String -> raw.trim('"').takeIf { it.isNotBlank() && it != "null" }
        is JSONObject -> raw.optString("id").ifBlank {
            raw.optString("pos_create_bill_once").ifBlank {
                raw.optString("pos_edit_bill").ifBlank {
                    raw.optString("pos_save_table").ifBlank {
                        raw.optString("qg_table_rotate_qr")
                    }
                }
            }
        }.takeIf { it.isNotBlank() }
        is JSONArray -> {
            val first = raw.opt(0)
            when (first) {
                is String -> first
                is JSONObject -> first.optString("id").ifBlank {
                    first.optString("pos_create_bill_once").ifBlank {
                        first.optString("pos_save_table").ifBlank { first.optString("qg_table_rotate_qr") }
                    }
                }
                else -> first?.toString()
            }?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }
        }
        else -> raw.toString().trim('"').takeIf { it.isNotBlank() && it != "null" }
    }

    private fun orderNumber(raw: String?, id: String): String = nativeOrderNumber(raw)
}
