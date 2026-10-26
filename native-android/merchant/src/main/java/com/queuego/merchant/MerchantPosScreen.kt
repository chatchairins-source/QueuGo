package com.queuego.merchant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.queuego.shared.NativeAuth
import com.queuego.shared.NativeOrderRealtime
import com.queuego.shared.NativePrintQueue
import com.queuego.shared.QueueGoHttpException
import com.queuego.shared.NativeRealtimeSubscription
import com.queuego.shared.QgCard
import com.queuego.shared.QgGreen
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import com.queuego.shared.QgSectionTitle
import com.queuego.shared.QgStatusPill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

private val posRoles = listOf(
    "WAITER" to "พนักงานเสิร์ฟ",
    "CASHIER" to "แคชเชียร์",
    "KITCHEN" to "ครัว"
)

private val posPaymentMethods = listOf(
    "cash" to "เงินสด",
    "bank_transfer" to "โอนเงิน",
    "promptpay" to "พร้อมเพย์",
    "card" to "บัตร",
    "other" to "อื่น ๆ"
)

@Composable
fun MerchantPosScreen(
    auth: NativeAuth,
    onBack: () -> Unit,
    backLabel: String = "ย้อนกลับ"
) {
    val context = LocalContext.current
    val api = remember { MerchantPosApi(MerchantPosPendingStore(context.noBackupFilesDir.resolve("queuego-pos-edit-pending"))) }
    val offlineDraftStore = remember { MerchantPosOfflineDraftStore(context.noBackupFilesDir.resolve("queuego-pos-offline-drafts")) }
    val realtime = remember { NativeOrderRealtime() }
    val refreshMutex = remember { Mutex() }
    val printQueue = remember { NativePrintQueue() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val printerStore = remember { MerchantPrinterStore(context) }
    val printBridge = remember { MerchantPrintBridge() }

    var snapshot by remember { mutableStateOf<PosSnapshot?>(null) }
    var snapshotActorId by remember { mutableStateOf<String?>(null) }
    var snapshotLoading by remember { mutableStateOf(true) }
    var selectedBillId by rememberSaveable { mutableStateOf<String?>(null) }
    var view by rememberSaveable { mutableStateOf("counter") }
    var mode by rememberSaveable { mutableStateOf("TAKEAWAY") }
    var tableId by rememberSaveable { mutableStateOf<String?>(null) }
    var noTable by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingEdit by remember { mutableStateOf<PosEditIntent?>(null) }
    var pendingEditError by remember { mutableStateOf<String?>(null) }
    var offlineDraft by remember { mutableStateOf<PosOfflineDraft?>(null) }
    var draftDeviceId by remember { mutableStateOf<String?>(null) }
    var offlineCatalogOnly by remember { mutableStateOf(false) }
    var networkValidated by remember { mutableStateOf(false) }
    var confirmDraftSubmit by remember { mutableStateOf(false) }
    var confirmDraftDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(auth.session.authUserId, busy) {
        if (!busy) {
            try {
                pendingEdit = api.pendingEdit(auth)
                pendingEditError = null
            } catch (failure: Exception) {
                pendingEditError = failure.message ?: "อ่านรายการ POS ค้างไม่สำเร็จ"
            }
        }
    }

    LaunchedEffect(auth.session.authUserId, offlineDraftStore) {
        snapshot = null
        snapshotActorId = null
        offlineCatalogOnly = false
        selectedBillId = null
        offlineDraft = null
        draftDeviceId = null
        try {
            val device = withContext(Dispatchers.IO) { offlineDraftStore.deviceId() }
            draftDeviceId = device
            offlineDraft = withContext(Dispatchers.IO) {
                offlineDraftStore.loadForActor(auth.session.authUserId, device)
            }
        } catch (failure: Exception) {
            message = failure.message ?: "อ่านร่าง POS ไม่สำเร็จ"
        }
    }

    var pendingPosRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPosProductId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPosType by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPosTableId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPosNote by rememberSaveable { mutableStateOf<String?>(null) }

    var paymentMethod by rememberSaveable { mutableStateOf("cash") }
    var paymentMethodMenu by remember { mutableStateOf(false) }
    var cashReceived by rememberSaveable { mutableStateOf("") }

    var discountOpen by remember { mutableStateOf(false) }
    var discountText by rememberSaveable { mutableStateOf("") }
    var cancelOpen by remember { mutableStateOf(false) }
    var cancelReason by rememberSaveable { mutableStateOf("") }
    var refundTarget by remember { mutableStateOf<PosBill?>(null) }
    var refundReason by rememberSaveable { mutableStateOf("") }
    var priceTarget by remember { mutableStateOf<PosProduct?>(null) }
    var priceText by rememberSaveable { mutableStateOf("") }
    var optionTarget by remember { mutableStateOf<PosProduct?>(null) }

    var tableDialog by remember { mutableStateOf(false) }
    var editingTable by remember { mutableStateOf<PosTable?>(null) }
    var tableLabel by rememberSaveable { mutableStateOf("") }
    var tableActive by rememberSaveable { mutableStateOf(true) }
    var qrTable by remember { mutableStateOf<PosTable?>(null) }

    var reportDays by rememberSaveable { mutableStateOf(1) }
    var report by remember { mutableStateOf<PosOwnerDashboard?>(null) }

    var historyDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var historyType by rememberSaveable { mutableStateOf("ALL") }
    var historyTypeMenu by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<PosHistoryRow>>(emptyList()) }


    var inviteRole by rememberSaveable { mutableStateOf("WAITER") }
    var inviteRoleMenu by remember { mutableStateOf(false) }
    var inviteSecret by rememberSaveable { mutableStateOf("") }

    var printerSettings by remember { mutableStateOf(MerchantPrinterSettings()) }
    var printerSettingsLoaded by remember { mutableStateOf(false) }
    var printerBaseline by remember { mutableStateOf<Map<String, Pair<String?, String?>>?>(null) }
    var pendingPrintCount by remember { mutableStateOf(0) }
    val printBusy = pendingPrintCount > 0

    LaunchedEffect(printerStore) {
        printerSettings = withContext(Dispatchers.IO) { printerStore.load() }
        printerSettingsLoaded = true
    }

    fun can(permission: String): Boolean {
        val s = snapshot ?: return false
        return s.owner || s.currentStaff?.allows(permission) == true
    }

    fun sendToPrinter(
        settings: MerchantPrinterSettings,
        type: String,
        text: String,
        printedKey: String? = null,
        billId: String? = null,
        force: Boolean = false,
        successMessage: String
    ) {
        val snap = snapshot ?: return
        // Manual double taps are rejected synchronously; automatic jobs queue instead of disappearing.
        if ((force || type == "test") && printBusy) return
        pendingPrintCount++
        scope.launch {
            try {
                val sent = printQueue.submit(
                    key = printedKey,
                    force = force,
                    wasPrinted = { key ->
                        withContext(Dispatchers.IO) { printerStore.wasPrinted(key) }
                    },
                    markPrinted = printerStore::markPrinted
                ) {
                    // Refresh through the authenticated Production API before sending shop data externally.
                    val fresh = api.snapshot(auth)
                    require(fresh.shopId == snap.shopId) { "สิทธิ์ร้านเปลี่ยนแล้ว กรุณาโหลด POS ใหม่" }
                    val ticket = if (billId == null) text else {
                        val bill = fresh.bills.firstOrNull { it.id == billId }
                            ?: error("ไม่พบบิล กรุณาโหลด POS ใหม่")
                        val currentKey = if (type == "kitchen") merchantKitchenPrintKey(fresh, bill)
                            else merchantReceiptPrintKey(fresh, bill)
                        require(currentKey != null) { "สถานะบิลเปลี่ยนแล้ว กรุณาโหลด POS ใหม่ก่อนพิมพ์" }
                        if (type == "kitchen") {
                            val prefix = "kitchen:" + fresh.shopId + ":" + bill.id + ":"
                            val batch = printedKey?.takeIf { it.startsWith(prefix) }
                                ?.removePrefix(prefix)?.toIntOrNull()
                            require(batch != null && fresh.linesByOrder[bill.id].orEmpty().any { it.batch == batch }) {
                                "ไม่พบชุดครัวที่ต้องการพิมพ์ กรุณาโหลด POS ใหม่"
                            }
                            merchantKitchenTicket(fresh, bill, batch)
                        } else {
                            require(currentKey == printedKey) { "บิลเปลี่ยนแล้ว กรุณาโหลด POS ใหม่" }
                            merchantReceiptTicket(fresh, bill)
                        }
                    }
                    printBridge.print(settings, type, ticket, fresh.shopId, fresh.shopName)
                }
                if (sent) message = successMessage
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                message = (failure.message ?: "พิมพ์ไม่สำเร็จ") +
                    " · ตรวจเครื่องพิมพ์ก่อนกดพิมพ์ซ้ำ ระบบไม่ส่งซ้ำอัตโนมัติเมื่อผลไม่แน่นอน"
            } finally {
                pendingPrintCount--
            }
        }
    }

    suspend fun refreshSnapshot(select: String? = selectedBillId) = refreshMutex.withLock {
        networkValidated = posNetworkValidated(context)
        if (snapshot == null) snapshotLoading = true
        try {
            val fresh = api.snapshot(auth)
            snapshot = fresh
            snapshotActorId = auth.session.authUserId
            if (merchantPosIsReadFailureMessage(message)) message = null
            offlineCatalogOnly = false
            draftDeviceId?.let { device -> runCatching {
                withContext(Dispatchers.IO) {
                    offlineDraftStore.saveCatalog(PosOfflineCatalog(auth.session.authUserId, fresh.shopId,
                        device, fresh.shopName, System.currentTimeMillis(), fresh.products, fresh.tables))
                }
            } }
            val device = draftDeviceId
            if (device != null) {
                val savedDraft = withContext(Dispatchers.IO) {
                    offlineDraftStore.load(PosOfflineDraftScope(auth.session.authUserId, fresh.shopId, device))
                }
                offlineDraft = savedDraft?.takeIf { it.shopId == fresh.shopId }
                if (savedDraft != null && savedDraft.shopId != fresh.shopId) {
                    message = "ร่าง POS อยู่คนละร้านกับบัญชีปัจจุบัน จึงยังเปิดหรือส่งไม่ได้"
                }
            }
            selectedBillId = select?.takeIf { id -> fresh.bills.any { it.id == id && it.open } }
            if (selectedBillId == null && mode == "DINE_IN" && tableId != null) {
                selectedBillId = fresh.bills.firstOrNull { it.open && it.tableId == tableId }?.id
            }
            qrTable = qrTable?.let { old -> fresh.tables.firstOrNull { it.id == old.id } }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure is PosAccessDeniedException ||
                (failure is QueueGoHttpException && failure.statusCode in setOf(401, 403))) {
                snapshot = null
                snapshotActorId = null
                offlineCatalogOnly = false
                qrTable = null
            } else if (snapshot == null && draftDeviceId != null) {
                val cached = runCatching {
                    withContext(Dispatchers.IO) {
                        offlineDraftStore.loadCatalog(auth.session.authUserId, draftDeviceId!!)
                    }
                }.getOrNull()
                if (cached != null) {
                    snapshot = PosSnapshot(shopId = cached.shopId, shopName = cached.shopName, owner = false,
                        currentStaff = null, staff = emptyList(), products = cached.products,
                        tables = cached.tables, bills = emptyList(), linesByOrder = emptyMap())
                    snapshotActorId = auth.session.authUserId
                    offlineCatalogOnly = true
                    selectedBillId = null
                    offlineDraft = runCatching {
                        withContext(Dispatchers.IO) {
                            offlineDraftStore.load(PosOfflineDraftScope(auth.session.authUserId, cached.shopId, draftDeviceId!!))
                        }
                    }.getOrNull()
                    message = "ออฟไลน์ · ใช้แคตตาล็อกที่บันทึกไว้สร้างร่างเท่านั้น · ยังไม่สร้างบิลหรือรับเงิน"
                } else message = merchantPosLoadError(failure)
            } else {
                networkValidated = posNetworkValidated(context)
                message = merchantPosLoadError(failure)
            }
        } finally {
            snapshotLoading = false
        }
    }

    fun reload(select: String? = selectedBillId) {
        scope.launch { refreshSnapshot(select) }
    }

    fun runMutation(
        success: String,
        select: String? = selectedBillId,
        block: suspend () -> Unit
    ) {
        if (busy) return
        if (pendingEdit != null || pendingEditError != null) {
            message = "กรุณากดตรวจรายการ POS ค้างก่อนทำรายการอื่น"
            return
        }
        busy = true
        message = null
        scope.launch {
            try {
                block()
                message = success
                refreshSnapshot(select)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                message = failure.message ?: "บันทึกไม่สำเร็จ"
            } finally {
                busy = false
            }
        }
    }

    fun addPosProduct(product: PosProduct, optionsJson: String? = null) {
        if (busy) return
        val online = posNetworkValidated(context)
        if (online && !can("receive_order")) {
            message = "บัญชีนี้ไม่มีสิทธิ์รับออเดอร์"
            return
        }
        if (optionsJson == null && product.optionGroups.isNotEmpty()) {
            optionTarget = product
            return
        }
        if (mode == "DINE_IN" && tableId == null && !noTable) {
            message = "เลือกโต๊ะหรือไม่ระบุโต๊ะก่อน"
            return
        }

        if (!online || offlineDraft != null || offlineCatalogOnly) {
            val snap = snapshot
            val device = draftDeviceId
            if (snap == null || snapshotActorId != auth.session.authUserId || device == null) {
                message = "ออฟไลน์: โหลด POS ออนไลน์หนึ่งครั้งก่อน จึงจะบันทึกร่างสินค้าได้"
                return
            }
            if (selectedBillId != null) {
                message = "ออกจากบิลจริงก่อนเพิ่มสินค้าในร่างออฟไลน์"
                return
            }
            val scopeKey = PosOfflineDraftScope(auth.session.authUserId, snap.shopId, device)
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val existing = offlineDraftStore.load(scopeKey)
                        if (existing?.items?.any { it.requestIds.isNotEmpty() } == true) {
                            error("ร่างนี้เคยส่งแล้วและยังไม่ทราบผล กรุณาออนไลน์เพื่อตรวจด้วยรหัสเดิมก่อนแก้ไข")
                        }
                        val targetType = mode
                        val targetTable = if (targetType == "DINE_IN") tableId else null
                        if (existing != null && (existing.type != targetType || existing.tableId != targetTable)) {
                            error("ร่างเดิมใช้ประเภทบิลหรือโต๊ะอื่น กรุณาส่งหรือลบร่างเดิมก่อน")
                        }
                        val draft = existing ?: PosOfflineDraft(
                            UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                            auth.session.authUserId, snap.shopId, device, targetType, targetTable,
                            System.currentTimeMillis(), emptyList()
                        )
                        val candidate = posOfflineDraftItem(product, optionsJson)
                        val current = draft.items.toMutableList()
                        val index = current.indexOfFirst { it.productId == candidate.productId && it.optionsJson == candidate.optionsJson }
                        if (index >= 0) current[index] = current[index].copy(quantity = current[index].quantity + 1,
                            requestIds = emptyList())
                        else current.add(candidate)
                        offlineDraftStore.save(draft.copy(items = current))
                    }
                    offlineDraft = withContext(Dispatchers.IO) {
                        offlineDraftStore.load(scopeKey)
                    }
                    message = "เก็บสินค้าไว้ในร่างออฟไลน์แล้ว · ยังไม่ได้สร้างบิลหรือรับชำระเงิน"
                } catch (failure: Exception) {
                    message = failure.message ?: "บันทึกร่างออฟไลน์ไม่สำเร็จ"
                }
            }
            return
        }

        val newBill = selectedBillId.isNullOrBlank()
        val targetTable = if (mode == "DINE_IN") tableId else null
        val requestId = if (newBill) {
            val existing = pendingPosRequestId
            if (existing != null) {
                val same = pendingPosProductId == product.id &&
                    pendingPosType == mode &&
                    pendingPosTableId == targetTable &&
                    pendingPosNote == note
                if (!same) {
                    message = "มีบิลก่อนหน้าที่ยังไม่ทราบผล กรุณาตรวจบิลเดิมก่อนเพิ่มรายการใหม่"
                    return
                }
                existing
            } else {
                UUID.randomUUID().toString().also {
                    pendingPosRequestId = it
                    pendingPosProductId = product.id
                    pendingPosType = mode
                    pendingPosTableId = targetTable
                    pendingPosNote = note
                }
            }
        } else UUID.randomUUID().toString()

        val currentBillId = selectedBillId
        val requestType = mode
        val requestNote = note
        busy = true
        scope.launch {
            try {
                val id = api.addProduct(
                    auth = auth,
                    currentBillId = currentBillId,
                    type = requestType,
                    tableId = targetTable,
                    productId = product.id,
                    note = requestNote,
                    requestId = requestId,
                    optionsJson = optionsJson
                )
                // Commit acknowledgement must not be undone by a subsequent refresh failure.
                selectedBillId = id
                pendingPosRequestId = null
                pendingPosProductId = null
                pendingPosType = null
                pendingPosTableId = null
                pendingPosNote = null
                message = "เพิ่มสินค้าแล้ว"
                refreshSnapshot(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                message = if (newBill) {
                    "ผลเปิดบิลยังไม่แน่ชัด · กดตรวจบิลเดิมก่อนทำรายการอื่น: " +
                        (failure.message ?: "เชื่อมต่อไม่สำเร็จ")
                } else failure.message ?: "เพิ่มสินค้าไม่สำเร็จ"
            } finally {
                busy = false
            }
        }
    }

    fun retryPendingPosCreate() {
        val productId = pendingPosProductId ?: return
        val product = snapshot?.products?.firstOrNull { it.id == productId }
        if (product == null) {
            message = "ไม่พบสินค้าของบิลที่รอตรวจ กรุณาโหลดข้อมูลใหม่"
            return
        }
        if (selectedBillId != null) {
            selectedBillId = null
        }
        mode = pendingPosType ?: mode
        tableId = pendingPosTableId
        noTable = mode == "DINE_IN" && tableId == null
        note = pendingPosNote.orEmpty()
        addPosProduct(product)
    }

    fun submitDraftAfterReview() {
        val draft = offlineDraft ?: return
        val snap = snapshot
        val device = draftDeviceId
        if (!posNetworkValidated(context)) {
            networkValidated = false
            message = "ต้องเชื่อมต่ออินเทอร์เน็ตก่อนส่งร่าง"
            return
        }
        if (busy || offlineCatalogOnly || snap == null || snapshotActorId != auth.session.authUserId || device == null || selectedBillId != null ||
            draft.actorId != auth.session.authUserId || draft.shopId != snap.shopId || draft.deviceId != device) {
            message = "ตรวจบัญชี ร้าน และสถานะบิลก่อนส่งร่าง"
            return
        }
        if (!can("receive_order")) {
            message = "บัญชีนี้ไม่มีสิทธิ์รับออเดอร์"
            return
        }
        if (pendingEdit != null || pendingEditError != null) {
            message = "ตรวจรายการ POS ค้างก่อนส่งร่าง"
            return
        }
        val products = draft.items.map { item -> snap.products.firstOrNull { it.id == item.productId } }
        if (products.any { it == null || !it.available }) {
            message = "มีสินค้าที่ไม่พร้อมขายในขณะนี้ กรุณาออนไลน์และตรวจร่างอีกครั้ง"
            return
        }
        try {
            draft.items.forEachIndexed { index, item -> posOfflineDraftItem(products[index]!!, item.optionsJson) }
        } catch (failure: Exception) {
            message = failure.message ?: "มีตัวเลือกสินค้าที่ไม่มีหรือไม่ครบ กรุณาตรวจร่างอีกครั้ง"
            return
        }
        if (draft.type == "DINE_IN" && draft.tableId != null && snap.tables.none { it.id == draft.tableId && it.active }) {
            message = "โต๊ะในร่างนี้ปิดใช้งานแล้ว กรุณาตรวจร่างอีกครั้ง"
            return
        }
        confirmDraftSubmit = true
    }

    fun commitReviewedDraft() {
        if (busy) return
        if (!posNetworkValidated(context)) {
            networkValidated = false
            confirmDraftSubmit = false
            message = "การเชื่อมต่อหายไป · เก็บร่างไว้และส่งซ้ำได้เมื่อออนไลน์"
            return
        }
        val draft = offlineDraft ?: return
        val snap = snapshot ?: return
        val device = draftDeviceId ?: return
        val scopeKey = PosOfflineDraftScope(auth.session.authUserId, snap.shopId, device)
        confirmDraftSubmit = false
        busy = true
        scope.launch {
            try {
                val prepared = draft.copy(items = draft.items.map { item ->
                    item.copy(requestIds = if (item.requestIds.size == item.quantity) item.requestIds
                    else List(item.quantity) { UUID.randomUUID().toString() })
                })
                withContext(Dispatchers.IO) { offlineDraftStore.save(prepared) }
                offlineDraft = prepared
                val orderId = api.submitOfflineDraft(auth, prepared)
                selectedBillId = orderId
                withContext(Dispatchers.IO) { offlineDraftStore.remove(scopeKey, prepared.id) }
                offlineDraft = null
                message = "ส่งร่างเป็นบิลจริงแล้ว · ยังไม่รับชำระเงิน"
                refreshSnapshot(orderId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Keep the same draft and request IDs after a timeout; server replay is atomic.
                if (failure is QueueGoHttpException && failure.statusCode in 400..499) {
                    val rejected = draft.copy(items = draft.items.map { it.copy(requestIds = emptyList()) })
                    runCatching { withContext(Dispatchers.IO) { offlineDraftStore.save(rejected) } }
                        .onSuccess { offlineDraft = rejected }
                    message = (failure.message ?: "เซิร์ฟเวอร์ปฏิเสธร่าง") + " · ไม่มีบิลถูกสร้าง แก้ร่างแล้วลองใหม่ได้"
                } else {
                    message = (failure.message ?: "ส่งร่างไม่สำเร็จ") + " · เก็บร่างและรหัสเดิมไว้ให้ลองใหม่"
                }
            } finally {
                busy = false
            }
        }
    }

    fun discardOfflineDraft() {
        val draft = offlineDraft ?: return
        if (draft.items.any { it.requestIds.isNotEmpty() }) {
            message = "ร่างนี้เคยส่งแล้วและยังไม่ทราบผล กรุณาออนไลน์เพื่อตรวจด้วยรหัสเดิมก่อนลบ"
            return
        }
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    offlineDraftStore.remove(PosOfflineDraftScope(draft.actorId, draft.shopId, draft.deviceId), draft.id)
                }
                offlineDraft = null
                message = "ลบร่างบนอุปกรณ์แล้ว · ไม่มีบิลหรือการชำระเงินถูกสร้าง"
            } catch (failure: Exception) {
                message = failure.message ?: "ลบร่างไม่สำเร็จ"
            }
        }
    }

    LaunchedEffect(auth.session.accessToken, draftDeviceId) {
        if (draftDeviceId != null) refreshSnapshot()
    }

    LaunchedEffect(auth.session.accessToken, snapshot?.shopId, lifecycle) {
        val shopId = snapshot?.shopId ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            realtime.changes(
                auth.session.accessToken,
                listOf(NativeRealtimeSubscription("orders", "shop_id=eq.$shopId"))
            ).collect {
                refreshSnapshot()
            }
        }
    }

    LaunchedEffect(auth.session.accessToken, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(5_000L)
                if (!busy) refreshSnapshot()
            }
        }
    }

    LaunchedEffect(snapshot, printerSettings, printerSettingsLoaded) {
        if (!printerSettingsLoaded) return@LaunchedEffect
        val snap = snapshot ?: return@LaunchedEffect
        val current = snap.bills.associate { bill ->
            val kitchen = merchantKitchenPrintKey(snap, bill)
            val receipt = merchantReceiptPrintKey(snap, bill)
            bill.id to (kitchen to receipt)
        }
        val previous = printerBaseline
        printerBaseline = current
        if (previous == null || printerSettings.bridgeUrl.isBlank()) return@LaunchedEffect

        snap.bills.forEach { bill ->
            val prior = previous[bill.id]
            val kitchenKey = merchantKitchenPrintKey(snap, bill)
            if (
                printerSettings.autoKitchen &&
                !kitchenKey.isNullOrBlank() &&
                prior?.first != kitchenKey
            ) {
                sendToPrinter(
                    settings = printerSettings,
                    type = "kitchen",
                    text = merchantKitchenTicket(snap, bill),
                    printedKey = kitchenKey,
                    billId = bill.id,
                    successMessage = "พิมพ์ใบครัวอัตโนมัติแล้ว · " + bill.number
                )
            }

            val receiptKey = merchantReceiptPrintKey(snap, bill)
            if (
                printerSettings.autoReceipt &&
                !receiptKey.isNullOrBlank() &&
                prior?.second != receiptKey
            ) {
                sendToPrinter(
                    settings = printerSettings,
                    type = "receipt",
                    text = merchantReceiptTicket(snap, bill),
                    printedKey = receiptKey,
                    billId = bill.id,
                    successMessage = "พิมพ์ใบเสร็จอัตโนมัติแล้ว · " + bill.number
                )
            }
        }
    }

    LaunchedEffect(view, reportDays, snapshot?.shopId) {
        if (view == "reports" && snapshot?.owner == true) {
            report = runCatching { api.ownerDashboard(auth, reportDays) }
                .onFailure { message = it.message ?: "โหลดรายงานไม่สำเร็จ" }
                .getOrNull()
        }
    }

    LaunchedEffect(view, historyDate, historyType, snapshot?.shopId) {
        val snap = snapshot
        if (view == "history" && snap?.owner == true) {
            val day = runCatching { LocalDate.parse(historyDate) }.getOrNull()
            if (day != null) {
                val zone = ZoneId.of("Asia/Bangkok")
                val from = day.atStartOfDay(zone).toInstant().toString()
                val to = day.plusDays(1).atStartOfDay(zone).toInstant().toString()
                history = runCatching {
                    api.history(auth, snap.shopId, from, to, historyType)
                }.onFailure { message = it.message ?: "โหลดประวัติไม่สำเร็จ" }
                    .getOrDefault(emptyList())
            }
        }
    }

    val snap = snapshot
    val openBills = snap?.bills?.filter { it.open }.orEmpty()
    val selectedBill = openBills.firstOrNull { it.id == selectedBillId }
    val kitchenEnabled = snap?.shopCategory == "food" || snap?.shopCategory == "cafe"

    LaunchedEffect(snap?.shopCategory) {
        if (!kitchenEnabled && view == "kitchen") view = "counter"
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack, enabled = !busy) { Text(backLabel) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("QueueGo หน้าร้าน", fontWeight = FontWeight.Black, fontSize = MerchantPageTitleSize)
                Text(
                    snap?.shopName ?: "POS",
                    color = QgMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else TextButton(onClick = { reload() }) { Text("โหลดใหม่") }
        }

        if (!message.isNullOrBlank()) {
            val text = message!!
            val isSuccess = text.contains("แล้ว") || text.contains("สำเร็จ") || text.contains("สร้าง")
            if (!isSuccess) {
                Surface(
                    color = Color(0xFFFFF1F3),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text, color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = { reload() }, enabled = !busy) {
                            Text("ลองอีกครั้ง", fontSize = 11.sp)
                        }
                    }
                }
            } else {
                Text(text, color = QgGreen,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp), fontSize = 12.sp)
            }
        }

        if (pendingEdit != null || pendingEditError != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(pendingEditError ?: "มีรายการ POS ที่ยังไม่ทราบผล ระบบเก็บรายการเดิมไว้แล้ว",
                    color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                OutlinedButton(enabled = !busy, onClick = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            try {
                                val id = api.retryPendingEdit(auth)
                                pendingEdit = null
                                pendingEditError = null
                                pendingPosRequestId = null
                                pendingPosProductId = null
                                pendingPosType = null
                                pendingPosTableId = null
                                pendingPosNote = null
                                selectedBillId = id
                                message = "ตรวจรายการ POS เดิมแล้ว"
                                refreshSnapshot(id)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                message = failure.message ?: "ยังตรวจรายการ POS เดิมไม่สำเร็จ"
                            } finally { busy = false }
                        }
                    }
                }) { Text("ตรวจรายการค้าง") }
            }
        }

        if (offlineDraft != null) {
            PosOfflineDraftPanel(
                draft = offlineDraft!!,
                snapshot = snap,
                online = networkValidated,
                enabled = !busy && selectedBillId == null && pendingEdit == null && pendingEditError == null && can("receive_order"),
                onSubmit = ::submitDraftAfterReview,
                onDiscard = { confirmDraftDiscard = true }
            )
        }

        if (snap == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (snapshotLoading) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(4) {
                            Box(Modifier.fillMaxWidth().height(64.dp).background(QgMuted.copy(alpha = 0.12f), RoundedCornerShape(12.dp)))
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ยังโหลดข้อมูล POS ไม่สำเร็จ", color = QgMuted)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { reload() }) { Text("ลองอีกครั้ง") }
                    }
                }
            }
            return@Column
        }

        PosTabs(
            current = view,
            owner = snap.owner,
            kitchenEnabled = kitchenEnabled,
            onSelect = {
                view = it
                message = null
            }
        )

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (view) {
                "counter" -> PosCounterView(
                    snapshot = snap,
                    selectedBill = selectedBill,
                    mode = mode,
                    tableId = tableId,
                    noTable = noTable,
                    note = note,
                    search = search,
                    busy = busy,
                    pendingCreate = pendingPosRequestId != null,
                    canReceive = can("receive_order") || !networkValidated,
                    kitchenEnabled = kitchenEnabled,
                    canSendKitchen = kitchenEnabled && can("send_kitchen"),
                    canServe = can("serve_order"),
                    canDiscount = can("discount"),
                    canCancel = can("cancel_bill"),
                    canClose = can("close_bill"),
                    canEditPrice = can("edit_price"),
                    onMode = { next ->
                        mode = next
                        tableId = null
                        noTable = next == "DINE_IN"
                        selectedBillId = null
                    },
                    onTable = { id ->
                        mode = "DINE_IN"
                        tableId = id
                        noTable = false
                        selectedBillId = openBills.firstOrNull { it.tableId == id }?.id
                    },
                    onNoTable = {
                        mode = "DINE_IN"
                        tableId = null
                        noTable = true
                        selectedBillId = openBills.firstOrNull {
                            it.type == "DINE_IN" && it.tableId == null
                        }?.id
                    },
                    onNote = { note = it.take(500) },
                    onSearch = { search = it },
                    onProduct = ::addPosProduct,
                    onRetryPending = ::retryPendingPosCreate,
                    onReduce = { line ->
                        val bill = selectedBill
                        if (bill != null && line.productId != null) {
                            runMutation("ลดรายการแล้ว", bill.id) {
                                api.reduceProduct(
                                    auth,
                                    bill.id,
                                    bill.type,
                                    bill.tableId,
                                    line.productId,
                                    line.description
                                )
                            }
                        }
                    },
                    onOpenBill = { bill ->
                        selectedBillId = bill.id
                        mode = bill.type
                        tableId = bill.tableId
                        noTable = bill.type == "DINE_IN" && bill.tableId == null
                    },
                    onBillAction = { action, label ->
                        val bill = selectedBill
                        if (bill != null) runMutation(label + "แล้ว", bill.id) {
                            api.billAction(auth, bill.id, action)
                        }
                    },
                    onDiscount = {
                        discountText = selectedBill?.discount?.toString() ?: "0"
                        discountOpen = true
                    },
                    onCancel = {
                        cancelReason = ""
                        cancelOpen = true
                    },
                    onPay = {
                        view = "bills"
                    },
                    onEditPrice = { product ->
                        priceTarget = product
                        priceText = product.price.toString()
                    }
                )

                "tables" -> PosTablesView(
                    snapshot = snap,
                    busy = busy,
                    canManage = can("manage_staff"),
                    onOpen = { table ->
                        mode = "DINE_IN"
                        tableId = table.id
                        noTable = false
                        selectedBillId = openBills.firstOrNull { it.tableId == table.id }?.id
                        view = "counter"
                    },
                    onNoTable = {
                        mode = "DINE_IN"
                        tableId = null
                        noTable = true
                        selectedBillId = openBills.firstOrNull {
                            it.type == "DINE_IN" && it.tableId == null
                        }?.id
                        view = "counter"
                    },
                    onAdd = {
                        editingTable = null
                        tableLabel = ""
                        tableActive = true
                        tableDialog = true
                    },
                    onEdit = { table ->
                        editingTable = table
                        tableLabel = table.label
                        tableActive = table.active
                        tableDialog = true
                    },
                    onQr = { qrTable = it },
                    onDelete = { table ->
                        runMutation("ลบ " + table.label + " แล้ว", null) {
                            api.deleteTable(auth, table.id)
                        }
                    }
                )

                "kitchen" -> if (kitchenEnabled) PosKitchenView(
                    snapshot = snap,
                    busy = busy,
                    can = ::can,
                    onPosAction = { bill, action, label ->
                        runMutation(label + "แล้ว", bill.id) {
                            api.billAction(auth, bill.id, action)
                        }
                    }
                )

                "bills" -> PosBillsView(
                    bills = openBills.filter { !kitchenEnabled || it.kitchenStatus in setOf("READY", "SERVED") },
                    showKitchenStatus = kitchenEnabled,
                    selected = selectedBill,
                    paymentMethod = paymentMethod,
                    paymentMethodMenu = paymentMethodMenu,
                    cashReceived = cashReceived,
                    busy = busy,
                    canClose = can("close_bill"),
                    onSelect = { selectedBillId = it.id },
                    onPaymentMenu = { paymentMethodMenu = it },
                    onPaymentMethod = {
                        paymentMethod = it
                        paymentMethodMenu = false
                        if (it != "cash") cashReceived = ""
                    },
                    onCash = { cashReceived = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    onPay = {
                        val bill = selectedBill
                        val method = paymentMethod
                        if (!busy && bill != null && can("close_bill")) {
                            val cash = if (method == "cash") cashReceived.toDoubleOrNull() else null
                            if (method == "cash" && (cash == null || !cash.isFinite() || cash < bill.total)) {
                                message = "เงินสดที่รับต้องไม่น้อยกว่ายอดบิล"
                            } else {
                                busy = true
                                scope.launch {
                                    try {
                                        api.takePayment(auth, bill.id, method, cash)
                                        val change = if (method == "cash" && cash != null) {
                                            " · เงินทอน ฿" + money(cash - bill.total)
                                        } else ""
                                        selectedBillId = null
                                        cashReceived = ""
                                        message = "ปิดบิลแล้ว" + change
                                        refreshSnapshot(null)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        message = failure.message ?: "ปิดบิลไม่สำเร็จ"
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        }
                    }
                )


                "reports" -> PosReportsView(
                    snapshot = snap,
                    days = reportDays,
                    report = report,
                    busy = busy,
                    onDays = { reportDays = it },
                    onRefund = { bill ->
                        refundTarget = bill
                        refundReason = ""
                    }
                )

                "history" -> PosHistoryView(
                    snapshot = snap,
                    date = historyDate,
                    type = historyType,
                    typeMenu = historyTypeMenu,
                    rows = history,
                    onDate = { historyDate = it.take(10) },
                    onTypeMenu = { historyTypeMenu = it },
                    onType = {
                        historyType = it
                        historyTypeMenu = false
                    }
                )

                "printer" -> MerchantPrinterScreen(
                    snapshot = snap,
                    settings = printerSettings,
                    busy = busy || printBusy,
                    onSave = { next ->
                        scope.launch {
                            try {
                                val safe = next.validated()
                                withContext(Dispatchers.IO) { printerStore.save(safe) }
                                printerSettings = safe
                                message = "บันทึกการตั้งค่าเครื่องพิมพ์แล้ว"
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                message = failure.message ?: "บันทึกเครื่องพิมพ์ไม่สำเร็จ"
                            }
                        }
                    },
                    onTest = { draft ->
                        sendToPrinter(
                            settings = draft,
                            type = "test",
                            text = snap.shopName + "\nQueueGo Print Bridge\nทดสอบเครื่องพิมพ์ " + draft.widthMm + " มม.",
                            successMessage = "ทดสอบเครื่องพิมพ์แล้ว"
                        )
                    },
                    onKitchen = { bill ->
                        val key = merchantKitchenPrintKey(snap, bill)
                        sendToPrinter(
                            settings = printerSettings,
                            type = "kitchen",
                            text = merchantKitchenTicket(snap, bill),
                            printedKey = key,
                            billId = bill.id,
                            force = true,
                            successMessage = "พิมพ์ใบครัวแล้ว · " + bill.number
                        )
                    },
                    onReceipt = { bill ->
                        val key = merchantReceiptPrintKey(snap, bill)
                        sendToPrinter(
                            settings = printerSettings,
                            type = "receipt",
                            text = merchantReceiptTicket(snap, bill),
                            printedKey = key,
                            billId = bill.id,
                            force = true,
                            successMessage = "พิมพ์ใบเสร็จแล้ว · " + bill.number
                        )
                    }
                )

                "staff" -> PosStaffView(
                    snapshot = snap,
                    inviteRole = inviteRole,
                    inviteRoleMenu = inviteRoleMenu,
                    inviteSecret = inviteSecret,
                    busy = busy,
                    onInviteMenu = { inviteRoleMenu = it },
                    onInviteRole = {
                        inviteRole = it
                        inviteRoleMenu = false
                    },
                    onCreateInvite = {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                runCatching { api.createStaffInvite(auth, inviteRole) }
                                    .onSuccess {
                                        inviteSecret = it
                                        message = "สร้างรหัสเชิญแล้ว · ใช้ครั้งเดียวภายใน 24 ชม."
                                    }
                                    .onFailure { message = it.message ?: "สร้างรหัสเชิญไม่สำเร็จ" }
                                busy = false
                            }
                        }
                    },
                    onCycleRole = { staff ->
                        val next = when (staff.role) {
                            "WAITER" -> "CASHIER"
                            "CASHIER" -> if (kitchenEnabled) "KITCHEN" else "WAITER"
                            else -> "WAITER"
                        }
                        runMutation("เปลี่ยนหน้าที่พนักงานแล้ว", null) {
                            api.setStaffRole(auth, staff.userId, next, staff.active)
                        }
                    },
                    onToggle = { staff ->
                        runMutation(
                            if (staff.active) "ปิดสิทธิ์พนักงานแล้ว" else "เปิดสิทธิ์พนักงานแล้ว",
                            null
                        ) {
                            api.setStaffRole(auth, staff.userId, staff.role, !staff.active)
                        }
                    }
                )
            }
        }
    }

    if (discountOpen && selectedBill != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) discountOpen = false },
            title = { Text("ส่วนลด · " + selectedBill.number) },
            text = {
                OutlinedTextField(
                    discountText,
                    { discountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("ยอดส่วนลด (บาท)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val amount = discountText.toDoubleOrNull()
                        if (amount == null || amount < 0 || amount > selectedBill.subtotal) {
                            message = "ยอดส่วนลดไม่ถูกต้อง"
                        } else {
                            discountOpen = false
                            runMutation("บันทึกส่วนลดแล้ว", selectedBill.id) {
                                api.applyDiscount(auth, selectedBill.id, amount)
                            }
                        }
                    }
                ) { Text("บันทึก") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { discountOpen = false }) { Text("กลับ") }
            }
        )
    }

    if (cancelOpen && selectedBill != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) cancelOpen = false },
            title = { Text("ยกเลิกบิล · " + selectedBill.number) },
            text = {
                OutlinedTextField(
                    cancelReason,
                    { cancelReason = it.take(500) },
                    label = { Text("เหตุผลอย่างน้อย 3 ตัวอักษร") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && cancelReason.trim().length >= 3,
                    onClick = {
                        cancelOpen = false
                        val billId = selectedBill.id
                        runMutation("ยกเลิกบิลแล้ว", null) {
                            api.cancelBill(auth, billId, cancelReason)
                        }
                    }
                ) { Text("ยืนยันยกเลิก") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { cancelOpen = false }) { Text("กลับ") }
            }
        )
    }

    if (optionTarget != null) {
        val product = optionTarget!!
        MerchantPosOptionSelectionDialog(
            product = product,
            groups = product.optionGroups,
            confirmLabel = if (posNetworkValidated(context) && offlineDraft == null && !offlineCatalogOnly) "เพิ่มลงบิล" else "เพิ่มลงร่าง",
            onDismiss = { if (!busy) optionTarget = null },
            onConfirm = { options ->
                optionTarget = null
                addPosProduct(product, options.toString())
            }
        )
    }

    if (refundTarget != null) {
        val bill = refundTarget!!
        AlertDialog(
            onDismissRequest = { if (!busy) refundTarget = null },
            title = { Text("บันทึกคืนเงินจริง · " + bill.number) },
            text = {
                OutlinedTextField(
                    refundReason,
                    { refundReason = it.take(500) },
                    label = { Text("เหตุผลอย่างน้อย 5 ตัวอักษร") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && refundReason.trim().length >= 5,
                    onClick = {
                        refundTarget = null
                        runMutation("บันทึกคืนเงินจริงแล้ว", null) {
                            api.refundBill(auth, bill.id, refundReason)
                        }
                    }
                ) { Text("ยืนยันคืนเงินจริง") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { refundTarget = null }) { Text("กลับ") }
            }
        )
    }

    if (priceTarget != null) {
        val product = priceTarget!!
        AlertDialog(
            onDismissRequest = { if (!busy) priceTarget = null },
            title = { Text("ราคาหน้าร้าน · " + product.name) },
            text = {
                OutlinedTextField(
                    priceText,
                    { priceText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("ราคาหน้าร้าน") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val price = priceText.toDoubleOrNull()
                        if (price == null || price !in 0.0..999999.0) {
                            message = "ราคาหน้าร้านไม่ถูกต้อง"
                        } else {
                            priceTarget = null
                            runMutation("แก้ราคาหน้าร้านแล้ว", selectedBillId) {
                                api.changePrice(auth, product.id, price)
                            }
                        }
                    }
                ) { Text("บันทึก") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { priceTarget = null }) { Text("กลับ") }
            }
        )
    }

    if (tableDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) tableDialog = false },
            title = { Text(if (editingTable == null) "เพิ่มโต๊ะ" else "แก้ไขโต๊ะ") },
            text = {
                Column {
                    OutlinedTextField(
                        tableLabel,
                        { tableLabel = it.take(40) },
                        label = { Text("ชื่อ/หมายเลขโต๊ะ") },
                        singleLine = true
                    )
                    if (editingTable != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(tableActive, { tableActive = it }, enabled = !busy)
                            Text("เปิดใช้งานโต๊ะ")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && tableLabel.trim().isNotEmpty(),
                    onClick = {
                        val id = editingTable?.id
                        tableDialog = false
                        runMutation(
                            if (id == null) "สร้างโต๊ะแล้ว" else "บันทึกโต๊ะแล้ว",
                            selectedBillId
                        ) {
                            api.saveTable(auth, id, tableLabel, tableActive)
                        }
                    }
                ) { Text("บันทึกโต๊ะ") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { tableDialog = false }) { Text("กลับ") }
            }
        )
    }

    if (qrTable != null && snap != null) {
        val table = qrTable!!
        AlertDialog(
            onDismissRequest = { if (!busy) qrTable = null },
            title = { Text(snap.shopName + " · " + table.label) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("สแกนเพื่อดูเมนูและสั่งอาหาร", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    val token = table.qrToken
                    if (token.isNullOrBlank()) {
                        Text("โต๊ะนี้ยังไม่มี QR Code", color = QgMuted)
                    } else {
                        val link = tableQrLink(token)
                        val bitmap = remember(link) { createQrBitmap(link) }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR Code " + table.label,
                                modifier = Modifier.size(230.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer {
                            Text(link, fontSize = 10.sp, color = QgMuted)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("QueueGo table QR", link))
                                message = "คัดลอกลิงก์ QR Code แล้ว"
                            }
                        ) { Text("คัดลอกลิงก์") }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        runMutation(if (table.qrToken == null) "สร้าง QR Code แล้ว" else "สร้าง QR Code ใหม่แล้ว") {
                            api.rotateTableQr(auth, table.id)
                        }
                    }
                ) { Text(if (table.qrToken == null) "สร้าง QR Code" else "สร้าง QR ใหม่") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { qrTable = null }) { Text("ปิด") }
            }
        )
    }

    if (confirmDraftSubmit && offlineDraft != null && snap != null) {
        val draft = offlineDraft!!
        val products = draft.items.mapNotNull { item ->
            snap.products.firstOrNull { it.id == item.productId }?.let { product ->
                runCatching { item to posOfflineDraftItem(product, item.optionsJson) }.getOrNull()
            }
        }
        val changed = products.filter { (item, current) -> posOfflineDraftLineChanged(item, current) }
        val total = products.sumOf { (item, current) -> current.priceSnapshot * item.quantity }
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDraftSubmit = false },
            title = { Text("ตรวจร่างก่อนสร้างบิล") },
            text = {
                Column {
                    Text("ส่งรายการไปตรวจสอบราคาและสิทธิ์บนเซิร์ฟเวอร์หรือไม่? จะสร้างบิลจริงเท่านั้น ยังไม่ชำระเงินและไม่ส่งเข้าครัว")
                    Spacer(Modifier.height(8.dp))
                    Text("ยอดจากราคาปัจจุบัน: ฿${money(total)}", fontWeight = FontWeight.Bold)
                    products.filter { it.second.optionLabel.isNotBlank() }.forEach { (item, current) ->
                        Text("${item.nameSnapshot} × ${item.quantity} · ${current.optionLabel}", fontSize = 12.sp)
                    }
                    changed.forEach { (item, current) ->
                        Text("${item.nameSnapshot} → ${current.nameSnapshot} · ฿${money(item.priceSnapshot)} → ฿${money(current.priceSnapshot)}", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && products.size == draft.items.size, onClick = ::commitReviewedDraft) { Text("ส่งเป็นบิลจริง") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { confirmDraftSubmit = false }) { Text("กลับไปตรวจร่าง") }
            }
        )
    }

    if (confirmDraftDiscard && offlineDraft != null) {
        AlertDialog(
            onDismissRequest = { confirmDraftDiscard = false },
            title = { Text("ลบร่าง POS บนอุปกรณ์นี้?") },
            text = { Text("ร่างนี้ยังไม่ใช่บิลและยังไม่มีการรับชำระเงิน") },
            confirmButton = {
                TextButton(onClick = { confirmDraftDiscard = false; discardOfflineDraft() }) { Text("ลบร่าง") }
            },
            dismissButton = { TextButton(onClick = { confirmDraftDiscard = false }) { Text("กลับ") } }
        )
    }
}

@Composable
private fun PosOfflineDraftPanel(
    draft: PosOfflineDraft,
    snapshot: PosSnapshot?,
    online: Boolean,
    enabled: Boolean,
    onSubmit: () -> Unit,
    onDiscard: () -> Unit
) {
    val currentProducts = draft.items.map { item ->
        item to snapshot?.products?.firstOrNull { it.id == item.productId }?.let { product ->
            runCatching { posOfflineDraftItem(product, item.optionsJson) }.getOrNull()
        }
    }
    val pending = draft.items.any { it.requestIds.isNotEmpty() }
    val valid = currentProducts.all { it.second != null }
    val changes = currentProducts.count { (item, current) ->
        current != null && posOfflineDraftLineChanged(item, current)
    }
    QgCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("ร่างตะกร้าบนอุปกรณ์นี้", fontWeight = FontWeight.Black)
            Text(if (online) "ออนไลน์ · ตรวจสินค้าและราคาปัจจุบันก่อนส่ง" else "ออฟไลน์ · ยังไม่สร้างบิล ไม่ชำระเงิน และไม่ส่งเข้าครัว",
                color = if (online) QgGreen else QgRed, fontSize = 12.sp)
            currentProducts.take(4).forEach { (item, current) ->
                Text("${item.nameSnapshot} × ${item.quantity}" +
                    if (current != null && posOfflineDraftLineChanged(item, current))
                        " · ปัจจุบัน ${current.nameSnapshot} ฿${money(current.priceSnapshot)}" else " · ฿${money(item.priceSnapshot)}",
                    fontSize = 12.sp)
                if (item.optionLabel.isNotBlank()) Text(item.optionLabel, fontSize = 11.sp, color = QgMuted)
            }
            if (draft.items.size > 4) Text("และอีก ${draft.items.size - 4} รายการ", color = QgMuted, fontSize = 11.sp)
            if (changes > 0) Text("มี ${changes} รายการที่ชื่อหรือราคาเปลี่ยน · ตรวจทานก่อนส่ง", color = QgRed, fontSize = 11.sp)
            if (online && !valid) Text("มีสินค้าหรือตัวเลือกที่ไม่พร้อมขาย กรุณาตรวจร่างอีกครั้ง", color = QgRed, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = enabled && online && snapshot != null && valid, onClick = onSubmit) { Text("ตรวจและส่งเมื่อออนไลน์") }
                OutlinedButton(enabled = !pending, onClick = onDiscard) { Text(if (pending) "ตรวจผลก่อนลบ" else "ลบร่าง") }
            }
        }
    }
}

@Composable
private fun PosTabs(current: String, owner: Boolean, kitchenEnabled: Boolean, onSelect: (String) -> Unit) {
    val tabs = buildList {
        add("counter" to "＋ รับออเดอร์")
        add("tables" to "โต๊ะ")
        if (kitchenEnabled) add("kitchen" to "ครัว")
        add("bills" to "คิดเงิน")
        add("printer" to "เครื่องพิมพ์")
        if (owner) {
            add("reports" to "ยอดขาย")
            add("history" to "ประวัติ")
            add("staff" to "พนักงาน")
        }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEach { (key, label) ->
            if (key == current) {
                Button(
                    onClick = { onSelect(key) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                ) { Text(label, fontSize = 11.sp) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(key) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                ) { Text(label, fontSize = 11.sp) }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun PosCounterView(
    snapshot: PosSnapshot,
    selectedBill: PosBill?,
    mode: String,
    tableId: String?,
    noTable: Boolean,
    note: String,
    search: String,
    busy: Boolean,
    pendingCreate: Boolean,
    canReceive: Boolean,
    kitchenEnabled: Boolean,
    canSendKitchen: Boolean,
    canServe: Boolean,
    canDiscount: Boolean,
    canCancel: Boolean,
    canClose: Boolean,
    canEditPrice: Boolean,
    onMode: (String) -> Unit,
    onTable: (String) -> Unit,
    onNoTable: () -> Unit,
    onNote: (String) -> Unit,
    onSearch: (String) -> Unit,
    onProduct: (PosProduct) -> Unit,
    onRetryPending: () -> Unit,
    onReduce: (PosLine) -> Unit,
    onOpenBill: (PosBill) -> Unit,
    onBillAction: (String, String) -> Unit,
    onDiscount: () -> Unit,
    onCancel: () -> Unit,
    onPay: () -> Unit,
    onEditPrice: (PosProduct) -> Unit
) {
    val openBills = snapshot.bills.filter { it.open }
    val lines = selectedBill?.let { snapshot.linesByOrder[it.id].orEmpty() }.orEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        if (pendingCreate) {
            Surface(
                color = Color(0xFFFFF5E8),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "กำลังตรวจบิลก่อนหน้าที่ผลลัพธ์ไม่แน่ชัด",
                        modifier = Modifier.weight(1f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(onClick = onRetryPending, enabled = !busy) {
                        Text("ตรวจบิลเดิม", fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PosChoiceButton("ทานที่ร้าน", mode == "DINE_IN", Modifier.weight(1f)) { onMode("DINE_IN") }
            PosChoiceButton("รับกลับ", mode == "TAKEAWAY", Modifier.weight(1f)) { onMode("TAKEAWAY") }
        }

        if (mode == "DINE_IN") {
            Spacer(Modifier.height(10.dp))
            Text("เลือกโต๊ะ", fontWeight = FontWeight.ExtraBold)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                PosChoiceButton("ไม่ระบุโต๊ะ", noTable, onClick = onNoTable)
                snapshot.tables.filter { it.active }.forEach { table ->
                    val bill = openBills.firstOrNull { it.tableId == table.id }
                    PosChoiceButton(
                        table.label + if (bill != null) " · " + bill.number else "",
                        tableId == table.id,
                        onClick = { onTable(table.id) }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            search,
            onSearch,
            label = { Text("ค้นหาเมนู") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(
            note,
            onNote,
            label = { Text(if (kitchenEnabled) "หมายเหตุสำหรับครัว (ถ้ามี)" else "หมายเหตุรายการ (ถ้ามี)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        QgSectionTitle("เมนูสินค้า", if (selectedBill == null) "เลือกสินค้าเพื่อเริ่มบิล" else selectedBill.number)
        Spacer(Modifier.height(8.dp))
        val products = snapshot.products.filter {
            search.isBlank() || it.name.contains(search.trim(), ignoreCase = true)
        }
        products.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { product ->
                    QgCard(
                        Modifier
                            .weight(1f)
                            .padding(bottom = 8.dp)
                            .clickable(enabled = !busy && canReceive && !posProductSoldOut(product)) { onProduct(product) }
                    ) {
                        Column {
                            QgRemoteImage(product.image, Modifier.fillMaxWidth().height(82.dp), product.name)
                            Spacer(Modifier.height(5.dp))
                            Text(product.name, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                            Text("฿" + money(product.price), color = QgRed, fontWeight = FontWeight.Black)
                            if (posProductInventoryTracked(product)) {
                                Text(
                                    if (posProductSoldOut(product)) "สินค้าหมดสต็อก · เหลือ 0" else "เหลือ ${product.stock}",
                                    color = if (posProductSoldOut(product)) QgRed else QgMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (canEditPrice) {
                                TextButton(onClick = { onEditPrice(product) }, enabled = !busy) {
                                    Text("แก้ราคา", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (products.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) { Text("ไม่พบสินค้าที่ขายหน้าร้าน", color = QgMuted) }
        }

        if (selectedBill != null) {
            Spacer(Modifier.height(10.dp))
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedBill.number, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Spacer(Modifier.weight(1f))
                        if (kitchenEnabled) QgStatusPill(selectedBill.kitchenStatus)
                    }
                    Spacer(Modifier.height(6.dp))
                    lines.forEach { line ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(line.name + " × " + line.quantity, fontWeight = FontWeight.Bold)
                                if (!line.description.isNullOrBlank()) {
                                    Text(line.description, color = QgMuted, fontSize = 10.sp)
                                }
                            }
                            Text("฿" + money(line.total), fontWeight = FontWeight.Bold)
                            if (selectedBill.kitchenStatus == "NEW" && line.productId != null) {
                                Spacer(Modifier.width(6.dp))
                                OutlinedButton(
                                    onClick = { onReduce(line) },
                                    enabled = !busy,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp)
                                ) { Text("−") }
                            }
                        }
                        HorizontalDivider()
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(
                            "ยอดรวม" + if (selectedBill.discount > 0) " · ลด ฿" + money(selectedBill.discount) else "",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Text("฿" + money(selectedBill.total), color = QgRed, fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (kitchenEnabled) when (selectedBill.kitchenStatus) {
                "NEW" -> if (canSendKitchen) {
                    Button(
                        onClick = { onBillAction("send", "ส่งเข้าครัว") },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ส่งเข้าครัว", fontWeight = FontWeight.Bold) }
                }
                "READY" -> if (canServe) {
                    Button(
                        onClick = { onBillAction("serve", "เสิร์ฟ") },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("เสิร์ฟแล้ว", fontWeight = FontWeight.Bold) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (canDiscount) {
                    OutlinedButton(onClick = onDiscount, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text("ส่วนลด")
                    }
                }
                if (canClose && (!kitchenEnabled || selectedBill.kitchenStatus in setOf("READY", "SERVED"))) {
                    Button(onClick = onPay, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text("คิดเงิน")
                    }
                }
                if (canCancel) {
                    OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text("ยกเลิกบิล")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        QgSectionTitle("บิลที่ยังไม่ปิด")
        Spacer(Modifier.height(7.dp))
        if (openBills.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่มีบิล", color = QgMuted) }
        } else {
            openBills.forEach { bill ->
                QgCard(
                    Modifier.fillMaxWidth().padding(bottom = 7.dp).clickable { onOpenBill(bill) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(bill.number, fontWeight = FontWeight.ExtraBold)
                            Text(
                                if (bill.type == "DINE_IN") {
                                    "ทานที่ร้าน · " + (snapshot.tables.firstOrNull { it.id == bill.tableId }?.label ?: "ไม่ระบุโต๊ะ")
                                } else "รับกลับ",
                                color = QgMuted,
                                fontSize = 11.sp
                            )
                        }
                        Text("฿" + money(bill.total), color = QgRed, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PosTablesView(
    snapshot: PosSnapshot,
    busy: Boolean,
    canManage: Boolean,
    onOpen: (PosTable) -> Unit,
    onNoTable: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (PosTable) -> Unit,
    onQr: (PosTable) -> Unit,
    onDelete: (PosTable) -> Unit
) {
    val open = snapshot.bills.filter { it.open }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QgSectionTitle("โต๊ะ", "จัดการโต๊ะและ QR Code สำหรับสั่งอาหาร")
            Spacer(Modifier.weight(1f))
            if (canManage) Button(onClick = onAdd, enabled = !busy) { Text("＋ เพิ่มโต๊ะ") }
        }
        Spacer(Modifier.height(10.dp))
        snapshot.tables.forEach { table ->
            val bill = open.firstOrNull { it.tableId == table.id }
            QgCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(table.label, fontWeight = FontWeight.Black)
                            Text(
                                bill?.let { "มีออเดอร์ · " + it.number }
                                    ?: if (table.active) "ว่าง" else "ปิดใช้งาน",
                                color = QgMuted,
                                fontSize = 11.sp
                            )
                        }
                        Button(onClick = { onOpen(table) }, enabled = table.active || bill != null) {
                            Text(if (bill == null) "เปิดโต๊ะ" else "เปิดบิล")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { onQr(table) }, enabled = canManage && !busy) {
                            Text("ดู QR Code")
                        }
                        if (canManage) {
                            OutlinedButton(onClick = { onEdit(table) }, enabled = !busy) {
                                Text("แก้ไข")
                            }
                            OutlinedButton(
                                onClick = { onDelete(table) },
                                enabled = !busy && bill == null
                            ) { Text("ลบโต๊ะ") }
                        }
                    }
                }
            }
        }
        if (snapshot.tables.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่มีโต๊ะที่ตั้งไว้", color = QgMuted) }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onNoTable, modifier = Modifier.fillMaxWidth()) {
            Text("＋ รับออเดอร์แบบไม่ระบุโต๊ะ")
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PosKitchenView(
    snapshot: PosSnapshot,
    busy: Boolean,
    can: (String) -> Boolean,
    onPosAction: (PosBill, String, String) -> Unit
) {
    val posBills = snapshot.bills.filter {
        it.open && it.kitchenStatus !in setOf("NEW", "SERVED")
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        QgSectionTitle("คิวครัวหน้าร้าน", "เฉพาะบิล POS ของร้านอาหารและคาเฟ่")
        Spacer(Modifier.height(10.dp))

        posBills.forEach { bill ->
            val all = snapshot.linesByOrder[bill.id].orEmpty()
            val lastBatch = all.maxOfOrNull { it.batch } ?: 0
            val lines = all.filter { it.batch == lastBatch && it.kitchenStatus != "SERVED" }
            QgCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column {
                    Row {
                        Text(
                            if (bill.type == "DINE_IN") {
                                "ทานที่ร้าน · " + (snapshot.tables.firstOrNull { it.id == bill.tableId }?.label ?: "ไม่ระบุโต๊ะ")
                            } else "รับกลับ",
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.weight(1f))
                        QgStatusPill(bill.kitchenStatus)
                    }
                    Text(bill.number, color = QgMuted, fontSize = 11.sp)
                    lines.forEach { line ->
                        Text(
                            line.name + " × " + line.quantity +
                                (line.description?.let { " · " + it } ?: ""),
                            fontSize = 12.sp
                        )
                    }
                    val action = when {
                        bill.kitchenStatus == "SENT_TO_KITCHEN" && can("cook_order") ->
                            "cook" to "เริ่มทำ"
                        bill.kitchenStatus == "COOKING" && can("ready_order") ->
                            "ready" to "พร้อมเสิร์ฟ"
                        bill.kitchenStatus == "READY" && can("serve_order") ->
                            "serve" to "เสิร์ฟแล้ว"
                        else -> null
                    }
                    if (action != null) {
                        Spacer(Modifier.height(7.dp))
                        Button(
                            onClick = { onPosAction(bill, action.first, action.second) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(action.second, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        if (posBills.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่มีออเดอร์เข้าครัว", color = QgMuted) }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PosBillsView(
    bills: List<PosBill>,
    showKitchenStatus: Boolean,
    selected: PosBill?,
    paymentMethod: String,
    paymentMethodMenu: Boolean,
    cashReceived: String,
    busy: Boolean,
    canClose: Boolean,
    onSelect: (PosBill) -> Unit,
    onPaymentMenu: (Boolean) -> Unit,
    onPaymentMethod: (String) -> Unit,
    onCash: (String) -> Unit,
    onPay: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        QgSectionTitle("บิลรอคิดเงิน")
        Spacer(Modifier.height(8.dp))
        if (bills.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่มีบิลรอชำระ", color = QgMuted) }
        } else bills.forEach { bill ->
            QgCard(
                Modifier.fillMaxWidth().padding(bottom = 7.dp).clickable { onSelect(bill) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(bill.number, fontWeight = FontWeight.Black)
                        if (showKitchenStatus) Text(bill.kitchenStatus, color = QgMuted, fontSize = 11.sp)
                    }
                    Text("฿" + money(bill.total), color = QgRed, fontWeight = FontWeight.Black)
                }
            }
        }

        if (selected != null && selected in bills) {
            Spacer(Modifier.height(12.dp))
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("คิดเงิน · " + selected.number, fontWeight = FontWeight.Black)
                    Text("ยอดที่ต้องรับ", color = QgMuted)
                    Text("฿" + money(selected.total), color = QgRed, fontWeight = FontWeight.Black, fontSize = 28.sp)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        OutlinedButton(
                            onClick = { onPaymentMenu(true) },
                            enabled = canClose && !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(posPaymentMethods.firstOrNull { it.first == paymentMethod }?.second ?: paymentMethod)
                        }
                        DropdownMenu(paymentMethodMenu, { onPaymentMenu(false) }) {
                            posPaymentMethods.forEach { (key, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { onPaymentMethod(key) })
                            }
                        }
                    }
                    if (paymentMethod == "cash") {
                        Spacer(Modifier.height(7.dp))
                        OutlinedTextField(
                            cashReceived,
                            onCash,
                            label = { Text("รับเงินสด") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val cash = cashReceived.toDoubleOrNull()
                        Text(
                            "เงินทอน " + if (cash == null) "—" else "฿" + money((cash - selected.total).coerceAtLeast(0.0)),
                            color = QgMuted,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onPay,
                        enabled = canClose && !busy,
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("ยืนยันรับเงินและปิดบิล", fontWeight = FontWeight.Bold) }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PosReportsView(
    snapshot: PosSnapshot,
    days: Int,
    report: PosOwnerDashboard?,
    busy: Boolean,
    onDays: (Int) -> Unit,
    onRefund: (PosBill) -> Unit
) {
    val paid = snapshot.bills.filter { it.paymentStatus == "PAID" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        QgSectionTitle("รายได้ทั้งหมด")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(1 to "วันนี้", 7 to "7 วัน", 30 to "30 วัน").forEach { (value, label) ->
                PosChoiceButton(label, days == value) { onDays(value) }
            }
        }
        Spacer(Modifier.height(9.dp))
        if (report == null) {
            CircularProgressIndicator()
        } else {
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    PosReportLine("ทานที่ร้าน", report.dineIn)
                    PosReportLine("รับกลับ", report.takeaway)
                    HorizontalDivider()
                    PosReportLine("ยอดขายรวม", report.dineIn + report.takeaway, true)
                    Text("จำนวนบิล " + report.orders, color = QgMuted, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        QgSectionTitle("บิลที่ชำระแล้ว")
        Spacer(Modifier.height(7.dp))
        if (paid.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่มีบิล", color = QgMuted) }
        } else paid.forEach { bill ->
            QgCard(Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(bill.number, fontWeight = FontWeight.ExtraBold)
                        Text(
                            (bill.paymentMethod ?: "-") +
                                (bill.staffId?.let { " · " + (snapshot.staff.firstOrNull { s -> s.userId == it }?.displayName ?: "ร้าน") } ?: ""),
                            color = QgMuted,
                            fontSize = 10.sp
                        )
                    }
                    Text("฿" + money(bill.total), fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick = { onRefund(bill) }, enabled = !busy) {
                        Text("คืนเงินจริง", fontSize = 10.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PosHistoryView(
    snapshot: PosSnapshot,
    date: String,
    type: String,
    typeMenu: Boolean,
    rows: List<PosHistoryRow>,
    onDate: (String) -> Unit,
    onTypeMenu: (Boolean) -> Unit,
    onType: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        QgSectionTitle("ประวัติออเดอร์")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            date,
            onDate,
            label = { Text("วันที่ YYYY-MM-DD") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(7.dp))
        Box {
            OutlinedButton(onClick = { onTypeMenu(true) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when (type) {
                        "DINE_IN" -> "ทานที่ร้าน"
                        "TAKEAWAY" -> "รับกลับ"
                        else -> "ทั้งหมด"
                    }
                )
            }
            DropdownMenu(typeMenu, { onTypeMenu(false) }) {
                listOf(
                    "ALL" to "ทั้งหมด",
                    "DINE_IN" to "ทานที่ร้าน",
                    "TAKEAWAY" to "รับกลับ",
                ).forEach { (key, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onType(key) })
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (rows.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) { Text("ไม่พบออเดอร์ในวันที่เลือก", color = QgMuted) }
        } else rows.forEach { row ->
            QgCard(Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(row.number, fontWeight = FontWeight.ExtraBold)
                        val staff = row.staffId?.let { id ->
                            snapshot.staff.firstOrNull { it.userId == id }?.displayName
                        } ?: "ร้าน"
                        Text(
                            historyTypeLabel(row) + " · " + row.status + " · " + staff,
                            color = QgMuted,
                            fontSize = 10.sp
                        )
                    }
                    Text("฿" + money(row.total), fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PosStaffView(
    snapshot: PosSnapshot,
    inviteRole: String,
    inviteRoleMenu: Boolean,
    inviteSecret: String,
    busy: Boolean,
    onInviteMenu: (Boolean) -> Unit,
    onInviteRole: (String) -> Unit,
    onCreateInvite: () -> Unit,
    onCycleRole: (PosStaff) -> Unit,
    onToggle: (PosStaff) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        QgSectionTitle("บัญชีพนักงาน", "พนักงานสมัครด้วยอีเมลของตนเอง แล้วใช้รหัสเชิญที่ร้านออกให้")
        Spacer(Modifier.height(10.dp))
        Box {
            OutlinedButton(onClick = { onInviteMenu(true) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(posRoles.firstOrNull { it.first == inviteRole }?.second ?: inviteRole)
            }
            DropdownMenu(inviteRoleMenu, { onInviteMenu(false) }) {
                posRoles.filter { it.first != "KITCHEN" || snapshot.shopCategory in setOf("food", "cafe") }.forEach { (key, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onInviteRole(key) })
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Button(
            onClick = onCreateInvite,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("สร้างรหัสเชิญ (ใช้ครั้งเดียว ภายใน 24 ชม.)") }

        if (inviteSecret.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("รหัสเชิญ", fontWeight = FontWeight.Black)
                    SelectionContainer {
                        Text(inviteSecret, color = QgRed, fontSize = 11.sp)
                    }
                    Text("คัดลอกส่งให้พนักงานและเก็บไว้ตอนนี้เท่านั้น", color = QgMuted, fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        snapshot.staff.forEach { staff ->
            QgCard(Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(staff.displayName, fontWeight = FontWeight.ExtraBold)
                            Text(
                                posRoles.firstOrNull { it.first == staff.role }?.second ?: staff.role,
                                color = QgMuted,
                                fontSize = 11.sp
                            )
                        }
                        QgStatusPill(if (staff.active) "เปิดสิทธิ์" else "ปิดสิทธิ์", staff.active)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(onClick = { onCycleRole(staff) }, enabled = !busy) {
                            Text("เปลี่ยนหน้าที่", fontSize = 10.sp)
                        }
                        OutlinedButton(onClick = { onToggle(staff) }, enabled = !busy) {
                            Text(if (staff.active) "ปิดสิทธิ์" else "เปิดสิทธิ์", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        if (snapshot.staff.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่มีพนักงาน", color = QgMuted) }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PosChoiceButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label, fontSize = 11.sp) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label, fontSize = 11.sp) }
    }
}

@Composable
private fun PosReportLine(label: String, value: Double, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), fontWeight = if (strong) FontWeight.Black else FontWeight.Normal)
        Text("฿" + money(value), fontWeight = if (strong) FontWeight.Black else FontWeight.Bold)
    }
}

@Composable
private fun PosReadinessLine(label: String, ready: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(if (ready) "✓" else "○", color = if (ready) QgGreen else QgRed, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(7.dp))
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text(if (ready) "พร้อมแล้ว" else "ตั้งค่า", color = if (ready) QgGreen else QgRed, fontSize = 10.sp)
    }
}

private fun historyTypeLabel(row: PosHistoryRow): String = when {
    row.type == "DINE_IN" -> "ทานที่ร้าน"
    row.type == "TAKEAWAY" -> "รับกลับ"
    else -> row.type
}

private fun tableQrLink(token: String): String =
    "https://chatchairins-source.github.io/QueuGo/table-order.html#scan/" + token

private fun createQrBitmap(value: String): Bitmap? = runCatching {
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512)
    Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (y in 0 until 512) {
            for (x in 0 until 512) {
                bitmap.setPixel(
                    x,
                    y,
                    if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
    }
}.getOrNull()

private fun money(value: Double): String =
    String.format(Locale.US, "%,.2f", value.coerceAtLeast(0.0))

private fun posNetworkValidated(context: Context): Boolean = try {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
} catch (_: Exception) { false }
