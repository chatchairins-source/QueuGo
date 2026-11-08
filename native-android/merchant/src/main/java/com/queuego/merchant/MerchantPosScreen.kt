Warning: truncated output (original token count: 51219)
Total output lines: 3902

package com.queuego.merchant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
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
import com.queuego.shared.QgIconButton
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
    val merchantApi = remember { MerchantApi() }
    val cashMovementStore = remember(context) { MerchantPosCashMovementStore(context) }
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
    // Only an authenticated, refreshed PAID + CLOSED bill may be previewed or printed.
    var receiptDialogBillId by rememberSaveable { mutableStateOf<String?>(null) }
    var view by rememberSaveable { mutableStateOf("counter") }
    var mode by rememberSaveable { mutableStateOf("TAKEAWAY") }
    var tableId by rememberSaveable { mutableStateOf<String?>(null) }
    var noTable by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var scannedCode by rememberSaveable { mutableStateOf("") }
    var scannedProduct by remember(auth.session.authUserId) { mutableStateOf<PosProduct?>(null) }
    var scanStatus by remember(auth.session.authUserId) { mutableStateOf<String?>(null) }
    var scanLoading by remember(auth.session.authUserId) { mutableStateOf(false) }
    var bestSellerNames by remember(auth.session.authUserId) { mutableStateOf<Set<String>>(emptySet()) }
    var selectedPosCategory by rememberSaveable { mutableStateOf("all") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingReportCsv by remember { mutableStateOf<String?>(null) }
    val reportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val csv = pendingReportCsv
        pendingReportCsv = null
        if (uri != null && csv != null) {
            scope.launch(Dispatchers.IO) {
                val result = runCatching {
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: error("ไม่สามารถเปิดไฟล์สำหรับบันทึกรายงาน POS")
                    output.use { stream -> stream.write(csv.toByteArray(Charsets.UTF_8)) }
                }
                withContext(Dispatchers.Main) {
                    message = result.fold(
                        onSuccess = { "บันทึกรายงาน POS CSV แล้ว" },
                        onFailure = { "บันทึก CSV ไม่สำเร็จ: " + (it.message ?: "ข้อผิดพลาด") }
                    )
                }
            }
        }
    }
    var pendingQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrActionMessage by remember { mutableStateOf<String?>(null) }
    val qrSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val bitmap = pendingQrBitmap
        pendingQrBitmap = null
        if (uri != null && bitmap != null) {
            scope.launch(Dispatchers.IO) {
                val result = runCatching {
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: error("ไม่สามารถเปิดไฟล์สำหรับบันทึก QR Code")
                    output.use { stream ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                            "สร้างไฟล์ PNG ไม่สำเร็จ"
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    val resultText = result.fold(
                        onSuccess = { "บันทึก QR Code แล้ว" },
                        onFailure = { "บันทึก QR Code ไม่สำเร็จ: " + (it.message ?: "ข้อผิดพลาด") }
                    )
                    qrActionMessage = resultText
                    message = resultText
                }
            }
        }
    }
    var pendingEdit by remember { mutableStateOf<PosEditIntent?>(null) }
    var pendingEditError by remember { mutableStateOf<String?>(null) }
    var offlineDraft by remember { mutableStateOf<PosOfflineDraft?>(null) }
    var draftDeviceId by remember { mutableStateOf<String?>(null) }
    var offlineCatalogOnly by remember { mutableStateOf(false) }
    var networkValidated by remember { mutableStateOf(false) }
    var confirmDraftSubmit by remember { mutableStateOf(false) }
    var confirmDraftDiscard by remember { mutableStateOf(false) }
    var deliveryShop by remember(auth.user.id) { mutableStateOf<MerchantShop?>(null) }
    var deliveryLoading by remember(auth.user.id) { mutableStateOf(false) }

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
    var cashShift by remember(auth.session.authUserId) { mutableStateOf<PosCashShift?>(null) }
    var cashShiftError by remember(auth.session.authUserId) { mutableStateOf<String?>(null) }
    var cashShiftPending by remember { mutableStateOf(false) }
    var openingCash by rememberSaveable { mutableStateOf("0") }
    var countedCash by rememberSaveable { mutableStateOf("") }
    var cashMovementAmount by rememberSaveable { mutableStateOf("") }
    var cashMovementReason by rememberSaveable { mutableStateOf("") }
    var pendingCashMovement by remember(auth.session.authUserId, snapshot?.shopId) {
        mutableStateOf(snapshot?.shopId?.let { cashMovementStore.load(it, auth.session.authUserId) })
    }
    val pendingCashMovementType = pendingCashMovement?.type
    val pendingCashMovementAmount = pendingCashMovement?.amount?.toString()
    val pendingCashMovementReason = pendingCashMovement?.reason
    val pendingCashMovementRequestId = pendingCashMovement?.requestId
    val pendingCashMovementShopId = pendingCashMovement?.shopId
    val pendingCashMovementActorId = pendingCashMovement?.actorId

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
    var itemSalesReport by remember { mutableStateOf<PosItemSalesReport?>(null) }
    var taxSettings by remember { mutableStateOf(PosTaxSettings()) }
    var taxRateText by rememberSaveable { mutableStateOf("7.00") }
    var taxSettingsLoaded by remember { mutableStateOf(false) }


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

    // QR rotation is a write. Reconcile its exact token with a read-only snapshot
    // after an uncertain response; never rotate a second time automatically.
    fun createTableQr(table: PosTable) {
        if (busy) return
        qrTable = table
        busy = true
        message = null
        scope.launch {
            try {
                val token = api.rotateTableQr(auth, table.id)
                refreshSnapshot(selectedBillId)
                val refreshed = snapshot?.tables?.firstOrNull { it.id == table.id }
                val serverTable = refreshed?.takeIf { !it.qrToken.isNullOrBlank() }
                qrTable = serverTable ?: table.copy(qrToken = token)
                message = if (serverTable != null) {
                    "สร้าง QR Code แล้ว"
                } else {
                    "เซิร์ฟเวอร์ยืนยัน QR Code แล้ว แต่โหลดรายการโต๊ะล่าสุดไม่สำเร็จ กรุณาโหลด POS ใหม่"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // The RPC may have committed before timing out. Check the existing table
                // once and keep the same token if the server already created it.
                refreshSnapshot(selectedBillId)
                val refreshed = snapshot?.tables?.firstOrNull { it.id == table.id }
                if (!refreshed?.qrToken.isNullOrBlank()) {
                    qrTable = refreshed
                    message = "QR Code ยืนยันจากเซิร์ฟเวอร์แล้ว"
                } else {
                    qrTable = refreshed ?: table
                    message = "ยังยืนยันผลการสร้าง QR Code ไม่ได้ กรุณาตรวจสอบโต๊ะก่อนลองใหม่: " +
                        (failure.message ?: "ข้อผิดพลาด")
                }
            } finally {
                busy = false
            }
        }
    }

    fun savePosTable(id: String?, label: String, active: Boolean) {
        if (busy) return
        if (pendingEdit != null || pendingEditError != null) {
            message = "กรุณากดตรวจรายการ POS ค้างก่อนทำรายการอื่น"
            return
        }
        val normalizedLabel = label.trim()
        val previousIds = snapshot?.tables?.map { it.id }?.toSet().orEmpty()
        busy = true
        message = null
        scope.launch {
            var savedId: String? = null
            var qrToken: String? = null
            var qrError: String? = null
            try {
                val createdId = api.saveTable(auth, id, normalizedLabel, active)
                savedId = createdId
                tableDialog = false
                if (id == null) {
                    try {
                        qrToken = api.rotateTableQr(auth, createdId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        qrError = failure.message ?: "สร้าง QR Code ไม่สำเร็จ"
                    }
                }
                refreshSnapshot(selectedBillId)
                val refreshed = snapshot?.tables?.firstOrNull { it.id == createdId }
                if (id == null) {
                    val serverTableWithQr = refreshed?.takeIf {
                        !it.qrToken.isNullOrBlank() && (qrToken == null || it.qrToken == qrToken)
                    }
                    val createdTable = when {
                        serverTableWithQr != null -> serverTableWithQr
                        qrToken != null -> PosTable(createdId, normalizedLabel, active, qrToken)
                        else -> refreshed ?: PosTable(createdId, normalizedLabel, active, null)
                    }
                    qrTable = createdTable
                    message = when {
                        !createdTable.qrToken.isNullOrBlank() -> "สร้างโต๊ะและ QR Code แล้ว"
                        refreshed != null -> "สร้างโต๊ะแล้ว แต่ QR Code ยังไม่พร้อม" +
                            (qrError?.let { " · " + it } ?: " · กดสร้าง QR Code เพื่อลองอีกครั้ง")
                        else -> "เซิร์ฟเวอร์ยืนยันการสร้างโต๊ะแล้ว แต่โหลดรายการล่าสุดไม่สำเร็จ กรุณากดโหลด POS ใหม่ก่อนสร้างซ้ำ"
                    }
                } else {
                    message = if (refreshed?.label == normalizedLabel && refreshed.active == active) {
                        "บันทึกโต๊ะแล้ว"
                    } else {
                        "เซิร์ฟเวอร์บันทึกโต๊ะแล้ว แต่โหลดข้อมูลล่าสุดไม่สำเร็จ กรุณากดโหลด POS ใหม่ก่อนลองซ้ำ"
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Do not repeat a timed-out create. First read the shop's tables and
                // reconcile a newly visible table with this exact label.
                val knownRejection = failure is QueueGoHttpException && failure.statusCode in 400..499
                if (id == null && savedId == null && !knownRejection) {
                    refreshSnapshot(selectedBillId)
                    val candidates = snapshot?.tables.orEmpty().filter {
                        it.id !in previousIds && it.label.trim().equals(normalizedLabel, ignoreCase = true)
                    }
                    if (candidates.size == 1) {
                        val candidate = candidates.single()
                        tableDialog = false
                        var confirmedToken = candidate.qrToken
                        if (confirmedToken.isNullOrBlank()) {
                            try {
                                confirmedToken = api.rotateTableQr(auth, candidate.id)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (qrFailure: Exception) {
                                qrError = qrFailure.message ?: "สร้าง QR Code ไม่สำเร็จ"
                            }
                            refreshSnapshot(selectedBillId)
                        }
                        val latest = snapshot?.tables?.firstOrNull { it.id == candidate.id }
                        qrTable = latest?.takeIf { !it.qrToken.isNullOrBlank() }
                            ?: PosTable(candidate.id, candidate.label, candidate.active, confirmedToken)
                        message = if (!qrTable?.qrToken.isNullOrBlank()) {
                            "สร้างโต๊ะและ QR Code ยืนยันจากเซิร์ฟเวอร์แล้ว"
                        } else {
                            "ยืนยันว่าโต๊ะถูกสร้างแล้ว แต่ QR Code ยังไม่พร้อม" +
                                (qrError?.let { " · " + it } ?: " · ตรวจสอบโต๊ะก่อนลองสร้าง QR อีกครั้ง")
                        }
                    } else {
                        tableDialog = false
                        message = "ผลบันทึกโต๊ะยังไม่ชัดเจน กรุณาโหลดรายการโต๊ะและตรวจสอบก่อนสร้างซ้ำ: " +
                            (failure.message ?: "ข้อผิดพลาด")
                    }
                } else {
                    message = failure.message ?: "บันทึกโต๊ะไม่สำเร็จ"
                }
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
                    "ผลเปิดบิลยังไม่แน่ชัด · กดตรวจบิลเดิมก่อน…31219 tokens truncated…อย 5 ตัวอักษร)") },
                            enabled = !shiftPending && !busy && !cashMovementPending,
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        if (cashMovementPending) {
                            Text(
                                "มีรายการ " + (if (pendingCashMovementType == "cash_in") "เงินเข้า" else "เงินออก") + " ที่ยังไม่ยืนยัน ใช้ปุ่มตรวจรายการเดิมเพื่อส่งคำขอเดิมอย่างปลอดภัย",
                                color = QgRed, fontSize = 12.sp
                            )
                            Button(
                                onClick = { onCashMovement(pendingCashMovementType ?: "cash_in") },
                                enabled = !shiftPending && !busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                            ) { Text("ตรวจรายการเดิม") }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onCashMovement("cash_in") },
                                    enabled = !shiftPending && !busy,
                                    modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                                ) { Text("เงินเข้า") }
                                OutlinedButton(
                                    onClick = { onCashMovement("cash_out") },
                                    enabled = !shiftPending && !busy,
                                    modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                                ) { Text("เงินออก") }
                            }
                        }
                        OutlinedTextField(
                            countedCash, onCountedCash,
                            label = { Text("นับเงินสดจริงเพื่อปิดกะ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = onCloseShift,
                            enabled = !busy && !shiftPending && countedCash.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                        ) { Text("ปิดกะและบันทึกยอดนับจริง") }
                    }
                }
            }
        } else {
            Text("บัญชีนี้ไม่มีสิทธิ์คิดเงิน โปรดใช้บัญชีเจ้าของร้านหรือแคชเชียร์",
                color = QgRed, fontSize = 12.sp)
        }

        Text("บิลรอคิดเงิน", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        if (bills.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) {
                Text("ยังไม่มีบิลรอชำระ", color = QgMuted)
            }
        } else {
            bills.forEach { bill ->
                val ready = !showKitchenStatus || bill.kitchenStatus in setOf("READY", "SERVED")
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(bill) },
                    color = if (selected?.id == bill.id) Color(0xFFFFF2F4) else Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, if (selected?.id == bill.id) QgRed else Color(0xFFE9EAEE)
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(bill.number, fontWeight = FontWeight.ExtraBold)
                            Text(
                                if (ready) "พร้อมคิดเงิน" else "รอครัวพร้อม · " + bill.kitchenStatus,
                                color = if (ready) Color(0xFF14804A) else QgMuted,
                                fontSize = 12.sp
                            )
                        }
                        Text("฿" + money(bill.total),
                            color = QgRed, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        if (selected != null && selected in bills) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("คิดเงิน · " + selected.number, fontWeight = FontWeight.ExtraBold)
                    Text("ยอดรับชำระ", fontSize = 12.sp, color = QgMuted)
                    Text("฿" + money(selected.total), color = QgRed,
                        fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                    if (!canPaySelected) {
                        Text("บิลร้านอาหาร/คาเฟ่ต้องพร้อมจากครัวก่อนรับเงิน",
                            color = QgRed, fontSize = 12.sp)
                    }
                    Box {
                        OutlinedButton(
                            onClick = { onPaymentMenu(true) },
                            enabled = canClose && !busy && canPaySelected && shift?.open == true,
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
                    val cash = cashReceived.toDoubleOrNull()
                    val cashTenderValid = paymentMethod != "cash" ||
                        (cash != null && cash.isFinite() && cash >= selected.total)
                    if (paymentMethod == "cash") {
                        OutlinedTextField(
                            cashReceived, onCash,
                            label = { Text("เงินสดที่รับ (บาท)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = { onCash(money(selected.total)) },
                            enabled = canClose && !busy && canPaySelected && shift?.open == true
                        ) { Text("รับพอดี ฿" + money(selected.total)) }
                        Text(
                            "เงินทอน " + if (cash == null || !cash.isFinite()) "—" else "฿" + money((cash - selected.total).coerceAtLeast(0.0)),
                            color = QgMuted, fontSize = 12.sp
                        )
                        if (cashReceived.isNotBlank() && !cashTenderValid) {
                            Text("เงินสดที่รับต้องไม่น้อยกว่ายอดบิล", color = QgRed, fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = onPay,
                        enabled = canClose && !busy && !shiftPending && shift?.open == true && canPaySelected && cashTenderValid,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = QgRed)
                    ) { Text("ยืนยันรับเงินและปิดบิล", fontWeight = FontWeight.Bold) }
                    if (shift?.open != true) {
                        Text("ต้องเปิดกะรับเงินด้านบนก่อนจึงจะปิดบิลได้",
                            fontSize = 12.sp, color = QgRed)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

internal fun posReportCsvCell(value: Any?): String {
    val raw = value?.toString().orEmpty()
    val startsFormula = raw.firstOrNull()?.let { ch ->
        ch == '=' || ch == '+' || ch == '-' || ch == '@' || ch == '\t' || ch == '\r'
    } == true
    val safe = if (startsFormula) "'" + raw else raw
    return "\"" + safe.replace("\"", "\"\"") + "\""
}

internal fun buildPosReportCsv(
    report: PosOwnerDashboard,
    itemSalesReport: PosItemSalesReport
): String {
    val rows = mutableListOf<List<Any?>>()
    rows.add(listOf("รายงาน POS", "ค่า"))
    rows.add(listOf("ทานที่ร้าน", report.dineIn))
    rows.add(listOf("รับกลับ", report.takeaway))
    rows.add(listOf("ยอดขายรวม", report.dineIn + report.takeaway))
    rows.add(listOf("จำนวนบิล", report.orders))
    rows.add(listOf("เฉลี่ยต่อบิล", report.averageTicket))
    rows.add(listOf("รับเงินสด", report.cashSales))
    rows.add(listOf("ส่วนลด", report.discounts))
    rows.add(listOf("คืนเงิน", report.refunds))
    rows.add(listOf("ยกเลิกบิล", report.voids))
    rows.add(listOf("เทียบช่วงก่อน (%)", report.changePercent ?: ""))
    rows.add(emptyList<Any?>())
    rows.add(listOf("ยอดขายรายชั่วโมง"))
    rows.add(listOf("ชั่วโมง", "จำนวนบิล", "ยอดขาย"))
    report.hourlySales.forEach { hourly ->
        rows.add(listOf(
            hourly.hour.toString().padStart(2, '0') + ":00",
            hourly.bills,
            hourly.sales
        ))
    }
    rows.add(emptyList<Any?>())
    rows.add(listOf("สินค้า", "จำนวน", "จำนวนบิล", "ยอดขาย (บาท)"))
    itemSalesReport.items.forEach { item ->
        rows.add(listOf(item.name, item.quantity, item.bills, item.sales))
    }
    return "\uFEFF" + rows.joinToString("\r\n") { row ->
        row.joinToString(",") { posReportCsvCell(it) }
    }
}

@Composable
private fun PosTaxSettingsView(
    settings: PosTaxSettings,
    rateText: String,
    busy: Boolean,
    onEnabled: (Boolean) -> Unit,
    onRate: (String) -> Unit,
    onPricesIncludeTax: (Boolean) -> Unit,
    onRegistration: (String) -> Unit,
    onBranch: (String) -> Unit,
    onAddress: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        QgSectionTitle("ภาษี POS", "ตั้งค่าร้าน · ใช้กับบิล POS ใหม่เท่านั้น")
        Spacer(Modifier.height(8.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = settings.enabled, onCheckedChange = onEnabled, enabled = !busy)
                Column(Modifier.weight(1f)) {
                    Text("เปิดใช้ภาษี", fontWeight = FontWeight.Bold)
                    Text("ค่าเริ่มต้นปิดไว้ ไม่เปลี่ยนบิลเก่า", color = QgMuted, fontSize = 12.sp)
                }
            }
            OutlinedTextField(
                value = rateText, onValueChange = onRate, enabled = !busy,
                label = { Text("อัตราภาษี (%)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text("รูปแบบราคาหน้าร้าน", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = settings.pricesIncludeTax, onClick = { onPricesIncludeTax(true) }, enabled = !busy)
                Column(Modifier.weight(1f)) {
                    Text("ราคารวมภาษีแล้ว")
                    Text("ยอดที่ลูกค้าเห็นคงเดิม และแยกยอดภาษีในบิล", color = QgMuted, fontSize = 11.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = !settings.pricesIncludeTax, onClick = { onPricesIncludeTax(false) }, enabled = !busy)
                Column(Modifier.weight(1f)) {
                    Text("ราคายังไม่รวมภาษี")
                    Text("ระบบบวกภาษีเพิ่มจากยอดหลังหักส่วนลด", color = QgMuted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Text("ข้อมูลใบกำกับภาษี", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = settings.registrationNumber.orEmpty(), onValueChange = onRegistration,
                enabled = !busy, label = { Text("เลขประจำตัวผู้เสียภาษี") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = settings.branchCode.orEmpty(), onValueChange = onBranch,
                enabled = !busy, label = { Text("รหัสสาขา เช่น 00000") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = settings.invoiceAddress.orEmpty(), onValueChange = onAddress,
                enabled = !busy, label = { Text("ที่อยู่สำหรับใบกำกับภาษี") },
                modifier = Modifier.fillMaxWidth(), minLines = 2
            )
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "กำลังบันทึก…" else "บันทึกการตั้งค่าภาษี")
        }
        Text(
            "รายละเอียดภาษีในใบเสร็จและรายงานต้องอ่านจากข้อมูลที่บันทึกไว้ในบิล ณ เวลาขาย",
            color = QgMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun PosReportsView(
    snapshot: PosSnapshot,
    days: Int,
    report: PosOwnerDashboard?,
    itemSalesReport: PosItemSalesReport?,
    busy: Boolean,
    onDays: (Int) -> Unit,
    onRefund: (PosBill) -> Unit,
    onExportCsv: (String) -> Unit
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
        if (report != null) {
            val changeLabel = report.changePercent?.let {
                (if (it > 0) "+" else "") + it.toString() + "%"
            } ?: "ไม่มีข้อมูลช่วงก่อน"
            QgCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    PosReportLine("ทานที่ร้าน", report.dineIn)
                    PosReportLine("รับกลับ", report.takeaway)
                    HorizontalDivider()
                    PosReportLine("ยอดขายรวม", report.dineIn + report.takeaway, true)
                    Text("จำนวนบิล " + report.orders, color = QgMuted, fontSize = 11.sp)
                    PosReportLine("เฉลี่ยต่อบิล", report.averageTicket)
                    PosReportLine("รับเงินสด", report.cashSales)
                    PosReportLine("ส่วนลด", report.discounts)
                    PosReportLine("คืนเงิน", report.refunds)
                    Text("ยกเลิกบิล " + report.voids, color = QgMuted, fontSize = 11.sp)
                    Text("เทียบช่วงก่อน: " + changeLabel, color = QgMuted, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(14.dp))
            QgSectionTitle("ยอดขายรายชั่วโมง")
            Spacer(Modifier.height(7.dp))
            if (report.hourlySales.isEmpty()) {
                Text("ยังไม่มีรายการขายรายชั่วโมงในช่วงนี้", color = QgMuted, fontSize = 12.sp)
            } else {
                report.hourlySales.forEach { hourly ->
                    QgCard(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(hourly.hour.toString().padStart(2, '0') + ":00",
                                modifier = Modifier.weight(0.7f), fontWeight = FontWeight.Bold)
                            Text(hourly.bills.toString() + " บิล",
                                modifier = Modifier.weight(0.7f), color = QgMuted, fontSize = 12.sp)
                            Text("฿" + money(hourly.sales),
                                modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                QgSectionTitle("สินค้าขายดี", "ยอดขายรายสินค้า POS · ตามช่วงเวลาที่เลือก")
            }
            if (report != null && itemSalesReport != null && itemSalesReport.items.isNotEmpty()) {
                OutlinedButton(
                    onClick = { onExportCsv(buildPosReportCsv(report, itemSalesReport)) },
                    enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("ส่งออก CSV", fontSize = 11.sp) }
            }
        }
        if (itemSalesReport == null) {
            Text("กำลังโหลดสรุปยอดขายรายสินค้า", color = QgMuted, fontSize = 12.sp)
        } else if (itemSalesReport.items.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) {
                Text("ยังไม่มีรายการสินค้าที่ชำระแล้วในช่วงนี้", color = QgMuted)
            }
        } else {
            itemSalesReport.items.forEach { item ->
                QgCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(item.name, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth()) {
                            Text("ขาย " + item.quantity.toString() + " · " +
                                item.bills + " บิล", modifier = Modifier.weight(1f),
                                color = QgMuted, fontSize = 12.sp)
                            Text("฿" + money(item.sales), color = QgRed,
                                fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
            Text("แสดง " + itemSalesReport.items.size + " จาก " +
                itemSalesReport.itemCount + " รายการ",
                color = QgMuted, fontSize = 11.sp)
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
private fun PosDeliveryView(
    snapshot: PosSnapshot,
    shop: MerchantShop?,
    loading: Boolean,
    busy: Boolean,
    onEnable: () -> Unit
) {
    val menuReady = snapshot.products.any { product ->
        product.deliveryAvailable &&
            (product.deliveryPrice ?: product.price).let { it.isFinite() && it >= 0.0 }
    }
    val checks = listOf(
        "ข้อมูลร้าน" to !shop?.name.isNullOrBlank(),
        "ที่อยู่และพิกัด" to (
            !shop?.address.isNullOrBlank() &&
                shop?.latitude?.isFinite() == true &&
                shop.longitude?.isFinite() == true
            ),
        "เมนูและราคา" to menuReady,
        "เวลาเปิดร้าน" to (!shop?.openTime.isNullOrBlank() && !shop?.closeTime.isNullOrBlank()),
        "อนุมัติร้าน" to (shop?.status == "active")
    )
    val ready = checks.all { it.second }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QgSectionTitle(
            "QueueGo Delivery",
            "ใช้หน้าร้าน POS ต่อได้เสมอ · ตรวจความพร้อมก่อนเปิดรับ Delivery"
        )
        if (loading && shop == null) {
            QgCard(Modifier.fillMaxWidth()) { Text("กำลังตรวจข้อมูลร้าน…", color = QgMuted) }
        }
        checks.forEach { (label, complete) ->
            QgCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (complete) "✓" else "○",
                        color = if (complete) QgRed else QgMuted,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(
                        if (complete) "พร้อมแล้ว" else "ยังไม่พร้อม",
                        color = if (complete) QgRed else QgMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text("สถานะรับ Delivery", color = QgMuted, fontSize = 10.sp)
                Text(
                    if (shop?.deliveryEnabled == true) "เปิด" else "ปิด",
                    color = if (shop?.deliveryEnabled == true) QgRed else QgMuted,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        if (shop?.deliveryEnabled != true) {
            Button(
                onClick = onEnable,
                enabled = ready && !busy && !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "กำลังเปิด Delivery…" else "เปิด Delivery เมื่อพร้อม", fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                "ข้อมูลพร้อมสำหรับ Delivery · จัดการเปิด/ปิดรับงานจากหน้าหลักร้าน",
                color = QgMuted,
                fontSize = 11.sp
            )
        }
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

private fun tableQrFileName(shopName: String, tableLabel: String): String {
    val raw = "QueueGo-" + shopName + "-" + tableLabel + "-QR"
    val safe = raw.map { ch ->
        if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '-'
    }.joinToString("").trim('-').take(90)
    return (safe.ifBlank { "QueueGo-Table-QR" }) + ".png"
}

private fun printTableQr(context: Context, title: String, bitmap: Bitmap) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        ?: error("อุปกรณ์นี้ไม่มีบริการพิมพ์")
    val safeJobName = title.map { ch ->
        if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '-'
    }.joinToString("").trim('-').take(60).ifBlank { "QueueGo-Table-QR" }
    val defaultAttributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        .setResolution(PrintAttributes.Resolution("queuego-qr-300dpi", "QueueGo QR", 300, 300))
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()
    var currentAttributes = defaultAttributes
    printManager.print(
        safeJobName,
        object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal.isCanceled) {
                    callback.onLayoutCancelled()
                    return
                }
                currentAttributes = newAttributes
                val info = PrintDocumentInfo.Builder("$safeJobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal,
                callback: WriteResultCallback
            ) {
                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                    return
                }
                val document = PrintedPdfDocument(context, currentAttributes)
                try {
                    val page = document.startPage(1)
                    val pageWidth = page.info.pageWidth.toFloat()
                    val pageHeight = page.info.pageHeight.toFloat()
                    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 20f
                        textAlign = Paint.Align.CENTER
                    }
                    page.canvas.drawText(title.take(56), pageWidth / 2f, pageHeight * 0.22f, titlePaint)
                    val size = minOf(pageWidth, pageHeight) * 0.52f
                    val left = (pageWidth - size) / 2f
                    val top = pageHeight * 0.29f
                    page.canvas.drawBitmap(
                        bitmap,
                        null,
                        RectF(left, top, left + size, top + size),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    )
                    document.finishPage(page)
                    ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                        document.writeTo(output)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (failure: Exception) {
                    callback.onWriteFailed(failure.message ?: "พิมพ์ QR Code ไม่สำเร็จ")
                } finally {
                    document.close()
                }
            }
        },
        defaultAttributes
    )
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
