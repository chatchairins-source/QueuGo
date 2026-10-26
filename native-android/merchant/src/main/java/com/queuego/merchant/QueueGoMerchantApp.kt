package com.queuego.merchant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.merchantRealtimeSubscriptions
import com.queuego.shared.QgInk
import com.queuego.shared.QgLine
import com.queuego.shared.NativeOrderRealtime
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.queuego.shared.NativeAuth
import com.queuego.shared.NativePushApi
import com.queuego.shared.NativePushDeviceStore
import com.queuego.shared.nativeLogoutScope
import com.queuego.shared.QueueGoVoiceCallOverlay
import com.queuego.shared.NativeVoiceCallController
import com.queuego.shared.NativeVoicePhase
import com.queuego.shared.QgAccountDeletionSection
import com.queuego.shared.QgBg
import com.queuego.shared.QgBottomNav
import com.queuego.shared.QgCard
import com.queuego.shared.QgGreen
import com.queuego.shared.QgIcon
import com.queuego.shared.QgIconButton
import com.queuego.shared.QgMuted
import com.queuego.shared.QgMapPoint
import com.queuego.shared.QgNavItem
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import com.queuego.shared.QgSectionTitle
import com.queuego.shared.QgStatusPill
import com.queuego.shared.QueueGoAuthHost
import com.queuego.shared.QueueGoBrand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun QueueGoMerchantApp(pushTarget: NativePushLaunchTarget? = null) {
    QueueGoAuthHost(expectedRole = "shop", appLabel = "Merchant") { auth, logout ->
        MerchantShell(auth, logout, pushTarget)
    }
}

@Composable
private fun MerchantShell(
    auth: NativeAuth,
    logout: () -> Unit,
    pushTarget: NativePushLaunchTarget? = null
) {
    val api = remember { MerchantApi() }
    val scope = rememberCoroutineScope()
    val realtime = remember { NativeOrderRealtime() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val ordersMutex = remember { Mutex() }
    val notificationsMutex = remember { Mutex() }
    val routeSaver = Saver<String, String>(
        save = { merchantRestorableRoute(it) }, restore = { merchantRestorableRoute(it) }
    )
    var screen by rememberSaveable(auth.user.id, stateSaver = routeSaver) { mutableStateOf("home") }
    var shop by remember { mutableStateOf<MerchantShop?>(null) }
    var orders by remember { mutableStateOf<List<MerchantOrder>>(emptyList()) }
    var notifications by remember { mutableStateOf<List<MerchantNotification>>(emptyList()) }
    var products by remember { mutableStateOf<List<MerchantProduct>>(emptyList()) }
    var readiness by remember { mutableStateOf<ShopReadiness?>(null) }
    var shopOpen by remember { mutableStateOf(true) }
    var todayRevenue by remember { mutableStateOf(MerchantTodayRevenue(0, 0.0, 0.0, 0.0)) }
    var knownPendingIds by remember { mutableStateOf<Set<String>?>(null) }
    var selectedOrder by remember { mutableStateOf<MerchantOrder?>(null) }
    var selectedRiderContext by remember { mutableStateOf<MerchantRiderContext?>(null) }
    var selectedProduct by remember { mutableStateOf<MerchantProduct?>(null) }
    var newProductCategory by remember { mutableStateOf<String?>(null) }
    var productReturnScreen by rememberSaveable(auth.user.id, stateSaver = routeSaver) { mutableStateOf("products") }
    var groceryCatalogItems by remember { mutableStateOf<List<MerchantGroceryCatalogItem>>(emptyList()) }
    var groceryCatalogCategories by remember { mutableStateOf<List<String>>(emptyList()) }
    var groceryCatalogQuery by remember { mutableStateOf("") }
    var groceryCatalogCategory by remember { mutableStateOf("") }
    var groceryCatalogMode by remember { mutableStateOf("all") }
    var groceryCatalogOffset by remember { mutableIntStateOf(0) }
    var groceryCatalogHasMore by remember { mutableStateOf(false) }
    var groceryCatalogLoading by remember { mutableStateOf(false) }
    var groceryCatalogError by remember { mutableStateOf<String?>(null) }
    var groceryCatalogGeneration by remember { mutableIntStateOf(0) }
    val groceryOwner by rememberUpdatedState(auth.user.id)
    var orderItems by remember { mutableStateOf<List<MerchantOrderItem>>(emptyList()) }
    var gpRate by remember { mutableStateOf(0.0) }
    var loading by remember { mutableStateOf(true) }
    var initialLoadError by remember(auth.user.id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var handledPushEventId by remember { mutableStateOf<Long?>(null) }
    var pushRetrySignal by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val voiceController = remember(auth.user.id) { NativeVoiceCallController(context) }
    val voiceState by voiceController.state.collectAsState()
    DisposableEffect(voiceController) { onDispose { voiceController.close() } }
    val pushStore = remember { NativePushDeviceStore(context, "shop") }
    val pushApi = remember { NativePushApi() }
    val pushLogoutScope = remember(context) { nativeLogoutScope(context, scope) }
    var pushPermissionRequested by remember(auth.user.id) { mutableStateOf(false) }
    var pushActionBusy by remember(auth.user.id) { mutableStateOf(false) }
    var pendingPushTestAfterPermission by remember(auth.user.id) { mutableStateOf(false) }

    suspend fun runMerchantPushProfileAction(testNotification: Boolean) {
        val enabled = syncMerchantNativePush(context, auth)
        if (!enabled) {
            message = "ยังไม่ได้ตั้งค่า Firebase สำหรับการแจ้งเตือนบนเครื่องนี้"
            return
        }
        if (testNotification) {
            message = "ระบบจะส่งแจ้งเตือนทดสอบ กรุณากด Home เพื่อตรวจสอบ"
            pushApi.test(auth)
            message = "ส่งคำขอทดสอบการแจ้งเตือนแล้ว"
        } else {
            message = "เปิดการแจ้งเตือนเบื้องหลังแล้ว"
        }
    }

    val pushPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val shouldTest = pendingPushTestAfterPermission
        pendingPushTestAfterPermission = false
        if (granted) {
            pushActionBusy = true
            scope.launch {
                try {
                    runMerchantPushProfileAction(shouldTest)
                } catch (failure: kotlinx.coroutines.CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    message = failure.message ?: "เปิดการแจ้งเตือนไม่สำเร็จ"
                } finally {
                    pushActionBusy = false
                }
            }
        } else {
            message = "กรุณาอนุญาตการแจ้งเตือนเพื่อรับออเดอร์และสถานะสำคัญเบื้องหลัง"
        }
    }

    fun launchMerchantPushProfileAction(testNotification: Boolean) {
        if (pushActionBusy) return
        if (
            Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingPushTestAfterPermission = testNotification
            pushPermissionRequested = true
            pushPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        pushActionBusy = true
        scope.launch {
            try {
                runMerchantPushProfileAction(testNotification)
            } catch (failure: kotlinx.coroutines.CancellationException) {
                throw failure
            } catch (failure: Exception) {
                message = failure.message ?: if (testNotification) {
                    "ทดสอบการแจ้งเตือนไม่สำเร็จ"
                } else {
                    "เปิดการแจ้งเตือนไม่สำเร็จ"
                }
            } finally {
                pushActionBusy = false
            }
        }
    }
    fun logoutWithPushCleanup() {
        pushLogoutScope.launch {
            runCatching { pushApi.unsubscribe(auth, pushStore.deviceId()) }
        }
        logout()
    }
    LaunchedEffect(auth.user.id, auth.session.accessToken, auth.session.sessionId) {
        if (!merchantFirebaseConfigured(context)) return@LaunchedEffect
        ensureMerchantNotificationChannel(context)
        if (
            Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { syncMerchantNativePush(context, auth) }
        } else if (!pushPermissionRequested) {
            pushPermissionRequested = true
            pushPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var pendingVoiceAnswer by remember { mutableStateOf(false) }
    var pendingVoiceOrderId by remember { mutableStateOf<String?>(null) }
    val voicePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingVoiceAnswer) {
                voiceController.answerIncoming(auth)
            } else {
                pendingVoiceOrderId?.let { orderId ->
                    if (voiceController.state.value.phase in setOf(
                            NativeVoicePhase.RINGING_OUT,
                            NativeVoicePhase.INCOMING,
                            NativeVoicePhase.CONNECTING,
                            NativeVoicePhase.CONNECTED
                        )
                    ) {
                        message = "มีสาย QueueGo ที่กำลังดำเนินอยู่"
                    } else {
                        voiceController.startOutgoing(auth, orderId, "customer")
                    }
                }
            }
        } else {
            message = "กรุณาอนุญาตไมโครโฟนเพื่อโทรผ่าน QueueGo"
        }
        pendingVoiceOrderId = null
        pendingVoiceAnswer = false
    }

    fun startCustomerVoiceCall(orderId: String) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceController.startOutgoing(auth, orderId, "customer")
        } else {
            pendingVoiceOrderId = orderId
            pendingVoiceAnswer = false
            voicePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun answerVoiceCall() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceController.answerIncoming(auth)
        } else {
            pendingVoiceOrderId = null
            pendingVoiceAnswer = true
            voicePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    var setupLogoUri by remember { mutableStateOf<Uri?>(null) }
    var setupCoverUri by remember { mutableStateOf<Uri?>(null) }
    var merchantGpsPoint by remember { mutableStateOf<QgMapPoint?>(null) }
    var merchantGpsSignal by remember { mutableIntStateOf(0) }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) setupLogoUri = it
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) setupCoverUri = it
    }
    fun updateMerchantGps() {
        val point = merchantLastKnownLocation(context)
        if (point != null) {
            merchantGpsPoint = point
            merchantGpsSignal++
            message = "ใช้ตำแหน่ง GPS ปัจจุบันแล้ว"
        } else {
            message = "ยังอ่านตำแหน่ง GPS ไม่ได้"
        }
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) updateMerchantGps()
        else message = "กรุณาอนุญาตตำแหน่งเพื่อปักหมุดร้าน"
    }

    BackHandler(enabled = screen != "home") {
        screen = when (screen) {
            "shop-setup", "hours", "support" -> "profile"
            "notifications", "media", "modules", "market-stock" -> "home"
            "catalog", "grocery-catalog" -> "products"
            "market-catalog" -> "products"
            "product" -> productReturnScreen
            "order" -> "orders"
            else -> "home"
        }
        selectedOrder = null
    }

    suspend fun refreshAll() {
        loading = true
        runCatching {
            val s = api.loadShop(auth)
            orders = api.loadOrders(auth)
            notifications = runCatching { api.loadNotifications(auth) }.getOrDefault(emptyList())
            knownPendingIds = orders.filter { it.status == "pending" }.map { it.id }.toSet()
            shop = s
            if (s != null) {
                products = api.loadProducts(auth, s.id)
                readiness = api.readiness(auth, s.id)
                shopOpen = runCatching { api.shopOpenState(auth, s.id) }.getOrDefault(true)
                val today = LocalDate.now(ZoneId.of("Asia/Bangkok")).toString()
                todayRevenue = runCatching { api.todayRevenue(auth, today) }.getOrDefault(MerchantTodayRevenue(0, 0.0, 0.0, 0.0))
                gpRate = runCatching { api.effectiveGp(auth, s.id) }.getOrDefault(0.0)
            }
            initialLoadError = null
        }.onFailure {
            if (it is kotlinx.coroutines.CancellationException) throw it
            if (shop == null) initialLoadError = it.message ?: "เชื่อมต่อไม่ได้"
            else message = it.message ?: "โหลดข้อมูลร้านไม่สำเร็จ"
        }
        loading = false
    }

    suspend fun refreshGroceryCatalog() {
        val activeShop = shop ?: return
        if (activeShop.category != "grocery") return
        val generation = ++groceryCatalogGeneration
        val owner = auth.user.id
        fun current() = generation == groceryCatalogGeneration && groceryOwner == owner &&
            shop?.id == activeShop.id && screen == "grocery-catalog"
        groceryCatalogLoading = true
        groceryCatalogError = null
        try {
            if (groceryCatalogCategories.isEmpty()) {
                val categories = api.groceryCatalogCategories(auth, activeShop.id)
                if (!current()) return
                groceryCatalogCategories = categories
            }
            val page = api.groceryCatalogPage(auth, activeShop.id, groceryCatalogQuery,
                groceryCatalogCategory, groceryCatalogMode, groceryCatalogOffset)
            if (!current()) return
            groceryCatalogItems = page.items
            groceryCatalogHasMore = page.hasMore
        } catch (failure: kotlinx.coroutines.CancellationException) {
            throw failure
        } catch (failure: Exception) {
            if (current()) {
                groceryCatalogError = failure.message ?: "โหลดแคตตาล็อกสินค้าไม่สำเร็จ"
                groceryCatalogItems = emptyList()
                groceryCatalogHasMore = false
            }
        } finally {
            if (generation == groceryCatalogGeneration) groceryCatalogLoading = false
        }
    }

    fun playMerchantNotificationSound() {
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }
            .getOrNull() ?: return
        val started = runCatching {
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 650)
        }.getOrDefault(false)
        if (!started) {
            runCatching { tone.release() }
            return
        }
        scope.launch {
            try {
                delay(750)
            } finally {
                runCatching { tone.release() }
            }
        }
    }

    suspend fun refreshOrders() = ordersMutex.withLock {
        runCatching { api.loadOrders(auth) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .onSuccess { fresh ->
                val pendingNow = fresh.filter { it.status == "pending" }.map { it.id }.toSet()
                val before = knownPendingIds
                if (before != null && pendingNow.any { it !in before }) {
                    playMerchantNotificationSound()
                }
                knownPendingIds = pendingNow
                orders = fresh
                selectedOrder = selectedOrder?.let { old -> fresh.find { it.id == old.id } ?: old }
            }
        if (screen == "order" && selectedOrder != null) {
            runCatching { api.loadOrderItems(auth, selectedOrder!!.id) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .onSuccess { orderItems = it }
        }
    }

    LaunchedEffect(
        screen,
        selectedOrder?.id,
        selectedOrder?.riderId,
        selectedOrder?.status,
        auth.session.accessToken
    ) {
        val order = selectedOrder
        selectedRiderContext = if (
            screen == "order" &&
            order != null &&
            !order.riderId.isNullOrBlank()
        ) {
            runCatching { api.loadOrderRider(auth, order.id) }.getOrNull()
        } else {
            null
        }
    }

    suspend fun refreshNotifications() = notificationsMutex.withLock {
        runCatching { api.loadNotifications(auth) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .onSuccess { notifications = it }
    }

    LaunchedEffect(auth.session.accessToken, shop?.id, lifecycle) {
        val shopId = shop?.id ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            realtime.changes(
                auth.session.accessToken,
                merchantRealtimeSubscriptions(auth.user.id, shopId)
            ).collect {
                refreshOrders()
                refreshNotifications()
            }
        }
    }

    LaunchedEffect(auth.user.id) { refreshAll() }
    LaunchedEffect(pushTarget?.eventId, loading, pushRetrySignal, auth.session.accessToken) {
        val target = pushTarget ?: return@LaunchedEffect
        if (loading || handledPushEventId == target.eventId) return@LaunchedEffect
        if (target.referenceId.isBlank()) {
            handledPushEventId = target.eventId
            screen = "notifications"
            return@LaunchedEffect
        }
        try {
            val lookup = lookupMerchantNotificationOrder(
                referenceId = target.referenceId,
                current = orders,
                idOf = { it.id },
                refresh = { api.loadOrders(auth) }
            )
            orders = lookup.snapshot
            val order = lookup.order
            handledPushEventId = target.eventId
            if (order != null) {
                selectedOrder = order
                orderItems = runCatching { api.loadOrderItems(auth, order.id) }.getOrDefault(emptyList())
                screen = "order"
            } else {
                screen = if (target.type == "voice_call") "orders" else "notifications"
            }
        } catch (failure: kotlinx.coroutines.CancellationException) {
            throw failure
        } catch (_: Exception) {
            message = "กำลังเชื่อมต่อเพื่อเปิดรายการจากการแจ้งเตือน"
            delay(3_000)
            pushRetrySignal += 1
        }
    }
    LaunchedEffect(auth.session.accessToken, auth.session.sessionId, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    voiceController.refreshIncoming(auth)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Foreground polling complements push without blocking Merchant operations.
                }
                delay(3_000)
            }
        }
    }
    LaunchedEffect(screen, auth.session.accessToken, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (screen == "home" || screen == "orders" || screen == "order") {
                while (true) {
                    refreshOrders()
                    delay(3_000)
                }
            } else if (screen == "notifications") {
                while (true) {
                    refreshNotifications()
                    delay(10_000)
                }
            }
        }
    }
    LaunchedEffect(
        screen,
        groceryCatalogQuery,
        groceryCatalogCategory,
        groceryCatalogMode,
        groceryCatalogOffset,
        auth.session.accessToken
    ) {
        if (screen == "grocery-catalog") lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            refreshGroceryCatalog()
            kotlinx.coroutines.awaitCancellation()
        }
    }

    fun runAction(order: MerchantOrder, action: String, reason: String? = null) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            runCatching { api.action(auth, order.id, action, reason) }
                .onSuccess { result ->
                    message = when (action) {
                        "accepted" -> "รับออเดอร์แล้ว"
                        "preparing" -> "เริ่มเตรียมออเดอร์แล้ว"
                        "ready" -> "พร้อมส่งแล้ว"
                        "cancel" -> "ยกเลิกออเดอร์แล้ว"
                        else -> "อัปเดตสถานะแล้ว"
                    }
                    orders = runCatching { api.loadOrders(auth) }.getOrDefault(orders)
                    selectedOrder = orders.find { it.id == order.id } ?: order.copy(status = result.status)
                }
                .onFailure { message = it.message ?: "เปลี่ยนสถานะไม่สำเร็จ" }
            busy = false
        }
    }

    fun adjustReadyEstimate(order: MerchantOrder, deltaMinutes: Int) {
        if (busy) return
        val nowInstant = Instant.now()
        val current = runCatching { Instant.parse(order.readyEstimateAt ?: "") }.getOrNull()
            ?: runCatching { Instant.parse(order.preparingAt ?: "").plusSeconds(20 * 60L) }.getOrNull()
            ?: nowInstant.plusSeconds(20 * 60L)
        val requested = current.plusSeconds(deltaMinutes * 60L)
        val maximum = nowInstant.plusSeconds(2 * 60 * 60L)
        val target = when {
            requested.isBefore(nowInstant) -> nowInstant
            requested.isAfter(maximum) -> maximum
            else -> requested
        }
        busy = true
        message = null
        scope.launch {
            runCatching {
                api.setReadyEstimate(auth, order.id, target.toString(), order.readyRevision)
                api.loadOrders(auth)
            }.onSuccess { fresh ->
                orders = fresh
                selectedOrder = fresh.find { it.id == order.id } ?: selectedOrder
                message = "อัปเดตเวลาที่คาดว่าจะพร้อมแล้ว"
            }.onFailure {
                message = it.message ?: "อัปเดตเวลาพร้อมไม่สำเร็จ"
            }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
        containerColor = QgBg,
        bottomBar = {
            if (screen in setOf("home", "orders", "products", "promotions", "profile")) {
                QgBottomNav(
                    selected = screen,
                    items = listOf(
                        QgNavItem("home", "หน้าหลัก", "home"),
                        QgNavItem("orders", "คำสั่งซื้อ", "orders"),
                        QgNavItem("products", "สินค้า", "box"),
                        QgNavItem("promotions", "โปรโมชั่น", "tag"),
                        QgNavItem("profile", "บัญชี", "user")
                    )
                ) { screen = it }
            }
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).background(QgBg)) {
            MerchantTopBar(
                screen = screen,
                unreadCount = notifications.count { !it.isRead },
                onHome = { screen = "home" },
                onNotifications = { screen = "notifications" }
            )
            if (!message.isNullOrBlank()) {
                Text(
                    message!!,
                    color = if (message!!.contains("แล้ว")) QgGreen else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (initialLoadError != null) {
                QgCard(Modifier.fillMaxWidth().padding(14.dp)) {
                    Column {
                        Text("โหลดข้อมูลร้านไม่สำเร็จ", fontWeight = FontWeight.Bold)
                        Text(initialLoadError!!, color = QgMuted, modifier = Modifier.padding(vertical = 8.dp))
                        Button(onClick = { scope.launch { refreshAll() } }, enabled = !loading) {
                            Text(if (loading) "กำลังโหลดข้อมูล…" else "ลองอีกครั้ง")
                        }
                    }
                }
            } else when (screen) {
                "home" -> DashboardScreen(
                    shop = shop,
                    orders = orders,
                    loading = loading,
                    shopOpen = shopOpen,
                    busy = busy,
                    onToggleOpen = { next ->
                        val currentShop = shop
                        if (currentShop != null && !busy) {
                            busy = true
                            scope.launch {
                                runCatching { api.setShopOpen(auth, currentShop, next) }
                                    .onSuccess {
                                        shopOpen = next
                                        message = if (next) "เปิดร้านแล้ว" else "ปิดร้านชั่วคราวแล้ว"
                                    }
                                    .onFailure { message = it.message ?: "เปลี่ยนสถานะร้านไม่สำเร็จ" }
                                busy = false
                            }
                        }
                    },
                    onPause = { minutes ->
                        val currentShop = shop
                        if (currentShop != null && !busy) {
                            busy = true
                            scope.launch {
                                runCatching { api.pauseShop(auth, currentShop.id, minutes) }
                                    .onSuccess {
                                        shopOpen = false
                                        message = "พักรับออเดอร์แล้ว"
                                    }
                                    .onFailure { message = it.message ?: "พักร้านไม่สำเร็จ" }
                                busy = false
                            }
                        }
                    },
                    onPos = { screen = "pos" },
                    onOrders = { screen = "orders" },
                    onProducts = { screen = "products" },
                    onLaundry = { screen = "laundry" },
                    onRevenue = { screen = "revenue" },
                    onPromotion = { screen = "promotions" },
                    onMedia = { screen = "media" },
                    onSupport = { screen = "support" },
                    onMarketStock = { screen = "market-stock" }
                )
                "orders" -> MerchantOrdersScreen(orders, loading) {
                    selectedOrder = it
                    selectedRiderContext = null
                    orderItems = emptyList()
                    screen = "order"
                }
                "order" -> MerchantOrderDetail(
                    order = selectedOrder,
                    rider = selectedRiderContext,
                    items = orderItems,
                    products = products,
                    busy = busy,
                    canStartCustomerCall = voiceState.phase !in setOf(
                        NativeVoicePhase.RINGING_OUT,
                        NativeVoicePhase.INCOMING,
                        NativeVoicePhase.CONNECTING,
                        NativeVoicePhase.CONNECTED
                    ),
                    onBack = {
                        selectedRiderContext = null
                        screen = "orders"
                    },
                    onReportIssue = { if (selectedOrder?.let { merchantOrderCanReportIssue(it.status) } == true) screen = "order-report" },
                    onAction = { action, reason -> selectedOrder?.let { runAction(it, action, reason) } },
                    onCallCustomer = { selectedOrder?.let { startCustomerVoiceCall(it.id) } },
                    onAdjustReady = { delta -> selectedOrder?.let { adjustReadyEstimate(it, delta) } },
                    onSaveItems = { draft ->
                        val current = selectedOrder
                        if (current != null && !busy) {
                            busy = true
                            scope.launch {
                                runCatching {
                                    api.editOrderItems(auth, current.id, draft)
                                    val freshOrders = api.loadOrders(auth)
                                    val freshItems = api.loadOrderItems(auth, current.id)
                                    orders = freshOrders
                                    selectedOrder = freshOrders.find { it.id == current.id } ?: current
                                    orderItems = freshItems
                                }.onSuccess {
                                    message = "อัปเดตรายการสินค้าแล้ว"
                                }.onFailure {
                                    message = it.message ?: "แก้ไขออเดอร์ไม่สำเร็จ"
                                }
                                busy = false
                            }
                        }
                    }
                )
                "products" -> MerchantProductsScreen(
                    products = products,
                    shopCategory = shop?.category ?: "other",
                    busy = busy,
                    onToggle = { product, next ->
                        if (!busy) {
                            busy = true
                            scope.launch {
                                runCatching { api.setProductAvailable(auth, product.id, next) }
                                    .onSuccess {
                                        products = products.map {
                                            if (it.id == product.id) it.copy(available = next) else it
                                        }
                                        message = if (next) "เปิดขายสินค้าแล้ว" else "ปิดขายสินค้าแล้ว"
                                    }
                                    .onFailure { message = it.message ?: "เปลี่ยนสถานะสินค้าไม่สำเร็จ" }
                                busy = false
                            }
                        }
                    },
                    onOpen = { product ->
                        selectedProduct = product
                        newProductCategory = null
                        productReturnScreen = "products"
                        screen = "product"
                    },
                    onAddManual = {
                        selectedProduct = null
                        newProductCategory = null
                        productReturnScreen = "products"
                        screen = "product"
                    },
                    onOpenCatalog = {
                        if (shop?.category == "grocery") {
                            groceryCatalogQuery = ""
                            groceryCatalogCategory = ""
                            groceryCatalogMode = "all"
                            groceryCatalogOffset = 0
                            screen = "grocery-catalog"
                        } else if (merchantMarketStockPermitted(shop?.category)) {
                            screen = "market-catalog"
                        } else {
                            selectedProduct = null
                            newProductCategory = null
                            screen = "catalog"
                        }
                    }
                )
                "market-catalog" -> {
                    val activeShop = shop
                    if (activeShop != null && merchantMarketStockPermitted(activeShop.category)) {
                        key(auth.user.id, activeShop.id) {
                            MerchantMarketCatalogScreen(auth, activeShop, api,
                                onSaved = {
                                    scope.launch {
                                        try {
                                            val fresh = api.loadProducts(auth, activeShop.id)
                                            if (groceryOwner == auth.user.id && shop?.id == activeShop.id) products = fresh
                                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                        catch (_: Exception) { message = "บันทึกแล้ว แต่ยังโหลดรายการในร้านไม่ได้" }
                                    }
                                }, onBack = { screen = "products" })
                        }
                    } else Text("หน้านี้ใช้สำหรับร้านตลาด เนื้อ ปลา ผัก หรือผลไม้", color = QgMuted)
                }
                "grocery-catalog" -> {
                    val activeShop = shop
                    if (activeShop == null || activeShop.category != "grocery") {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("หน้านี้ใช้สำหรับร้านขายของชำ", color = QgMuted)
                        }
                    } else {
                        MerchantGroceryCatalogScreen(
                            items = groceryCatalogItems,
                            categories = groceryCatalogCategories,
                            query = groceryCatalogQuery,
                            category = groceryCatalogCategory,
                            mode = groceryCatalogMode,
                            offset = groceryCatalogOffset,
                            hasMore = groceryCatalogHasMore,
                            gpRate = gpRate,
                            loading = groceryCatalogLoading,
                            busy = busy,
                            error = groceryCatalogError,
                            onRetry = { if (!busy && !groceryCatalogLoading) scope.launch { refreshGroceryCatalog() } },
                            onSearch = {
                                groceryCatalogQuery = it
                                groceryCatalogOffset = 0
                            },
                            onCategory = {
                                groceryCatalogCategory = it
                                groceryCatalogOffset = 0
                            },
                            onMode = {
                                groceryCatalogMode = it
                                groceryCatalogOffset = 0
                            },
                            onPage = { groceryCatalogOffset = it.coerceAtLeast(0) },
                            onSave = { item, draft ->
                                if (!busy) {
                                    busy = true
                                    scope.launch {
                                        var confirmed = false
                                        try {
                                            api.saveGroceryCatalogItem(auth, activeShop.id, item, draft)
                                            confirmed = true
                                            if (groceryOwner == auth.user.id && shop?.id == activeShop.id) {
                                                message = "เพิ่มสินค้าเข้าร้านแล้ว"
                                            }
                                        } catch (failure: kotlinx.coroutines.CancellationException) {
                                            throw failure
                                        } catch (failure: Exception) {
                                            message = "ยังยืนยันการบันทึกไม่ได้ กรุณาตรวจรายการในร้านก่อนลองใหม่: " + failure.message
                                        } finally {
                                            if (!confirmed) busy = false
                                        }
                                        if (confirmed) {
                                            try {
                                                val freshProducts = api.loadProducts(auth, activeShop.id)
                                                if (groceryOwner == auth.user.id && shop?.id == activeShop.id) products = freshProducts
                                                if (screen == "grocery-catalog") refreshGroceryCatalog()
                                                if (groceryCatalogError != null) message = "บันทึกแล้ว แต่ยังโหลดข้อมูลล่าสุดไม่ได้"
                                            } catch (failure: kotlinx.coroutines.CancellationException) {
                                                throw failure
                                            } catch (_: Exception) {
                                                message = "บันทึกแล้ว แต่ยังโหลดข้อมูลล่าสุดไม่ได้"
                                            } finally { busy = false }
                                        }
                                    }
                                }
                            },
                            onBack = { screen = "products" }
                        )
                    }
                }
                "catalog" -> {
                    val activeShop = shop
                    if (activeShop == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("ยังไม่พบข้อมูลร้าน", color = QgMuted)
                        }
                    } else {
                        MerchantCatalogScreen(
                            shop = activeShop,
                            products = products,
                            onOpenProduct = { product ->
                                selectedProduct = product
                                newProductCategory = null
                                productReturnScreen = "catalog"
                                screen = "product"
                            },
                            onAddProduct = { category ->
                                selectedProduct = null
                                newProductCategory = category
                                productReturnScreen = "catalog"
                                screen = "product"
                            },
                            onBack = { screen = "products" }
                        )
                    }
                }
                "product" -> {
                    val activeShop = shop
                    if (activeShop == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("ยังไม่พบข้อมูลร้าน", color = QgMuted)
                        }
                    } else {
                        MerchantProductEditorScreen(
                            shop = activeShop,
                            product = selectedProduct,
                            initialCategory = newProductCategory,
                            gpRate = gpRate,
                            busy = busy,
                            onSave = { draft, imageUri ->
                                if (!busy) {
                                    busy = true
                                    scope.launch {
                                        runCatching {
                                            val image = imageUri?.let {
                                                uploadMerchantImage(context, auth, it, "product")
                                            }
                                            api.saveProduct(
                                                auth = auth,
                                                shopId = activeShop.id,
                                                product = selectedProduct,
                                                draft = draft.copy(
                                                    image = image ?: draft.image ?: selectedProduct?.image
                                                ),
                                                gpRate = gpRate
                                            )
                                            api.loadProducts(auth, activeShop.id)
                                        }.onSuccess { fresh ->
                                            products = fresh
                                            readiness = runCatching {
                                                api.readiness(auth, activeShop.id)
                                            }.getOrDefault(readiness)
                                            selectedProduct = null
                                            newProductCategory = null
                                            message = "บันทึกสินค้าแล้ว"
                                            screen = productReturnScreen
                                        }.onFailure {
                                            message = it.message ?: "บันทึกสินค้าไม่สำเร็จ"
                                        }
                                        busy = false
                                    }
                                }
                            },
                            onAdjustInventory = { intent ->
                                if (busy) error("กำลังบันทึกสินค้า")
                                busy = true
                                try {
                                    val receipt = api.adjustInventoryStock(auth, intent)
                                    val fresh = api.loadProducts(auth, activeShop.id)
                                    products = fresh
                                    selectedProduct = fresh.firstOrNull { it.id == intent.productId } ?: selectedProduct
                                    receipt
                                } finally { busy = false }
                            },
                            onLoadInventoryHistory = { product, beforeAt, beforeId ->
                                api.loadInventoryAdjustmentHistory(auth, product.id, beforeAt, beforeId)
                            },
                            onDelete = { product ->
                                if (!busy) {
                                    busy = true
                                    scope.launch {
                                        runCatching {
                                            api.deleteOrArchiveProduct(auth, product.id)
                                            api.loadProducts(auth, activeShop.id)
                                        }.onSuccess { fresh ->
                                            products = fresh
                                            selectedProduct = null
                                            newProductCategory = null
                                            message = "นำสินค้าออกจากร้านแล้ว"
                                            screen = productReturnScreen
                                        }.onFailure {
                                            message = it.message ?: "นำสินค้าออกจากร้านไม่สำเร็จ"
                                        }
                                        busy = false
                                    }
                                }
                            },
                            onBack = {
                                selectedProduct = null
                                newProductCategory = null
                                screen = productReturnScreen
                            }
                        )
                    }
                }
                "pos" -> MerchantPosScreen(auth = auth, onBack = { screen = "home" })
                "laundry" -> MerchantLaundryScreen(auth)
                "revenue" -> MerchantRevenueScreen(
                    auth = auth,
                    api = api,
                    shopName = shop?.name.orEmpty(),
                    onBack = { screen = "home" },
                    onSettings = { screen = "profile" }
                )
                "promotions" -> {
                    val activeShop = shop
                    if (activeShop == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("ยังไม่พบข้อมูลร้าน", color = QgMuted)
                        }
                    } else {
                        MerchantPromotionScreen(
                            auth = auth,
                            shop = activeShop,
                            api = api
                        )
                    }
                }
                "notifications" -> MerchantNotificationsScreen(
                    notifications = notifications,
                    onBack = { screen = "home" },
                    onMarkAllRead = {
                        if (notifications.any { !it.isRead }) {
                            notifications = notifications.map { it.copy(isRead = true) }
                            scope.launch {
                                runCatching { api.markNotificationsRead(auth) }
                                    .onFailure { refreshNotifications() }
                            }
                        }
                    },
                    onTestSound = {
                        playMerchantNotificationSound()
                        message = "ทดสอบเสียงแจ้งเตือนแล้ว"
                    },
                    onOpenOrder = { orderId ->
                        scope.launch {
                            try {
                                val lookup = lookupMerchantNotificationOrder(
                                    referenceId = orderId,
                                    current = orders,
                                    idOf = { it.id },
                                    refresh = { api.loadOrders(auth) }
                                )
                                orders = lookup.snapshot
                                val current = lookup.order
                                if (current != null) {
                                    selectedOrder = current
                                    orderItems = runCatching { api.loadOrderItems(auth, current.id) }
                                        .getOrDefault(emptyList())
                                    screen = "order"
                                } else {
                                    message = "ไม่พบออเดอร์ที่อ้างอิงจากการแจ้งเตือน"
                                    screen = "orders"
                                }
                            } catch (failure: kotlinx.coroutines.CancellationException) {
                                throw failure
                            } catch (_: Exception) {
                                message = "เชื่อมต่อออเดอร์ไม่สำเร็จ กรุณาแตะการแจ้งเตือนอีกครั้ง"
                            }
                        }
                    }
                )
                "market-stock" -> {
                    val activeShop = shop
                    if (activeShop == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("ยังไม่พบข้อมูลร้าน", color = QgMuted)
                        }
                    } else {
                        MerchantMarketStockScreen(
                            auth = auth,
                            shop = activeShop,
                            api = api,
                            onBack = { screen = "home" }
                        )
                    }
                }
                "media" -> MerchantMediaScreen(
                    auth = auth,
                    onBack = { screen = "home" }
                )
                "modules" -> MerchantModulesScreen(
                    auth = auth,
                    api = api,
                    onBack = { screen = "home" },
                    onSetup = { screen = "shop-setup" },
                    onHours = { screen = "hours" },
                    onLaundry = { screen = "laundry" }
                )
                "support" -> MerchantSupportScreen(auth) { screen = "profile" }
                "order-report" -> selectedOrder?.let { order ->
                    MerchantSupportTicketsScreen(auth, orders, order.id, order.number) { screen = "order" }
                } ?: run { screen = "orders" }
                "profile" -> MerchantProfileScreen(
                    auth = auth,
                    shop = shop,
                    shopOpen = shopOpen,
                    onSetup = {
                        setupLogoUri = null
                        setupCoverUri = null
                        merchantGpsPoint = null
                        screen = "shop-setup"
                    },
                    onHours = { screen = "hours" },
                    onProducts = { screen = "products" },
                    onModules = { screen = "modules" },
                    pushBusy = pushActionBusy,
                    onEnableBackgroundNotifications = {
                        launchMerchantPushProfileAction(false)
                    },
                    onTestNotification = {
                        launchMerchantPushProfileAction(true)
                    },
                    onSupport = { screen = "support" },
                    logout = ::logoutWithPushCleanup
                )
                "hours" -> {
                    val activeShop = shop
                    if (activeShop == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("ยังไม่พบข้อมูลร้าน", color = QgMuted)
                        }
                    } else {
                        MerchantHoursScreen(
                            auth = auth,
                            shop = activeShop,
                            api = api,
                            onBack = { screen = "profile" },
                            onSaved = {
                                scope.launch {
                                    shop = runCatching { api.loadShop(auth) }.getOrDefault(shop)
                                    readiness = runCatching { api.readiness(auth, activeShop.id) }.getOrDefault(readiness)
                                }
                            }
                        )
                    }
                }
                "shop-setup" -> {
                    val activeShop = shop
                    if (activeShop == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("ยังไม่พบข้อมูลร้าน", color = QgMuted)
                        }
                    } else {
                        MerchantShopSetupScreen(
                            shop = activeShop,
                            contactName = auth.user.name,
                            busy = busy,
                            gpsPoint = merchantGpsPoint,
                            gpsSignal = merchantGpsSignal,
                            logoUri = setupLogoUri,
                            coverUri = setupCoverUri,
                            onPickLogo = { logoPicker.launch("image/*") },
                            onPickCover = { coverPicker.launch("image/*") },
                            onGps = {
                                if (merchantHasLocationPermission(context)) {
                                    updateMerchantGps()
                                } else {
                                    locationPermission.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            searchPlaces = { query -> api.searchPlaces(query) },
                            resolveAddress = { latitude, longitude ->
                                api.reverseGeocode(latitude, longitude)
                            },
                            onSave = { draft ->
                                if (!busy) {
                                    busy = true
                                    message = null
                                    scope.launch {
                                        runCatching {
                                            val logo = setupLogoUri?.let {
                                                uploadMerchantImage(context, auth, it, "logo")
                                            }
                                            val cover = setupCoverUri?.let {
                                                uploadMerchantImage(context, auth, it, "cover")
                                            }
                                            val saved = api.saveShopSetup(
                                                auth,
                                                activeShop,
                                                draft.copy(
                                                    logo = logo ?: activeShop.logo,
                                                    cover = cover ?: activeShop.cover
                                                )
                                            )
                                            val nextReadiness = api.readiness(auth, saved.id)
                                            saved to nextReadiness
                                        }.onSuccess { (saved, nextReadiness) ->
                                            shop = saved
                                            readiness = nextReadiness
                                            setupLogoUri = null
                                            setupCoverUri = null
                                            merchantGpsPoint = null
                                            message = "บันทึกข้อมูลร้านแล้ว"
                                            screen = "profile"
                                        }.onFailure {
                                            message = it.message ?: "บันทึกข้อมูลร้านไม่สำเร็จ"
                                        }
                                        busy = false
                                    }
                                }
                            },
                            onBack = { screen = "profile" }
                        )
                    }
                }
            }
        }
        }
        QueueGoVoiceCallOverlay(
            state = voiceState,
            currentUserId = auth.user.id,
            onAnswer = ::answerVoiceCall,
            onDecline = { voiceController.declineIncoming(auth) },
            onHangUp = { voiceController.hangUp() },
            onDismissEnded = { voiceController.dismissTerminal() },
            onToggleMute = { voiceController.setMuted(it) },
            onToggleSpeaker = { voiceController.setSpeakerEnabled(it) }
        )
    }
}

internal fun merchantTopBarTitle(screen: String): String? =
    if (screen == "promotions") "โปรโมชั่น" else null

@Composable
private fun MerchantTopBar(
    screen: String,
    unreadCount: Int,
    onHome: () -> Unit,
    onNotifications: () -> Unit
) {
    val title = merchantTopBarTitle(screen)
    if (title != null) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFAFFFFFF))
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .height(40.dp)
        ) {
            QgIconButton(
                icon = "back",
                modifier = Modifier.align(Alignment.CenterStart),
                onClick = onHome
            )
            Text(
                title,
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF17191D),
                fontSize = MerchantPageTitleSize,
                fontWeight = FontWeight.ExtraBold
            )
        }
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFAFFFFFF))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.clickable(onClick = onHome)) {
            Text(
                "Queue",
                color = Color(0xFF17191D),
                fontSize = 18.sp,
                lineHeight = 22.sp,
                letterSpacing = (-0.4).sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Go",
                color = QgRed,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                letterSpacing = (-0.4).sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.weight(1f))
        QgIconButton(
            icon = "bell",
            badge = unreadCount,
            onClick = onNotifications
        )
    }
}

@Composable
private fun DashboardScreen(
    shop: MerchantShop?,
    orders: List<MerchantOrder>,
    loading: Boolean,
    shopOpen: Boolean,
    busy: Boolean,
    onToggleOpen: (Boolean) -> Unit,
    onPause: (Int) -> Unit,
    onPos: () -> Unit,
    onOrders: () -> Unit,
    onProducts: () -> Unit,
    onLaundry: () -> Unit,
    onRevenue: () -> Unit,
    onPromotion: () -> Unit,
    onMedia: () -> Unit,
    onSupport: () -> Unit,
    onMarketStock: () -> Unit
) {
    val todayOrders = merchantDashboardSummary(orders)
    val orderCountsLoading = loading && orders.isEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        if (shop != null) {
            QgCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp)) {
                        QgRemoteImage(shop.logo ?: shop.cover, Modifier.fillMaxSize(), shop.name, maxDecodeSide = 256)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            shop.name,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            if (shopOpen) "เปิดรับออเดอร์" else "ปิดร้านชั่วคราว",
                            color = if (shopOpen) QgGreen else QgMuted,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(
                        checked = shopOpen,
                        onCheckedChange = onToggleOpen,
                        enabled = !busy
                    )
                }
            }

        }

        if (shop != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("พักรับออเดอร์:", color = QgMuted, fontSize = 9.5.sp)
                MerchantPauseButton("30 นาที", busy) { onPause(30) }
                MerchantPauseButton("1 ชั่วโมง", busy) { onPause(60) }
                MerchantPauseButton("1 วัน", busy) { onPause(1440) }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "สรุปออเดอร์วันนี้",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(merchantDashboardDateLabel(), color = QgMuted, style = MaterialTheme.typography.labelSmall)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MerchantMetricCard("ออเดอร์ทั้งหมด", if (orderCountsLoading) "…" else todayOrders.all.toString(), "orders", Modifier.weight(1f))
            MerchantMetricCard("เสร็จแล้ว", if (orderCountsLoading) "…" else todayOrders.completed.toString(), "clock", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MerchantMetricCard("กำลังเตรียม", if (orderCountsLoading) "…" else todayOrders.preparing.toString(), "box", Modifier.weight(1f))
            MerchantMetricCard("ยกเลิก", if (orderCountsLoading) "…" else todayOrders.cancelled.toString(), "close", Modifier.weight(1f))
        }

        Text(
            "เมนูหลัก",
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 14.dp),
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleMedium
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MerchantMenuTile("หน้าร้าน POS", "store", onPos, Modifier.weight(1f))
            MerchantMenuTile("จัดการคำสั่งซื้อ", "orders", onOrders, Modifier.weight(1f))
            MerchantMenuTile("จัดการสินค้า", "box", onProducts, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MerchantMenuTile("โปรโมชั่น", "tag", onPromotion, Modifier.weight(1f))
            MerchantMenuTile("รายงาน", "chart", onRevenue, Modifier.weight(1f))
            MerchantMenuTile("คลังรูปภาพ", "gallery", onMedia, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MerchantMenuTile("ติดต่อแอดมิน", "support", onSupport, Modifier.weight(1f))
            MerchantMenuTile("ฝากซัก", "laundry", onLaundry, Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }

        if (merchantMarketStockPermitted(shop?.category)) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MerchantMenuTile("ตลาดและสต๊อก", "box", onMarketStock, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun MerchantPauseButton(
    label: String,
    busy: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .background(Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, Color(0xFFD6EADF), RoundedCornerShape(18.dp))
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) {
        Text(label, color = Color(0xFF447263), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MerchantMetricCard(
    label: String,
    value: String,
    icon: String,
    modifier: Modifier
) {
    QgCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(Color(0xFFFFF0F3), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                QgIcon(icon, Modifier.size(20.dp), QgRed)
            }
            Spacer(Modifier.width(9.dp))
            Column {
                Text(label, color = QgMuted, style = MaterialTheme.typography.labelSmall)
                Text(value, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun MerchantMenuTile(
    label: String,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier
) {
    QgCard(
        modifier
            .height(96.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(Color(0xFFFFF0F3), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                QgIcon(icon, Modifier.size(20.dp), QgRed)
            }
            Spacer(Modifier.height(7.dp))
            Text(
                label,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun MerchantOrdersScreen(orders: List<MerchantOrder>, loading: Boolean, onOpen: (MerchantOrder) -> Unit) {
    var selectedFilter by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    val filters = listOf(
        "all" to "ทั้งหมด", "pending" to "รอรับ", "preparing" to "กำลังเตรียม",
        "searching" to "กำลังหาไรเดอร์", "delivering" to "กำลังส่ง", "done" to "สำเร็จ"
    )
    val visibleOrders = orders.filter { order ->
        (selectedFilter == "all" || merchantOrderFilterBucket(order.status, order.riderArrivedCustomerAt) == selectedFilter) &&
            merchantOrderSearchMatches(order, query)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        QgSectionTitle("คำสั่งซื้อ", "ดูสถานะและจัดการออเดอร์ของร้าน")
        Spacer(Modifier.height(10.dp))
        if (loading && orders.isEmpty()) CircularProgressIndicator() else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filters.forEach { (filter, label) ->
                    if (selectedFilter == filter) Button(onClick = { selectedFilter = filter }) {
                        Text(label, fontSize = 11.sp)
                    } else OutlinedButton(onClick = { selectedFilter = filter }) {
                        Text(label, fontSize = 11.sp)
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { QgIcon("search", Modifier.size(18.dp), QgMuted) },
                placeholder = { Text("ค้นหาด้วยเลขออเดอร์ หรือชื่อลูกค้า") }
            )
            Spacer(Modifier.height(10.dp))
            if (orders.isEmpty()) {
                QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่มีคำสั่งซื้อ", color = QgMuted) }
            } else if (visibleOrders.isEmpty()) {
                QgCard(Modifier.fillMaxWidth()) { Text("ไม่พบคำสั่งซื้อ", color = QgMuted) }
            } else visibleOrders.forEach { MerchantOrderCard(it, onOpen) }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun MerchantOrderCard(order: MerchantOrder, onOpen: (MerchantOrder) -> Unit) {
    QgCard(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onOpen(order) }) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(order.number, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                QgStatusPill(merchantStatus(order.status, order.riderArrivedCustomerAt), order.status !in setOf("cancelled", "no_rider_available"))
            }
            Spacer(Modifier.height(6.dp))
            Text("ลูกค้า: " + (order.customerName ?: "ลูกค้า"), color = QgMuted, style = MaterialTheme.typography.bodySmall)
            Text("${order.itemCount} รายการ • ฿" + "%.0f".format(order.subtotal), fontWeight = FontWeight.Bold)
            merchantOrderTimeLabel(order.createdAt).takeIf { it.isNotBlank() }?.let {
                Text(it, color = QgMuted, style = MaterialTheme.typography.labelSmall)
            }
            com.queuego.shared.cleanOrderDisplayNote(order.note)?.let { note ->
                Text(note, color = QgMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MerchantOrderDetail(
    order: MerchantOrder?,
    rider: MerchantRiderContext?,
    items: List<MerchantOrderItem>,
    products: List<MerchantProduct>,
    busy: Boolean,
    canStartCustomerCall: Boolean,
    onBack: () -> Unit,
    onReportIssue: () -> Unit,
    onAction: (String, String?) -> Unit,
    onCallCustomer: () -> Unit,
    onAdjustReady: (Int) -> Unit,
    onSaveItems: (List<MerchantOrderEditItem>) -> Unit
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var cancelOpen by remember(order?.id) { mutableStateOf(false) }
    var editOpen by remember(order?.id) { mutableStateOf(false) }
    LaunchedEffect(order?.status, order?.preparingAt) {
        while (order?.status == "preparing") {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("ย้อนกลับ") }
        Spacer(Modifier.height(12.dp))
        if (order == null) {
            Text("ไม่พบออเดอร์")
            return
        }
        Text(order.number, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        QgStatusPill(merchantStatus(order.status, order.riderArrivedCustomerAt), order.status !in setOf("cancelled", "no_rider_available"))
        if (merchantOrderShowsProgress(order.status)) {
            Spacer(Modifier.height(12.dp))
            QgCard(Modifier.fillMaxWidth()) {
                val stageLabels = listOf("ออเดอร์เข้า", "ร้านรับ", "เตรียมสินค้า", "กำลังจัดส่ง", "สำเร็จ")
                val stage = merchantOrderProgressStage(order.status)
                Row(Modifier.fillMaxWidth()) {
                    stageLabels.forEachIndexed { index, label ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(16.dp)
                                    .background(if (index <= stage) QgRed else QgLine, CircleShape)
                            )
                            Text(label, color = if (index <= stage) QgInk else QgMuted, fontSize = 8.sp, lineHeight = 10.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text("ลูกค้า", color = QgMuted, style = MaterialTheme.typography.labelSmall)
                Text(order.customerName?.takeIf(String::isNotBlank) ?: "ลูกค้า", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("ที่อยู่จัดส่ง", color = QgMuted, style = MaterialTheme.typography.labelSmall)
                Text(order.deliveryAddress?.takeIf(String::isNotBlank) ?: "-")
                if (order.status !in setOf("completed", "delivered", "cancelled", "no_rider_available")) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onCallCustomer,
                        enabled = !busy && canStartCustomerCall,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("โทรหาลูกค้าผ่าน QueueGo") }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                val itemQuantity = items.sumOf { it.quantity.coerceAtLeast(0) }
                Text("รายการสินค้า · $itemQuantity ชิ้น", fontWeight = FontWeight.ExtraBold)
                if (items.isEmpty()) Text("กำลังโหลดรายการ...", color = QgMuted)
                else items.forEach {
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        QgRemoteImage(it.image, Modifier.size(48.dp), it.name, maxDecodeSide = 192)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(it.name, fontWeight = FontWeight.Bold)
                            if (!it.description.isNullOrBlank()) Text(it.description!!, color = QgMuted, style = MaterialTheme.typography.bodySmall)
                            Text("จำนวน " + it.quantity)
                        }
                        Text("฿" + "%.0f".format(it.totalPrice), fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider()
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (order.status !in setOf("pending", "pending_shop", "cancelled")) {
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("ยอดที่ร้านได้รับจาก Rider", color = QgMuted)
                    Text("฿" + "%.0f".format(order.subtotal), color = QgRed, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
                    Text("เงินสดที่ Rider จ่ายร้านเมื่อรับสินค้า", color = QgMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        rider?.let { assignedRider ->
            Spacer(Modifier.height(10.dp))
            QgCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QgRemoteImage(
                        assignedRider.photo,
                        Modifier.size(58.dp),
                        assignedRider.name,
                        maxDecodeSide = 256
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Rider ที่รับงาน", color = QgMuted, fontSize = 9.sp)
                        Text(assignedRider.name, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        val vehicle = listOfNotNull(
                            assignedRider.vehicleType?.takeIf { it.isNotBlank() },
                            assignedRider.vehiclePlate?.takeIf { it.isNotBlank() }
                        ).joinToString(" · ").ifBlank { "QueueGo Rider" }
                        Text(vehicle, color = QgMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                        if (assignedRider.vehicleVerified) {
                            Text(
                                "ยืนยันข้อมูลรถแล้ว",
                                color = QgGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        assignedRider.phone?.let { phone ->
                            Text(phone, color = QgMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                    assignedRider.phone?.let { phone ->
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_DIAL,
                                            Uri.parse("tel:" + Uri.encode(phone))
                                        )
                                    )
                                }
                            }
                        ) {
                            Text("โทร", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        when (order.status) {
            "pending" -> {
                Button(
                    onClick = { onAction("accepted", null) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) { Text("รับออเดอร์", fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { cancelOpen = true },
                    enabled = !busy && merchantDeliveryCanCancel(order.status, order.preparingAt, order.readyAt),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ปฏิเสธออเดอร์") }
            }
            "accepted" -> {
                QgCard(Modifier.fillMaxWidth()) {
                    Text("ร้านรับออเดอร์แล้ว · รอระบบจัดหา Rider", fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { cancelOpen = true },
                    enabled = !busy && merchantDeliveryCanCancel(order.status, order.preparingAt, order.readyAt),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ยกเลิกออเดอร์") }
            }
            "searching_rider" -> {
                QgCard(Modifier.fillMaxWidth()) {
                    Text("กำลังหา Rider · รอ Rider รับงานก่อนเริ่มเตรียมออเดอร์", fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "ปรับเวลาคาดว่าพร้อมได้ตอนนี้เพื่อให้ระบบเลือกงานต่อ/งานพ่วงที่ไม่ทำให้ Rider รอ",
                        color = QgMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(8.dp))
                MerchantReadyEstimateControls(order, busy, onAdjustReady)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { cancelOpen = true },
                    enabled = !busy && merchantDeliveryCanCancel(order.status, order.preparingAt, order.readyAt),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ยกเลิกออเดอร์") }
            }
            "rider_assigned", "assigned" -> {
                Button(
                    onClick = { onAction("preparing", null) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) { Text("เริ่มเตรียมออเดอร์", fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.height(8.dp))
                MerchantReadyEstimateControls(order, busy, onAdjustReady)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { editOpen = true },
                    enabled = !busy && items.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("แก้ไขรายการ") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { cancelOpen = true },
                    enabled = !busy && merchantDeliveryCanCancel(order.status, order.preparingAt, order.readyAt),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ยกเลิกออเดอร์") }
            }
            "preparing" -> {
                val left = readinessLeft(order.readyEstimateAt, order.preparingAt, now)
                Button(
                    onClick = { onAction("ready", null) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(58.dp)
                ) {
                    Text(
                        if (left > 0) "พร้อมส่ง · เหลือ " + formatSeconds(left)
                        else "พร้อมส่ง · ถึงเวลาแล้ว",
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(8.dp))
                MerchantReadyEstimateControls(order, busy, onAdjustReady)
                Spacer(Modifier.height(6.dp))
                Text("เวลานี้ส่งต่อให้ Dispatch, Rider Stack และ ETA ลูกค้า", color = QgMuted, style = MaterialTheme.typography.bodySmall)
            }
            "ready" -> QgCard(Modifier.fillMaxWidth()) {
                Text("พร้อมส่งแล้ว · รอ Rider มารับสินค้า", fontWeight = FontWeight.ExtraBold)
            }
            "picked_up" -> QgCard(Modifier.fillMaxWidth()) {
                Text("Rider รับสินค้าออกจากร้านแล้ว", fontWeight = FontWeight.ExtraBold)
            }
            "in_progress" -> QgCard(Modifier.fillMaxWidth()) {
                Text(
                    if (order.riderArrivedCustomerAt.isNullOrBlank()) "Rider กำลังจัดส่ง"
                    else "Rider ถึงลูกค้าแล้ว · รอส่งมอบ",
                    fontWeight = FontWeight.ExtraBold
                )
            }
            "completed" -> QgCard(Modifier.fillMaxWidth()) {
                Text("ออเดอร์เสร็จสมบูรณ์", color = QgGreen, fontWeight = FontWeight.ExtraBold)
            }
            "cancelled" -> QgCard(Modifier.fillMaxWidth()) {
                Text("ออเดอร์ถูกยกเลิก", color = QgMuted, fontWeight = FontWeight.ExtraBold)
            }
            "no_rider_available" -> QgCard(Modifier.fillMaxWidth()) {
                Text("ไม่พบ Rider · ออเดอร์สิ้นสุดแล้ว", color = QgMuted, fontWeight = FontWeight.ExtraBold)
            }
        }
        if (merchantOrderCanReportIssue(order.status)) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onReportIssue,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("แจ้งปัญหาออเดอร์นี้") }
        }
        Spacer(Modifier.height(30.dp))
    }

    if (editOpen && order != null) {
        MerchantOrderItemEditor(
            order = order,
            currentItems = items,
            products = products,
            busy = busy,
            onDismiss = { editOpen = false },
            onSave = { draft ->
                editOpen = false
                onSaveItems(draft)
            }
        )
    }

    if (cancelOpen && order != null) {
        MerchantCancelGuard(
            order = order,
            busy = busy,
            onDismiss = { cancelOpen = false },
            onConfirm = { reason ->
                cancelOpen = false
                onAction("cancel", reason)
            }
        )
    }
}

private data class MerchantOrderEditDraftRow(
    val itemId: String?,
    val productId: String?,
    val name: String,
    val image: String?,
    val unitPrice: Double,
    val quantity: Int
)

@Composable
private fun MerchantOrderItemEditor(
    order: MerchantOrder,
    currentItems: List<MerchantOrderItem>,
    products: List<MerchantProduct>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<MerchantOrderEditItem>) -> Unit
) {
    var draft by remember(order.id) {
        mutableStateOf(
            currentItems.map {
                MerchantOrderEditDraftRow(
                    itemId = it.id.takeIf(String::isNotBlank),
                    productId = it.productId,
                    name = it.name,
                    image = it.image,
                    unitPrice = it.unitPrice,
                    quantity = it.quantity.coerceIn(1, 99)
                )
            }
        )
    }
    var substituteId by remember(order.id) { mutableStateOf("") }
    val substituteProducts = products.filter {
        it.available && it.deliveryAvailable &&
            draft.none { row -> !row.productId.isNullOrBlank() && row.productId == it.id }
    }
    val newSubtotal = draft.sumOf { it.unitPrice * it.quantity }
    val canSave = !busy && draft.isNotEmpty() && draft.size <= 30 &&
        draft.all { it.quantity in 1..99 }

    LaunchedEffect(order.status) {
        if (order.status !in setOf("rider_assigned", "assigned")) onDismiss()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Column {
                Text("แก้ไขรายการสินค้า", fontWeight = FontWeight.Black)
                Text(order.number, color = QgMuted, style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                QgCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("กรณีสินค้าหมด", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "ลดจำนวน ลบ หรือเลือกสินค้าทดแทนจากร้านได้ก่อนเริ่มเตรียมสินค้า ยอดใหม่จะไม่เกินยอดที่ลูกค้าสั่งไว้",
                            color = QgMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                draft.forEachIndexed { index, row ->
                    QgCard(Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
                        Column {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                QgRemoteImage(row.image, Modifier.size(44.dp), row.name, maxDecodeSide = 192)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(row.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        "%.0f บาท/ชิ้น".format(row.unitPrice),
                                        color = QgMuted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        draft = draft.filterIndexed { i, _ -> i != index }
                                    },
                                    enabled = !busy
                                ) { Text("ลบ") }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = {
                                        draft = draft.mapIndexed { i, item ->
                                            if (i == index) item.copy(quantity = (item.quantity - 1).coerceAtLeast(1))
                                            else item
                                        }
                                    },
                                    enabled = !busy && row.quantity > 1
                                ) { Text("−") }
                                Text(
                                    row.quantity.toString(),
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    fontWeight = FontWeight.Black
                                )
                                OutlinedButton(
                                    onClick = {
                                        draft = draft.mapIndexed { i, item ->
                                            if (i == index) item.copy(quantity = (item.quantity + 1).coerceAtMost(99))
                                            else item
                                        }
                                    },
                                    enabled = !busy && row.quantity < 99
                                ) { Text("+") }
                            }
                        }
                    }
                }
                if (draft.isEmpty()) {
                    Text("ต้องเหลือสินค้าอย่างน้อย 1 รายการ", color = QgRed, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text("เลือกสินค้าทดแทน", fontWeight = FontWeight.ExtraBold)
                if (substituteProducts.isEmpty()) {
                    Text("ไม่มีสินค้าทดแทนที่เปิดขาย Delivery", color = QgMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    substituteProducts.take(12).forEach { product ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) { substituteId = product.id }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = substituteId == product.id,
                                onClick = { substituteId = product.id },
                                enabled = !busy
                            )
                            Text(
                                product.name + " · " + "%.0f บาท".format(product.deliveryPrice),
                                Modifier.weight(1f)
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val product = substituteProducts.find { it.id == substituteId }
                            if (product != null && draft.size < 30) {
                                draft = draft + MerchantOrderEditDraftRow(
                                    itemId = null,
                                    productId = product.id,
                                    name = product.name,
                                    image = product.image,
                                    unitPrice = product.deliveryPrice,
                                    quantity = 1
                                )
                                substituteId = ""
                            }
                        },
                        enabled = !busy && substituteId.isNotBlank() && draft.size < 30,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("เพิ่มสินค้าทดแทน") }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("ยอดค่าสินค้าใหม่", Modifier.weight(1f), color = QgMuted)
                    Text(
                        "฿" + "%.0f".format(newSubtotal),
                        color = QgRed,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        draft.map {
                            MerchantOrderEditItem(
                                itemId = it.itemId,
                                productId = if (it.itemId == null) it.productId else null,
                                quantity = it.quantity
                            )
                        }
                    )
                },
                enabled = canSave
            ) { Text("บันทึกการแก้ไข") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !busy) { Text("ปิด") }
        }
    )
}

@Composable
private fun MerchantCancelGuard(
    order: MerchantOrder,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val reasons = remember {
        listOf(
            "สินค้าหมด",
            "ร้านไม่สามารถจัดเตรียมสินค้าได้",
            "ร้านปิดหรือมีเหตุฉุกเฉิน",
            "ลูกค้าขอให้ยกเลิก",
            "อื่น ๆ"
        )
    }
    var selectedReason by remember(order.id) { mutableStateOf("") }
    var otherReason by remember(order.id) { mutableStateOf("") }
    var acknowledged by remember(order.id) { mutableStateOf(false) }
    var slide by remember(order.id) { mutableStateOf(0f) }
    val cancellable = merchantDeliveryCanCancel(order.status, order.preparingAt, order.readyAt)
    val accepted = order.status != "pending"
    LaunchedEffect(order.status, order.preparingAt, order.readyAt) {
        if (!cancellable) onDismiss()
    }
    val reason = when {
        selectedReason == "อื่น ๆ" && otherReason.trim().isNotBlank() -> "อื่น ๆ: " + otherReason.trim()
        selectedReason == "อื่น ๆ" -> ""
        else -> selectedReason
    }
    val ready = cancellable && reason.isNotBlank() && acknowledged && !busy

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                if (accepted) "ยกเลิกออเดอร์" else "ปฏิเสธออเดอร์",
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column {
                QgCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("คำสั่งซื้อ", color = QgMuted, style = MaterialTheme.typography.bodySmall)
                            Text(order.number, fontWeight = FontWeight.ExtraBold)
                        }
                        Text("฿" + "%.0f".format(order.subtotal), color = QgRed, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (accepted)
                        "ร้านรับออเดอร์นี้แล้ว การยกเลิกจะถูกบันทึกและแจ้งลูกค้า และอาจกระทบ Rider กรุณายกเลิกเฉพาะเมื่อจำเป็นจริง"
                    else
                        "หากปฏิเสธ ออเดอร์นี้จะถูกยกเลิกและแจ้งลูกค้าทันที",
                    color = QgRed,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                Text("เลือกเหตุผล", fontWeight = FontWeight.ExtraBold)
                reasons.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !busy) {
                                selectedReason = item
                                slide = 0f
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReason == item,
                            onClick = {
                                selectedReason = item
                                slide = 0f
                            },
                            enabled = !busy
                        )
                        Text(item)
                    }
                }
                if (selectedReason == "อื่น ๆ") {
                    OutlinedTextField(
                        value = otherReason,
                        onValueChange = {
                            otherReason = it.take(420)
                            slide = 0f
                        },
                        enabled = !busy,
                        label = { Text("ระบุเหตุผลเพิ่มเติม") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy) {
                            acknowledged = !acknowledged
                            slide = 0f
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = acknowledged,
                        onCheckedChange = {
                            acknowledged = it
                            slide = 0f
                        },
                        enabled = !busy
                    )
                    Text(
                        if (accepted)
                            "ฉันตรวจสอบออเดอร์นี้แล้ว และเข้าใจว่าการยกเลิกจะกระทบลูกค้าและอาจกระทบ Rider"
                        else
                            "ฉันตรวจสอบออเดอร์นี้แล้ว และเข้าใจว่าการปฏิเสธจะกระทบลูกค้า",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    if (!ready) "เลือกเหตุผลและยืนยันก่อน"
                    else if (slide >= 95f) "ปล่อยเพื่อยืนยัน"
                    else "เลื่อนไปทางขวาเพื่อยืนยัน " + if (accepted) "ยกเลิก" else "ปฏิเสธ",
                    color = QgMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = slide,
                    onValueChange = { slide = it },
                    onValueChangeFinished = {
                        if (slide >= 95f && ready) onConfirm(reason)
                        slide = 0f
                    },
                    enabled = ready,
                    valueRange = 0f..100f
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !busy) { Text("ปิด") }
        }
    )
}

private const val MERCHANT_PRIVACY_POLICY_URL =
    "https://chatchairins-source.github.io/QueuGo/docs/privacy.html"

@Composable
private fun MerchantProfileScreen(
    auth: NativeAuth,
    shop: MerchantShop?,
    shopOpen: Boolean,
    onSetup: () -> Unit,
    onHours: () -> Unit,
    onProducts: () -> Unit,
    onModules: () -> Unit,
    pushBusy: Boolean,
    onEnableBackgroundNotifications: () -> Unit,
    onTestNotification: () -> Unit,
    onSupport: () -> Unit,
    logout: () -> Unit
) {
    val context = LocalContext.current
    var showDeletion by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QgRemoteImage(
                source = shop?.logo ?: shop?.cover,
                modifier = Modifier.size(56.dp),
                fallback = shop?.name ?: auth.user.name,
                cornerRadius = 17.dp,
                maxDecodeSide = 256
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    shop?.name ?: auth.user.name,
                    color = Color(0xFF202329),
                    fontSize = MerchantPageTitleSize,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    merchantCategoryLabel(shop?.category),
                    color = Color(0xFF8B9098),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Box(
                Modifier
                    .background(
                        if (shopOpen) Color(0xFFE8F9F1) else Color(0xFFF0F1F3),
                        RoundedCornerShape(99.dp)
                    )
                    .padding(horizontal = 9.dp, vertical = 7.dp)
            ) {
                Text(
                    if (shopOpen) "เปิดร้านอยู่" else "ปิดร้านอยู่",
                    color = if (shopOpen) Color(0xFF0A9660) else Color(0xFF777D85),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        MerchantProfileSectionTitle("ข้อมูลร้าน")
        MerchantProfileCard {
            MerchantProfileInfoRow("box", "ประเภทร้าน", merchantCategoryLabel(shop?.category))
            HorizontalDivider(color = Color(0xFFECEEF1))
            MerchantProfileInfoRow("phone", "เบอร์โทร", shop?.phone ?: "-")
            HorizontalDivider(color = Color(0xFFECEEF1))
            MerchantProfileInfoRow("pin", "ที่อยู่ร้าน", shop?.address ?: "-")
            HorizontalDivider(color = Color(0xFFECEEF1))
            MerchantProfileInfoRow(
                "clock",
                "เวลาทำการ",
                (shop?.openTime ?: "06:00") + " - " + (shop?.closeTime ?: "22:00") + " น."
            )
        }

        Spacer(Modifier.height(12.dp))
        MerchantProfileSectionTitle("จัดการร้าน")
        MerchantProfileCard {
            MerchantProfileActionRow(
                icon = "home",
                title = "แก้ไขข้อมูลร้าน",
                subtitle = "ชื่อร้าน เบอร์โทร ที่อยู่ และข้อมูลหน้าร้าน",
                enabled = shop != null,
                onClick = onSetup
            )
            HorizontalDivider(color = Color(0xFFECEEF1))
            MerchantProfileActionRow(
                icon = "clock",
                title = "เวลาทำการและวันหยุด",
                subtitle = "ตั้งเวลาเปิด–ปิดและวันหยุดของร้าน",
                enabled = shop != null,
                onClick = onHours
            )
            HorizontalDivider(color = Color(0xFFECEEF1))
            MerchantProfileActionRow(
                icon = "gear",
                title = "ตั้งค่าร้านค้า",
                subtitle = "โมดูลร้านค้า การตั้งค่าระบบ และทางลัดที่เกี่ยวข้อง",
                onClick = onModules
            )
            HorizontalDivider(color = Color(0xFFECEEF1))
            MerchantProfileActionRow(
                icon = "box",
                title = "จัดการเมนูสินค้า",
                subtitle = "เพิ่ม แก้ไข ราคา และสถานะสินค้า",
                primary = true,
                enabled = shop != null,
                onClick = onProducts
            )
        }

        Spacer(Modifier.height(12.dp))
        MerchantProfileSectionTitle("การแจ้งเตือน")
        MerchantProfileCard {
            MerchantProfileActionRow(
                icon = "bell",
                title = "การแจ้งเตือนเบื้องหลัง",
                subtitle = "รับออเดอร์และสถานะสำคัญแม้ไม่ได้เปิดหน้านี้",
                enabled = !pushBusy,
                onClick = onEnableBackgroundNotifications
            )
            HorizontalDivider(color = Color(0xFFECEEF1))
            MerchantProfileActionRow(
                icon = "bell",
                title = "ทดสอบการแจ้งเตือน",
                subtitle = "ตรวจว่าแจ้งเตือนเข้าเมื่อแอปอยู่เบื้องหลัง",
                enabled = !pushBusy,
                onClick = onTestNotification
            )
        }

        Spacer(Modifier.height(12.dp))
        MerchantProfileSectionTitle("ความเป็นส่วนตัวและบัญชี")
        MerchantProfileCard {
            MerchantProfileActionRow(
                icon = "gear",
                title = "นโยบายความเป็นส่วนตัว",
                subtitle = "ข้อมูลเกี่ยวกับการเก็บและใช้ข้อมูลส่วนบุคคล"
            ) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(MERCHANT_PRIVACY_POLICY_URL))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                runCatching { context.startActivity(intent) }
            }
            HorizontalDivider(color = Color(0xFFECEEF1))
            MerchantProfileActionRow(
                icon = "back",
                title = "ลบบัญชีถาวร",
                subtitle = "ลบบัญชีและข้อมูลร้านตามขั้นตอนที่กำหนด",
                danger = true
            ) { showDeletion = true }
            HorizontalDivider(color = Color(0xFFECEEF1))
            MerchantProfileActionRow(
                icon = "back",
                title = "ออกจากระบบ",
                subtitle = "ออกจากบัญชีร้านค้าบนอุปกรณ์นี้",
                onClick = logout
            )
        }

        Spacer(Modifier.height(12.dp))
        MerchantProfileSectionTitle("ช่วยเหลือ")
        MerchantProfileCard {
            MerchantProfileActionRow(
                icon = "support",
                title = "แจ้งปัญหา / ติดตามเรื่อง",
                subtitle = "ติดต่อทีมงาน QueueGo เมื่อพบปัญหาการใช้งาน",
                onClick = onSupport
            )
        }

        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(12.dp))
            com.queuego.shared.QgTestBuildInfo(
                "Merchant", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
                BuildConfig.GIT_SHA, BuildConfig.BUILT_UTC, BuildConfig.BUILD_TYPE
            )
        }
        Spacer(Modifier.height(28.dp))
    }

    if (showDeletion) {
        AlertDialog(
            onDismissRequest = { showDeletion = false },
            text = {
                QgAccountDeletionSection(
                    accessToken = auth.session.accessToken,
                    onDeleted = logout
                )
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { showDeletion = false }) {
                    Text("ปิด")
                }
            }
        )
    }
}

@Composable
private fun MerchantProfileSectionTitle(text: String) {
    Text(
        text,
        color = Color(0xFF81868E),
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp)
    )
}

@Composable
private fun MerchantProfileCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE6E8EB), RoundedCornerShape(16.dp))
    ) { content() }
}

@Composable
private fun MerchantProfileInfoRow(icon: String, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(Color(0xFFFFF1F4), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { QgIcon(icon, Modifier.size(17.dp), QgRed) }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color(0xFF959AA1), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                color = Color(0xFF202329),
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun MerchantProfileActionRow(
    icon: String,
    title: String,
    subtitle: String,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(
                    if (primary || danger) Color(0xFFFFF0F3) else Color(0xFFF6F7F8),
                    RoundedCornerShape(11.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            QgIcon(
                icon,
                Modifier.size(17.dp),
                if (primary || danger) QgRed else Color(0xFF737982)
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (primary || danger) Color(0xFFD90D2D) else Color(0xFF202329),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                color = Color(0xFF91969E),
                fontSize = 9.sp,
                lineHeight = 12.sp
            )
        }
        Text("›", color = Color(0xFFB4B8BE), fontSize = 21.sp)
    }
}

private fun merchantCategoryLabel(category: String?): String = when (category?.lowercase()) {
    "food" -> "ร้านอาหาร"
    "grocery" -> "ร้านขายของชำ"
    "cafe" -> "เครื่องดื่ม / คาเฟ่"
    "laundry" -> "ร้านฝากซัก"
    "market" -> "ตลาดสด"
    "shopping" -> "ช้อปปิ้ง"
    else -> "ร้านค้า"
}

private fun merchantStatus(status: String, riderArrivedCustomerAt: String? = null): String {
    val normalized = status.lowercase()
    if (normalized in setOf("in_progress", "delivering", "rider_to_customer") &&
        !riderArrivedCustomerAt.isNullOrBlank()
    ) return "Rider ถึงลูกค้าแล้ว"
    return when (normalized) {
        "pending", "pending_shop" -> "รอรับ"
        "accepted", "shop_accepted" -> "ร้านรับออเดอร์แล้ว"
        "searching_rider" -> "กำลังค้นหาไรเดอร์"
        "rider_to_shop", "rider_assigned", "assigned" -> "Rider กำลังมารับสินค้า"
        "preparing" -> "กำลังเตรียม"
        "ready", "waiting_rider" -> "พร้อมให้ Rider รับ"
        "picked_up" -> "Rider รับสินค้าแล้ว"
        "in_progress", "delivering", "rider_to_customer" -> "กำลังจัดส่ง"
        "completed", "delivered" -> "จัดส่งสำเร็จ"
        "cancelled" -> "ยกเลิก"
        "no_rider_available" -> "ไม่พบ Rider"
        else -> status
    }
}

@Composable
private fun MerchantReadyEstimateControls(
    order: MerchantOrder,
    busy: Boolean,
    onAdjustReady: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "ร้านคาดว่าพร้อม " + merchantReadyTime(order.readyEstimateAt, order.preparingAt),
            color = QgMuted,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = { onAdjustReady(-5) },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("-5 นาที") }
            OutlinedButton(
                onClick = { onAdjustReady(5) },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("+5 นาที") }
            OutlinedButton(
                onClick = { onAdjustReady(10) },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("+10 นาที") }
        }
    }
}

private fun readinessLeft(readyEstimateAt: String?, preparingAt: String?, now: Long): Int {
    val target = runCatching { Instant.parse(readyEstimateAt ?: "").toEpochMilli() }.getOrNull()
        ?: (runCatching { Instant.parse(preparingAt ?: "").toEpochMilli() }.getOrNull() ?: now) + 20 * 60 * 1000L
    return (((target - now).coerceAtLeast(0L) + 999L) / 1000L).toInt()
}

private fun merchantReadyTime(readyEstimateAt: String?, preparingAt: String?): String {
    val instant = runCatching { Instant.parse(readyEstimateAt ?: "") }.getOrNull()
        ?: runCatching { Instant.parse(preparingAt ?: "").plusSeconds(20 * 60L) }.getOrNull()
        ?: return "อีกประมาณ 20 นาที"
    return java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.of("Asia/Bangkok"))
        .format(instant) + " น."
}

private fun formatSeconds(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return min.toString().padStart(2, '0') + ":" + sec.toString().padStart(2, '0')
}
