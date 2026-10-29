package com.queuego.merchant

import com.queuego.shared.nativeOrderNumber

import com.queuego.shared.NativeAuth
import com.queuego.shared.QueueGoHttpException
import com.queuego.shared.nativePosPermissionAllowed
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.net.SocketTimeoutException
import java.util.UUID

data class PosProduct(
    val id: String,
    val name: String,
    val price: Double,
    val image: String?,
    val available: Boolean,
    val deliveryAvailable: Boolean = true,
    val deliveryPrice: Double? = null,
    val stock: Int = 0,
    val inventoryStockTracking: Boolean = false,
    val groceryMasterId: String? = null,
    val groceryStockTracking: Boolean = false,
    val optionGroups: List<PosProductOptionGroup> = emptyList(),
    val category: String? = null
)

internal fun posProductInventoryTracked(product: PosProduct): Boolean =
    product.inventoryStockTracking ||
        (!product.groceryMasterId.isNullOrBlank() && product.groceryStockTracking)

internal fun posProductSoldOut(product: PosProduct): Boolean =
    posProductInventoryTracked(product) && product.stock <= 0

data class PosTable(
    val id: String,
    val label: String,
    val active: Boolean,
    val qrToken: String? = null
)

data class PosShiftHistory(
    val id: String,
    val cashierName: String,
    val openedAt: String,
    val closedAt: String?,
    val openingFloat: Double,
    val expectedCash: Double?,
    val countedCash: Double?,
    val variance: Double?
)

// Read-only reconciliation after an uncertain shift RPC. Never repeat the mutation
// just because the response or a subsequent refresh was lost.
internal fun posOpenShiftMatches(shift: PosCashShift?, requestedOpening: Double): Boolean =
    shift?.open == true &&
        shift.openingFloat != null &&
        requestedOpening.isFinite() &&
        kotlin.math.abs(shift.openingFloat - requestedOpening) < 0.005

internal fun posCloseShiftMatches(
    currentShift: PosCashShift?,
    history: List<PosShiftHistory>,
    shiftId: String?,
    requestedCountedCash: Double
): Boolean {
    if (currentShift?.open != false || shiftId.isNullOrBlank() || !requestedCountedCash.isFinite()) {
        return false
    }
    val closed = history.firstOrNull { it.id == shiftId } ?: return false
    val counted = closed.countedCash ?: return false
    return !closed.closedAt.isNullOrBlank() &&
        kotlin.math.abs(counted - requestedCountedCash) < 0.005
}

data class PosCodeLookup(
    val productId: String,
    val name: String,
    val available: Boolean,
    val posAvailable: Boolean
)

data class PosProductCode(
    val id: String,
    val productId: String,
    val productName: String,
    val type: String,
    val code: String
)

data class PosCashShift(
    val open: Boolean,
    val expectedCash: Double?,
    val openingFloat: Double?,
    val cashierName: String?,
    val cashInTotal: Double? = null,
    val cashOutTotal: Double? = null,
    val id: String? = null
)

// Only a PAID + CLOSED bill for the same method and tender can acknowledge the checkout.
internal fun posPaymentReceiptMatches(
    order: JSONObject?, id: String, method: String, cashReceived: Double?
): Boolean {
    if (order == null || order.optString("id") != id ||
        order.optString("payment_status") != "PAID" ||
        order.optString("bill_status") != "CLOSED" ||
        order.optString("payment_method") != method) return false
    if (method != "cash") return true
    val actual = order.optDouble("cash_tendered", Double.NaN)
    return actual.isFinite() && cashReceived != null && cashReceived.isFinite() &&
        kotlin.math.abs(actual - cashReceived) < 0.005
}

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
    val staffId: String? = null,
    val taxEnabledSnapshot: Boolean = false,
    val taxRateSnapshot: Double? = null,
    val pricesIncludeTaxSnapshot: Boolean? = null,
    val taxableBase: Double? = null,
    val taxAmount: Double? = null,
    val taxRegistrationSnapshot: String? = null,
    val taxBranchSnapshot: String? = null,
    val taxAddressSnapshot: String? = null
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

data class PosItemSalesRow(
    val name: String,
    val quantity: Double,
    val bills: Int,
    val sales: Double
)

data class PosItemSalesReport(
    val itemCount: Int,
    val items: List<PosItemSalesRow>
)

data class PosHourlySalesRow(
    val hour: Int,
    val bills: Int,
    val sales: Double
)

data class PosOwnerDashboard(
    val dineIn: Double,
    val takeaway: Double,
    val delivery: Double,
    val gp: Double,
    val orders: Int,
    val averageTicket: Double = 0.0,
    val cashSales: Double = 0.0,
    val discounts: Double = 0.0,
    val refunds: Double = 0.0,
    val voids: Int = 0,
    val changePercent: Double? = null,
    val hourlySales: List<PosHourlySalesRow> = emptyList()
)

internal fun merchantPosOwnerDashboard(raw: JSONObject): PosOwnerDashboard {
    val hourlyRows = raw.optJSONArray("hourly_sales")
    return PosOwnerDashboard(
        dineIn = raw.optDouble("DINE_IN", 0.0),
        takeaway = raw.optDouble("TAKEAWAY", 0.0),
        delivery = raw.optDouble("DELIVERY", 0.0),
        gp = raw.optDouble("GP", 0.0),
        orders = raw.optInt("orders", 0),
        averageTicket = raw.optDouble("average_ticket", 0.0),
        cashSales = raw.optDouble("cash_sales", 0.0),
        discounts = raw.optDouble("discounts", 0.0),
        refunds = raw.optDouble("refunds", 0.0),
        voids = raw.optInt("voids", 0),
        changePercent = if (raw.has("change_percent") && !raw.isNull("change_percent")) {
            raw.optDouble("change_percent")
        } else {
            null
        },
        hourlySales = if (hourlyRows == null) {
            emptyList()
        } else {
            (0 until hourlyRows.length()).mapNotNull { index ->
                hourlyRows.optJSONObject(index)?.let { row ->
                    PosHourlySalesRow(
                        hour = row.optInt("hour"),
                        bills = row.optInt("bills"),
                        sales = row.optDouble("sales", 0.0)
                    )
                }
            }
        }
    )
}

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

data class PosTaxSettings(
    val enabled: Boolean = false,
    val ratePercent: Double = 7.0,
    val pricesIncludeTax: Boolean = true,
    val registrationNumber: String? = null,
    val branchCode: String? = null,
    val invoiceAddress: String? = null
)

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
    val shopCategory: String = ""
)

class PosAccessDeniedException(message: String) : IllegalStateException(message)

internal fun merchantPosLoadError(failure: Throwable): String {
    val detail = failure.message.orEmpty().trim()
    val timedOut = failure is SocketTimeoutException ||
        detail.equals("timeout", ignoreCase = true) ||
        detail.contains("timed out", ignoreCase = true) ||
        detail.contains("socket timeout", ignoreCase = true)
    return if (timedOut) {
        "โหลดข้อมูล POS ไม่ทัน ระบบหรือเครือข่ายตอบช้า · กดโหลดใหม่เพื่อลองอีกครั้ง"
    } else {
        detail.ifBlank { "โหลด POS ไม่สำเร็จ · กดโหลดใหม่เพื่อลองอีกครั้ง" }
    }
}

internal fun merchantPosIsReadFailureMessage(message: String?): Boolean {
    val value = message?.trim().orEmpty()
    return value.equals("timeout", ignoreCase = true) ||
        value.startsWith("โหลดข้อมูล POS ไม่ทัน") ||
        value.startsWith("โหลด POS ไม่สำเร็จ")
}

private data class PosSnapshotReadSet(
    val owner: Any,
    val profile: JSONArray,
    val tables: JSONArray,
    val products: JSONArray,
    val bills: JSONArray,
    val openBills: JSONArray,
    val staff: JSONArray
)

class MerchantPosApi(
    private val pendingStore: MerchantPosPendingStore,
    private val http: QueueGoNativeApi = QueueGoNativeApi()
) {
    suspend fun pendingEdit(auth: NativeAuth): PosEditIntent? = withContext(Dispatchers.IO) {
        pendingStore.load(auth.session.authUserId)
    }

    private suspend fun requireNoPendingEdit(auth: NativeAuth) {
        check(pendingEdit(auth) == null) { "กรุณากดตรวจรายการ POS ค้างก่อนทำรายการอื่น" }
    }

    suspend fun retryPendingEdit(auth: NativeAuth): String {
        val pending = pendingEdit(auth) ?: error("ไม่พบรายการ POS ค้าง")
        return submitPendingEdit(auth, pending)
    }

    private suspend fun submitPendingEdit(auth: NativeAuth, intent: PosEditIntent): String {
        val pending = withContext(Dispatchers.IO) {
            pendingStore.prepare(auth.session.authUserId, intent)
        }
        val raw = try {
            if (pending.optionsJson != null) {
                http.rpc("pos_add_product_with_options", auth.session.accessToken, pending.optionBody())
            } else if (pending.billId == null) {
                val sessionResult = http.rpc("check_active_session", auth.session.accessToken,
                    JSONObject().put("p_session_id", auth.session.sessionId))
                val active = when (sessionResult) {
                    is Boolean -> sessionResult
                    is JSONObject -> sessionResult.opt("check_active_session")
                    is JSONArray -> sessionResult.opt(0).let { first ->
                        if (first is JSONObject) first.opt("check_active_session") else first
                    }
                    else -> null
                }
                if (active != true) throw PosAccessDeniedException("Session POS ไม่พร้อมใช้งาน กรุณาเข้าสู่ระบบใหม่")
                http.rpc("pos_create_bill_once", auth.session.accessToken, JSONObject()
                    .put("p_request", pending.requestId).put("p_type", pending.type)
                    .put("p_table", pending.tableId ?: JSONObject.NULL)
                    .put("p_product", pending.productId).put("p_note", pending.note))
            } else http.rpc("pos_edit_bill_once", auth.session.accessToken, pending.body()
                .put("p_session_id", auth.session.sessionId))
        } catch (failure: com.queuego.shared.QueueGoHttpException) {
            // These errors originate in pos_edit_bill after the replay ledger lookup.
            // They prove the request did not commit. Auth/ownership/transport failures keep the key.
            val rejectedEdit = listOf("product unavailable", "bill not editable", "invalid kitchen state",
                "bill identity cannot change", "sent items cannot be reduced", "cannot reduce this line")
            if (failure.statusCode == 400 && rejectedEdit.any { failure.message.orEmpty().contains(it) }) {
                withContext(Dispatchers.IO) {
                    pendingStore.clear(auth.session.authUserId, pending.requestId)
                }
            }
            throw failure
        }
        val id = merchantPosReplayOrderId(raw, pending.billId)
        withContext(Dispatchers.IO) {
            pendingStore.clear(auth.session.authUserId, pending.requestId)
        }
        return id
    }

    suspend fun snapshot(auth: NativeAuth): PosSnapshot {
        val rawShop = http.rpc("pos_my_shop", auth.session.accessToken, JSONObject())
        val shopId = scalarText(rawShop)
        if (shopId.isBlank() || shopId == "null") {
            throw PosAccessDeniedException("บัญชีนี้ยังไม่มีสิทธิ์ POS ของร้าน")
        }

        // These reads are independent after the server resolves the active shop. Fetching them
        // concurrently prevents one POS refresh from waiting through a long chain of HTTP calls.
        val reads = coroutineScope {
            val owner = async { http.rpc("pos_is_owner", auth.session.accessToken, JSONObject()) }
            val profile = async { http.array(http.get(
                "shop_profiles?select=shop_name,public_category,metadata&id=eq." + http.enc(shopId) + "&limit=1",
                auth.session.accessToken
            )) }
            val tables = async { http.array(http.get(
                "pos_tables?select=id,label,active,qr_token&shop_id=eq." + http.enc(shopId) + "&order=label.asc",
                auth.session.accessToken
            )) }
            val products = async {
                val scope = "&shop_id=eq." + http.enc(shopId) + "&order=name.asc"
                val base = "products?select=id,name,category,price,pos_price,delivery_price,image,available,pos_available," +
                    "delivery_available,variants,stock,"
                try {
                    http.array(http.get(
                        base + "inventory_stock_tracking,grocery_master_id,grocery_stock_tracking" + scope,
                        auth.session.accessToken
                    ))
                } catch (error: QueueGoHttpException) {
                    if (error.statusCode != 400 ||
                        !error.message.orEmpty().contains("inventory_stock_tracking", ignoreCase = true)
                    ) throw error
                    http.array(http.get(
                        base + "grocery_master_id,grocery_stock_tracking" + scope,
                        auth.session.accessToken
                    ))
                }
            }
            val billColumns = "select=id,order_number,order_type,table_id,staff_id,status,kitchen_status,payment_status," +
                "payment_method,bill_status,cash_tendered,cash_change,paid_at,subtotal,total_amount,discount_amount,created_at," +
                "pos_tax_enabled_snapshot,pos_tax_rate_snapshot,pos_prices_include_tax_snapshot,pos_taxable_base,pos_tax_amount," +
                "pos_tax_registration_snapshot,pos_tax_branch_snapshot,pos_tax_address_snapshot"
            val bills = async { http.array(http.get(
                "orders?$billColumns&shop_id=eq." + http.enc(shopId) +
                    "&sales_channel=eq.POS&order=created_at.desc&limit=200",
                auth.session.accessToken
            )) }
            val openBills = async { http.array(http.get(
                "orders?$billColumns&shop_id=eq." + http.enc(shopId) +
                    "&sales_channel=eq.POS&payment_status=eq.UNPAID&status=neq.cancelled&order=created_at.desc",
                auth.session.accessToken
            )) }
            val staff = async { http.array(http.get(
                "pos_staff?select=user_id,shop_id,display_name,staff_role,permissions,active" +
                    "&shop_id=eq." + http.enc(shopId),
                auth.session.accessToken
            )) }
            PosSnapshotReadSet(
                owner = owner.await(),
                profile = profile.await(),
                tables = tables.await(),
                products = products.await(),
                bills = bills.await(),
                openBills = openBills.await(),
                staff = staff.await()
            )
        }
        val owner = scalarBoolean(reads.owner, "pos_is_owner")
        val profileRaw = reads.profile
        val tablesRaw = reads.tables
        val productsRaw = reads.products
        val billsRaw = reads.bills
        val openBillsRaw = reads.openBills
        val staffRaw = reads.staff
        val profile = profileRaw.optJSONObject(0)
        val shopName = profile?.optString("shop_name")?.takeIf { it.isNotBlank() } ?: "ร้านค้า"
        val shopCategory = profile?.optString("public_category")?.takeIf { it.isNotBlank() }
            ?: profile?.optJSONObject("metadata")?.optString("category")?.orEmpty()
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
        val billIds = idsOf(mergedBillsRaw)
        val itemsRaw = if (billIds.isEmpty()) JSONArray() else http.array(http.get(
            "order_items?select=order_id,product_id,item_name,description,quantity,total_price,pos_kitchen_status,pos_batch" +
                "&order_id=in.(" + billIds.joinToString(",") + ")&order=created_at.asc",
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
                        } else null,
                        stock = r.optInt("stock", 0),
                        inventoryStockTracking = r.optBoolean("inventory_stock_tracking", false),
                        groceryMasterId = nullableString(r, "grocery_master_id"),
                        groceryStockTracking = r.optBoolean("grocery_stock_tracking", false),
                        optionGroups = parsePosProductOptionGroups(r.optJSONArray("variants") ?: JSONArray()),
                        category = nullableString(r, "category")?.trim()?.takeIf { it.isNotEmpty() }
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
            shopCategory = shopCategory?.trim()?.lowercase().orEmpty()
        )
    }

    suspend fun addProduct(
        auth: NativeAuth,
        currentBillId: String?,
        type: String,
        tableId: String?,
        productId: String,
        note: String,
        requestId: String = UUID.randomUUID().toString(),
        optionsJson: String? = null
    ): String {
        return submitPendingEdit(auth, PosEditIntent(requestId, currentBillId?.takeIf { it.isNotBlank() },
            type, tableId, productId, 1, note.trim(), optionsJson))
    }

    suspend fun submitOfflineDraft(auth: NativeAuth, draft: PosOfflineDraft): String {
        requireNoPendingEdit(auth)
        require(draft.actorId == auth.session.authUserId) { "บัญชีที่ใช้งานไม่ตรงกับร่าง POS" }
        require(draft.items.all { it.requestIds.size == it.quantity }) { "กรุณาบันทึก request ID ของร่างก่อนส่ง" }
        val items = JSONArray().apply {
            draft.items.forEach { item ->
                put(JSONObject().put("product_id", item.productId).put("quantity", item.quantity)
                    .put("options", JSONObject(item.optionsJson)).put("request_ids", JSONArray(item.requestIds)))
            }
        }
        val raw = http.rpc("pos_submit_offline_draft", auth.session.accessToken,
            JSONObject().put("p_request", draft.requestId).put("p_type", draft.type)
                .put("p_table", draft.tableId ?: JSONObject.NULL).put("p_items", items))
        return merchantPosReplayOrderId(raw, null)
    }

    suspend fun reduceProduct(
        auth: NativeAuth,
        billId: String,
        type: String,
        tableId: String?,
        productId: String,
        note: String?
    ) {
        submitPendingEdit(auth, PosEditIntent(UUID.randomUUID().toString(), billId, type,
            tableId, productId, -1, note.orEmpty().trim()))
    }

    suspend fun billAction(auth: NativeAuth, billId: String, action: String) {
        requireNoPendingEdit(auth)
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
        requireNoPendingEdit(auth)
        require(reason.trim().length in 3..500) { "กรุณาระบุเหตุผลที่ยกเลิกอย่างน้อย 3 ตัวอักษร" }
        http.rpc(
            "pos_cancel_bill",
            auth.session.accessToken,
            JSONObject().put("p_order", billId).put("p_reason", reason.trim())
        )
    }

    // Production cash-register RPCs are cashier-scoped and server-authoritative.
    // Never manufacture an open shift from a local cache or auto-open one at checkout.
    suspend fun currentCashShift(auth: NativeAuth): PosCashShift =
        parseCashShift(http.rpc("pos_current_shift", auth.session.accessToken, JSONObject()))

    suspend fun shiftHistory(auth: NativeAuth, limit: Int = 20): List<PosShiftHistory> {
        val raw = http.rpc(
            "pos_register_shift_history", auth.session.accessToken,
            JSONObject().put("p_limit", limit.coerceIn(1, 50))
        )
        val rows = when (raw) {
            is JSONArray -> raw
            is JSONObject -> raw.optJSONArray("pos_register_shift_history")
                ?: raw.optJSONArray("history") ?: JSONArray()
            is String -> runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
            else -> JSONArray()
        }
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val id = row.optString("id")
                if (id.isBlank()) continue
                add(PosShiftHistory(
                    id = id,
                    cashierName = row.optString("cashier_name").ifBlank { "แคชเชียร์" },
                    openedAt = row.optString("opened_at"),
                    closedAt = nullableString(row, "closed_at"),
                    openingFloat = row.optDouble("opening_float", 0.0),
                    expectedCash = nullableDouble(row, "expected_cash"),
                    countedCash = nullableDouble(row, "counted_cash"),
                    variance = nullableDouble(row, "variance")
                ))
            }
        }
    }

    suspend fun openCashShift(auth: NativeAuth, openingFloat: Double): PosCashShift {
        require(openingFloat.isFinite() && openingFloat in 0.0..999999999.0) {
            "เงินสดต้นกะไม่ถูกต้อง"
        }
        val result = parseCashShift(http.rpc(
            "pos_open_shift", auth.session.accessToken,
            JSONObject().put("p_opening_float", openingFloat).put("p_note", "")
        ))
        require(result.open) { "Server ยังไม่ยืนยันการเปิดกะ" }
        return result
    }

    suspend fun closeCashShift(auth: NativeAuth, countedCash: Double): PosCashShift {
        require(countedCash.isFinite() && countedCash in 0.0..999999999.0) {
            "ยอดเงินสดที่นับไม่ถูกต้อง"
        }
        val result = parseCashShift(http.rpc(
            "pos_close_shift", auth.session.accessToken,
            JSONObject().put("p_counted_cash", countedCash).put("p_note", "")
        ))
        require(!result.open) { "Server ยังไม่ยืนยันการปิดกะ" }
        return result
    }

    suspend fun recordCashMovement(
        auth: NativeAuth,
        type: String,
        amount: Double,
        reason: String,
        requestId: String
    ): String {
        requireNoPendingEdit(auth)
        require(type == "cash_in" || type == "cash_out") { "ประเภทเงินเข้าออกไม่ถูกต้อง" }
        require(amount.isFinite() && amount in 0.01..999999999.0 &&
            kotlin.math.abs(amount * 100 - kotlin.math.round(amount * 100)) < 0.00001) {
            "จำนวนเงินต้องมากกว่า 0 และไม่เกิน 2 ตำแหน่งทศนิยม"
        }
        require(reason.trim().length in 5..500) { "กรุณาระบุเหตุผลอย่างน้อย 5 ตัวอักษร" }
        require(runCatching { UUID.fromString(requestId) }.isSuccess) { "รหัสรายการไม่ถูกต้อง" }
        val receipt = objectValue(http.rpc(
            "pos_record_cash_movement",
            auth.session.accessToken,
            JSONObject().put("p_type", type).put("p_amount", amount)
                .put("p_reason", reason.trim()).put("p_request", requestId)
        ))
        val eventId = receipt.optString("id")
        val recordedAmount = receipt.optDouble("amount", Double.NaN)
        val signed = if (type == "cash_out") -amount else amount
        check(eventId.isNotBlank() && receipt.optString("event_type") == type &&
            receipt.optString("reason") == reason.trim() &&
            recordedAmount.isFinite() && kotlin.math.abs(recordedAmount - signed) < 0.001) {
            "Server ยังไม่ยืนยันผลเงินเข้าออก กรุณาตรวจรายการเดิม"
        }
        return eventId
    }

    private fun parseCashShift(raw: Any): PosCashShift {
        val value = objectValue(raw)
        require(value.has("open")) { "Server ไม่ส่งสถานะกะรับเงิน" }
        return PosCashShift(
            open = value.optBoolean("open"),
            expectedCash = nullableDouble(value, "expected_cash"),
            openingFloat = nullableDouble(value, "opening_float"),
            cashierName = nullableString(value, "cashier_name"),
            cashInTotal = nullableDouble(value, "cash_in_total"),
            cashOutTotal = nullableDouble(value, "cash_out_total"),
            id = nullableString(value, "id")
        )
    }

    suspend fun takePayment(
        auth: NativeAuth,
        billId: String,
        method: String,
        cashReceived: Double? = null
    ): String {
        requireNoPendingEdit(auth)
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
        check(posPaymentReceiptMatches(objectValue(raw), billId, method, cashReceived)) {
            "Server ยังไม่ยืนยันการชำระเงิน กรุณาตรวจสถานะบิลเดิมก่อนกดรับเงินซ้ำ"
        }
        return billId
    }

    // Read only. An RPC timeout can occur AFTER the server committed the cash payment.
    // Reconcile the exact request against the authoritative paid order, never send another charge.
    suspend fun paymentConfirmed(
        auth: NativeAuth, billId: String, method: String, cashReceived: Double?
    ): Boolean {
        val rows = http.array(http.get(
            "orders?select=id,sales_channel,payment_status,bill_status,payment_method,cash_tendered" +
                "&id=eq." + http.enc(billId) + "&sales_channel=eq.POS&limit=1",
            auth.session.accessToken
        ))
        return posPaymentReceiptMatches(rows.optJSONObject(0), billId, method, cashReceived)
    }

    suspend fun takeCash(auth: NativeAuth, billId: String, received: Double): String =
        takePayment(auth, billId, "cash", received)

    suspend fun applyDiscount(auth: NativeAuth, billId: String, amount: Double) {
        requireNoPendingEdit(auth)
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

    // Reuse the same shop-scoped, permission-checked POS product code RPCs as Web.
    suspend fun lookupProductCode(auth: NativeAuth, rawCode: String): PosCodeLookup? {
        val code = rawCode.trim().uppercase(java.util.Locale.ROOT)
        require(code.length in 1..64) { "รหัสสินค้าต้องยาว 1–64 ตัวอักษร" }
        val raw = http.rpc(
            "pos_product_code_lookup", auth.session.accessToken,
            JSONObject().put("p_code", code)
        )
        if (raw == JSONObject.NULL) return null
        val obj = objectValue(raw)
        if (obj.length() == 0) return null
        val productId = obj.optString("product_id")
        require(productId.isNotBlank() && obj.optString("code") == code) {
            "Server ส่งรหัสสินค้าไม่ตรงกับที่ค้นหา"
        }
        return PosCodeLookup(
            productId = productId,
            name = obj.optString("name"),
            available = obj.optBoolean("available", false),
            posAvailable = obj.optBoolean("pos_available", false)
        )
    }

    suspend fun productCodes(auth: NativeAuth): List<PosProductCode> {
        val raw = http.rpc("pos_product_codes_for_shop", auth.session.accessToken, JSONObject())
        val arr = when (raw) {
            is JSONArray -> raw
            is String -> JSONArray(raw)
            is JSONObject -> raw.optJSONArray("pos_product_codes_for_shop")
                ?: raw.optJSONArray("data")
                ?: error("Server ส่งข้อมูลรหัสสินค้าไม่ถูกต้อง")
            else -> error("Server ส่งข้อมูลรหัสสินค้าไม่ถูกต้อง")
        }
        return buildList {
            for (index in 0 until arr.length()) {
                val row = arr.optJSONObject(index) ?: error("ข้อมูลรหัสสินค้าผิดรูปแบบ")
                val id = row.optString("id")
                val product = row.optString("product_id")
                require(id.isNotBlank() && product.isNotBlank()) { "รหัสสินค้าจาก Server ไม่ครบ" }
                add(PosProductCode(
                    id = id, productId = product,
                    productName = row.optString("product_name"),
                    type = row.optString("code_type"),
                    code = row.optString("code")
                ))
            }
        }
    }

    suspend fun saveProductCode(auth: NativeAuth, productId: String, type: String, code: String): String {
        requireNoPendingEdit(auth)
        val value = code.trim().uppercase(java.util.Locale.ROOT)
        require(type in setOf("barcode", "sku")) { "ประเภทรหัสสินค้าไม่ถูกต้อง" }
        require(value.length in 1..64 &&
            (if (type == "barcode") value.matches(Regex("[0-9]{8,14}"))
            else value.matches(Regex("[A-Z0-9][A-Z0-9._/-]{0,63}")))) {
            "บาร์โค้ดต้องเป็นเลข 8–14 หลัก หรือ SKU ใช้อักษรอังกฤษ ตัวเลข . _ / -"
        }
        val raw = http.rpc(
            "pos_product_code_save", auth.session.accessToken,
            JSONObject().put("p_product", productId)
                .put("p_code_type", type).put("p_code", value)
        )
        val result = objectValue(raw)
        val id = result.optString("id")
        check(id.isNotBlank() &&
            result.optString("product_id") == productId &&
            result.optString("code_type") == type &&
            result.optString("code") == value) {
            "Server ยังไม่ยืนยันบันทึกรหัสสินค้า กรุณาตรวจรายการเดิม"
        }
        return id
    }

    suspend fun removeProductCode(auth: NativeAuth, id: String) {
        requireNoPendingEdit(auth)
        val raw = http.rpc(
            "pos_product_code_remove", auth.session.accessToken,
            JSONObject().put("p_id", id)
        )
        val success = when (raw) {
            is Boolean -> raw
            is JSONObject -> raw.optBoolean("pos_product_code_remove", false)
            is JSONArray -> raw.optBoolean(0, false)
            else -> raw.toString() == "true"
        }
        check(success) { "Server ยังไม่ยืนยันการลบรหัสสินค้า กรุณาโหลดรายการใหม่" }
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

    suspend fun loadTaxSettings(auth: NativeAuth): PosTaxSettings {
        val raw = http.get(
            "pos_tax_settings?select=tax_enabled,tax_rate,prices_include_tax,tax_registration_number,tax_branch_code,tax_invoice_address&shop_id=eq." +
                http.enc(http.rpc("pos_my_shop", auth.session.accessToken, JSONObject()).toString().trim('"')) +
                "&limit=1",
            auth.session.accessToken
        )
        val rows = http.array(raw)
        val row = rows.optJSONObject(0) ?: return PosTaxSettings()
        return PosTaxSettings(
            enabled = row.optBoolean("tax_enabled", false),
            ratePercent = row.optDouble("tax_rate", 7.0),
            pricesIncludeTax = row.optBoolean("prices_include_tax", true),
            registrationNumber = row.optString("tax_registration_number").takeUnless { it == "null" },
            branchCode = row.optString("tax_branch_code").takeUnless { it == "null" },
            invoiceAddress = row.optString("tax_invoice_address").takeUnless { it == "null" }
        )
    }

    suspend fun saveTaxSettings(auth: NativeAuth, settings: PosTaxSettings) {
        require(settings.ratePercent.isFinite() && settings.ratePercent in 0.0..100.0) { "อัตราภาษีต้องอยู่ระหว่าง 0–100%" }
        require(settings.registrationNumber.orEmpty().length <= 30) { "เลขประจำตัวผู้เสียภาษียาวเกินไป" }
        require(settings.branchCode.orEmpty().length <= 20) { "รหัสสาขายาวเกินไป" }
        require(settings.invoiceAddress.orEmpty().length <= 1000) { "ที่อยู่ยาวเกินไป" }
        http.rpc(
            "pos_save_tax_settings",
            auth.session.accessToken,
            JSONObject()
                .put("p_tax_enabled", settings.enabled)
                .put("p_tax_rate", settings.ratePercent)
                .put("p_prices_include_tax", settings.pricesIncludeTax)
                .put("p_registration_number", settings.registrationNumber ?: JSONObject.NULL)
                .put("p_branch_code", settings.branchCode ?: JSONObject.NULL)
                .put("p_invoice_address", settings.invoiceAddress ?: JSONObject.NULL)
        )
    }

    suspend fun ownerDashboard(auth: NativeAuth, days: Int): PosOwnerDashboard {
        require(days in setOf(1, 7, 30)) { "ช่วงรายงานไม่ถูกต้อง" }
        val raw = http.rpc(
            "pos_owner_dashboard",
            auth.session.accessToken,
            JSONObject().put("p_days", days)
        )
        return merchantPosOwnerDashboard(objectValue(raw))
    }

    suspend fun ownerItemSales(auth: NativeAuth, days: Int): PosItemSalesReport {
        require(days in setOf(1, 7, 30)) { "ช่วงรายงานไม่ถูกต้อง" }
        val raw = objectValue(http.rpc(
            "pos_owner_item_sales",
            auth.session.accessToken,
            JSONObject().put("p_days", days)
        ))
        val rows = raw.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                add(PosItemSalesRow(
                    name = row.optString("item_name").ifBlank { "(ไม่ระบุชื่อ)" },
                    quantity = row.optDouble("quantity", 0.0),
                    bills = row.optInt("bills", 0),
                    sales = row.optDouble("sales", 0.0)
                ))
            }
        }
        return PosItemSalesReport(
            itemCount = raw.optInt("item_count", items.size),
            items = items
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
                "&sales_channel=eq.POS&created_at=gte." + http.enc(startIso) +
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
        staffId = nullableString(r, "staff_id"),
        taxEnabledSnapshot = r.optBoolean("pos_tax_enabled_snapshot", false),
        taxRateSnapshot = nullableDouble(r, "pos_tax_rate_snapshot"),
        pricesIncludeTaxSnapshot = if (r.has("pos_prices_include_tax_snapshot") && !r.isNull("pos_prices_include_tax_snapshot")) r.optBoolean("pos_prices_include_tax_snapshot") else null,
        taxableBase = nullableDouble(r, "pos_taxable_base"),
        taxAmount = nullableDouble(r, "pos_tax_amount"),
        taxRegistrationSnapshot = nullableString(r, "pos_tax_registration_snapshot"),
        taxBranchSnapshot = nullableString(r, "pos_tax_branch_snapshot"),
        taxAddressSnapshot = nullableString(r, "pos_tax_address_snapshot")
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


// Tax math foundation: operate on integer satang to avoid floating-point drift.
// Persisted shop settings and server-side checkout integration are required before
// this helper may be used to change a real bill total.
internal data class PosTaxBreakdown(
    val taxableBaseSatang: Long,
    val taxSatang: Long,
    val totalSatang: Long
)

internal fun calculatePosTaxBreakdown(
    amountSatang: Long,
    ratePercent: Double,
    pricesIncludeTax: Boolean
): PosTaxBreakdown {
    require(amountSatang >= 0L) { "Taxable amount cannot be negative" }
    require(ratePercent.isFinite() && ratePercent in 0.0..100.0) { "Tax rate must be between 0 and 100" }

    val rate = ratePercent / 100.0
    return if (pricesIncludeTax) {
        val tax = kotlin.math.round(amountSatang.toDouble() * rate / (1.0 + rate)).toLong()
        PosTaxBreakdown(
            taxableBaseSatang = amountSatang - tax,
            taxSatang = tax,
            totalSatang = amountSatang
        )
    } else {
        val tax = kotlin.math.round(amountSatang.toDouble() * rate).toLong()
        PosTaxBreakdown(
            taxableBaseSatang = amountSatang,
            taxSatang = tax,
            totalSatang = Math.addExact(amountSatang, tax)
        )
    }
}
