package com.queuego.customer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.queuego.shared.NativeOrderRealtime
import com.queuego.shared.customerRealtimeSubscriptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgAccountDeletionSection
import com.queuego.shared.QgBg
import com.queuego.shared.QgBottomNav
import com.queuego.shared.QgCard
import com.queuego.shared.QgLine
import com.queuego.shared.QgIconButton
import com.queuego.shared.QgIcon
import com.queuego.shared.QgMuted
import com.queuego.shared.QgNavItem
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import com.queuego.shared.QgSectionTitle
import com.queuego.shared.QgStatusPill
import com.queuego.shared.QueueGoAuthHost
import com.queuego.shared.QueueGoBrand
import com.queuego.shared.SecureRoleSessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private val categories = listOf(
    "all" to "ทั้งหมด",
    "food" to "อาหาร",
    "cafe" to "เครื่องดื่ม",
    "grocery" to "ร้านขายของชำ",
    "laundry" to "ฝากซัก",
    "market" to "ตลาดสด",
    "shopping" to "ช้อปปิ้ง"
)

private val homeDedicatedMarketCategories =
    setOf("market", "fresh", "fresh_market", "meat", "fish", "vegetable", "fruit")

@Composable
fun QueueGoCustomerApp(
    pushReferenceId: String? = null,
    onPushConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val store = remember { SecureRoleSessionStore(context, "customer") }
    var authRequested by rememberSaveable { mutableStateOf(store.load() != null) }
    var destinationAfterLogin by rememberSaveable { mutableStateOf("home") }

    if (!authRequested) {
        GuestCustomerShell(
            onLogin = { destination ->
                destinationAfterLogin = destination
                authRequested = true
            }
        )
    } else {
        QueueGoAuthHost(
            expectedRole = "customer",
            appLabel = "Customer",
            entryScreen = {
                CustomerAuthenticationScreen(it) {
                    destinationAfterLogin = "home"
                    authRequested = false
                }
            }
        ) { auth, logout ->
            key(auth.user.id, auth.user.authUserId) {
                CustomerShell(
                    auth = auth,
                    logout = {
                        logout()
                        destinationAfterLogin = "home"
                        authRequested = false
                    },
                    initialScreen = destinationAfterLogin,
                    pushReferenceId = pushReferenceId,
                    onPushConsumed = onPushConsumed
                )
            }
        }
    }
}

@Composable
private fun CustomerShell(
    auth: NativeAuth,
    logout: () -> Unit,
    initialScreen: String = "home",
    pushReferenceId: String? = null,
    onPushConsumed: () -> Unit = {}
) {
    val api = remember { CustomerApi() }
    val marketApi = remember { CustomerMarketApi() }
    val laundryApi = remember { CustomerLaundryApi() }
    val extrasApi = remember { CustomerExtrasApi() }
    val scope = rememberCoroutineScope()
    val realtime = remember { NativeOrderRealtime() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val liveMutex = remember { Mutex() }
    val context = LocalContext.current
    val cartStore = remember(auth.user.id) { CustomerCartStore(context, auth.user.id) }
    val locationStore = remember { CustomerLocationStore(context) }
    val savedDeviceLocation = remember { locationStore.load() }
    val guestCartStore = remember { CustomerCartStore(context, "guest") }
    val initialCartMerge = remember(auth.user.id) {
        val saved = cartStore.load()
        val guest = guestCartStore.load()
        when {
            guest.isEmpty() -> saved to false
            saved.isEmpty() -> {
                cartStore.save(guest)
                guestCartStore.save(emptyList())
                guest to false
            }
            saved.firstOrNull()?.product?.shopId == guest.firstOrNull()?.product?.shopId -> {
                val merged = (saved + guest)
                    .groupBy { it.product.id }
                    .values
                    .map { rows ->
                        val freshest = rows.last().product
                        CartLine(freshest, rows.sumOf { it.quantity }.coerceAtMost(99))
                    }
                cartStore.save(merged)
                guestCartStore.save(emptyList())
                merged to false
            }
            else -> saved to true
        }
    }

    var screen by remember(auth.user.id) { mutableStateOf(initialScreen) }
    var shopReturnScreen by remember { mutableStateOf("home") }
    var category by remember { mutableStateOf("all") }
    var shoppingMode by remember { mutableStateOf("all") }
    var shops by remember { mutableStateOf<List<CustomerShop>>(emptyList()) }
    var orders by remember { mutableStateOf<List<CustomerOrder>>(emptyList()) }
    var marketTrips by remember { mutableStateOf<List<MarketTripSummary>>(emptyList()) }
    var laundryOrders by remember { mutableStateOf<List<LaundryOrderSummary>>(emptyList()) }
    var banners by remember { mutableStateOf<List<HomeBanner>>(emptyList()) }
    var serviceBanners by remember { mutableStateOf<Map<String, ServiceBanner>>(emptyMap()) }
    var notifications by remember { mutableStateOf<List<CustomerNotification>>(emptyList()) }
    var selectedShop by remember { mutableStateOf<CustomerShop?>(null) }
    var selectedOrder by remember { mutableStateOf<CustomerOrder?>(null) }
    var trackingContext by remember { mutableStateOf<CustomerOrderContext?>(null) }
    var selectedMarketTrip by remember { mutableStateOf<MarketTripSummary?>(null) }
    var selectedLaundryOrder by remember { mutableStateOf<LaundryOrderSummary?>(null) }
    var supportInitialOrderId by remember { mutableStateOf<String?>(null) }
    val shopCatalog = remember(auth.user.id) { CustomerShopCatalog(scope) }
    val catalogState by shopCatalog.state.collectAsState()
    DisposableEffect(shopCatalog) { onDispose { shopCatalog.close() } }
    LaunchedEffect(screen) { if (screen != "shop") shopCatalog.close() }
    var orderItems by remember { mutableStateOf<List<CustomerOrderItem>>(emptyList()) }
    var cart by remember(auth.user.id) { mutableStateOf(initialCartMerge.first) }
    var location by remember { mutableStateOf(savedDeviceLocation) }
    var address by remember { mutableStateOf(savedDeviceLocation?.address.orEmpty()) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var message by remember {
        mutableStateOf<String?>(
            if (initialCartMerge.second)
                "ตะกร้าก่อนเข้าสู่ระบบเป็นคนละร้านกับตะกร้าบัญชีเดิม จึงเก็บไว้แยกกัน"
            else null
        )
    }
    val checkoutJournal = remember(cartStore) { cartStore.journal() }
    val checkoutRecovery = remember(checkoutJournal) { CustomerCheckoutRecovery<PendingCustomerCheckout, CustomerOrder>(checkoutJournal) }
    var pendingCheckoutRecord by remember { mutableStateOf(runCatching { checkoutJournal.read() }.getOrNull()) }
    var checkoutPending by remember { mutableStateOf(pendingCheckoutRecord != null) }

    var lastCheckoutReceipt by remember { mutableStateOf<String?>(null) }

    fun showPlacedOrder(order: CustomerOrder) {
        lastCheckoutReceipt = order.id
        cart = cartStore.load()
        note = ""
        selectedOrder = order
        orderItems = emptyList()
        message = "สั่งซื้อสำเร็จ · " + order.number
        pendingCheckoutRecord = null
        checkoutPending = false
        screen = "order"
    }
    LaunchedEffect(auth.session.accessToken, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    checkoutRecovery.reconcile { api.findPlacedOrder(auth, it.body.getString("p_order_id")) }?.let {
                        if (lastCheckoutReceipt != it.id) showPlacedOrder(it)
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Preserve pending for reconnect or explicit retry. */ }
                pendingCheckoutRecord = runCatching { checkoutJournal.read() }.getOrNull()
                checkoutPending = pendingCheckoutRecord != null
                delay(8_000)
            }
        }
    }

    val contextRefreshGate = remember(selectedOrder?.id) { TrackingContextRefreshGate() }

    fun refresh() {
        scope.launch {
            loading = true
            liveMutex.withLock { runCatching {
                shops = api.loadShops(auth)
                orders = api.loadOrders(auth)
                marketTrips = runCatching { marketApi.trips(auth) }.getOrDefault(emptyList())
                laundryOrders = runCatching { laundryApi.orders(auth) }.getOrDefault(emptyList())
                val saved = api.loadSavedLocation(auth)
                val selected = preferredCustomerDeliveryLocation(location, saved)
                if (selected != null && selected != location) {
                    location = selected
                    address = selected.address
                }
                banners = api.loadHomeBanners(auth)
                serviceBanners = api.loadServiceBanners(auth)
                notifications = runCatching { extrasApi.notifications(auth) }.getOrDefault(emptyList())
                message = null
            }.onFailure {
                if (it is CancellationException) throw it
                message = it.message ?: "โหลดข้อมูลไม่สำเร็จ"
            } }
            loading = false
        }
    }

    BackHandler(enabled = screen != "home") {
        if (screen == "checkout") {
            screen = "cart"
        } else if (screen == "shopping" && shoppingMode != "all") {
            shoppingMode = "all"
        } else {
            screen = "home"
            selectedOrder = null
            trackingContext = null
            selectedMarketTrip = null
            selectedLaundryOrder = null
            selectedShop = null
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            val p = lastKnownLocation(context)
            if (p != null) {
                location = CustomerLocation(p.first, p.second, address)
                message = "ใช้ตำแหน่งปัจจุบันแล้ว"
            } else message = "ยังอ่านตำแหน่ง GPS ไม่ได้"
        } else message = "กรุณาอนุญาตตำแหน่งเพื่อจัดส่ง"
    }

    fun openShopFromHome(shop: CustomerShop, returnTo: String = screen) {
        shopReturnScreen = returnTo
        selectedShop = shop
        screen = "shop"
        shopCatalog.open(shop.id) { api.loadProducts(auth, shop.id) }
    }

    fun openHomeBannerLink(raw: String) {
        val value = raw.trim()
        if (value.isBlank()) return
        if (value.startsWith("#")) {
            val route = value.removePrefix("#").trim().trimStart('/')
            when {
                route.isBlank() || route == "home" -> screen = "home"
                route == "search" -> screen = "search"
                route == "cart" -> screen = "cart"
                route == "orders" -> screen = "orders"
                route == "map" -> screen = "location"
                route == "market" -> screen = "market"
                route == "promotion" -> screen = "promotion"
                route == "laundry" -> screen = "laundry"
                route == "shopping" -> {
                    shoppingMode = "all"
                    screen = "shopping"
                }
                route.startsWith("shopping/") -> {
                    shoppingMode = route.substringAfter("shopping/").substringBefore('/').ifBlank { "all" }
                    screen = "shopping"
                }
                route in setOf("food", "cafe", "grocery") -> {
                    category = route
                    screen = "category"
                }
                route.startsWith("shop/") -> {
                    val id = route.substringAfter("shop/").substringBefore('/')
                    shops.find { it.id == id }?.let { openShopFromHome(it, "home") }
                }
            }
            return
        }
        if (value.startsWith("https://", true) || value.startsWith("http://", true)) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)))
            }.onFailure { message = "เปิดลิงก์ไม่สำเร็จ" }
        }
    }

    suspend fun refreshNotifications() = liveMutex.withLock {
        runCatching { extrasApi.notifications(auth) }
            .onFailure { if (it is CancellationException) throw it }
            .onSuccess { notifications = it }
    }

    suspend fun refreshOrders() = liveMutex.withLock {
        runCatching { api.loadOrders(auth) }
            .onFailure { if (it is CancellationException) throw it }
            .onSuccess { fresh ->
                orders = fresh
                selectedOrder = selectedOrder?.let { old -> fresh.find { it.id == old.id } ?: old }
            }
        runCatching { marketApi.trips(auth) }
            .onFailure { if (it is CancellationException) throw it }
            .onSuccess { fresh ->
                marketTrips = fresh
                selectedMarketTrip = selectedMarketTrip?.let { old -> fresh.find { it.id == old.id } ?: old }
            }
        runCatching { laundryApi.orders(auth) }
            .onFailure { if (it is CancellationException) throw it }
            .onSuccess { fresh ->
                laundryOrders = fresh
                selectedLaundryOrder = selectedLaundryOrder?.let { old -> fresh.find { it.id == old.id } ?: old }
            }
        val detail = selectedOrder?.takeIf { screen == "order" }
        if (detail != null && contextRefreshGate.shouldFetch(detail)) {
            runCatching { api.loadOrderItems(auth, detail.id) }
                .onFailure { if (it is CancellationException) { contextRefreshGate.cancelled(detail); throw it } }
                .onSuccess { if (screen == "order" && selectedOrder?.id == detail.id) orderItems = it }
            if (screen == "order" && selectedOrder?.id == detail.id) {
                runCatching { api.loadOrderContext(auth, detail.id) }
                    .onFailure { if (it is CancellationException) { contextRefreshGate.cancelled(detail); throw it } }
                    .onSuccess { if (screen == "order" && selectedOrder?.id == detail.id) trackingContext = it }
            }
        }
    }

    LaunchedEffect(auth.user.id) { refresh() }

    LaunchedEffect(pushReferenceId, loading, orders) {
        val reference = pushReferenceId?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        if (loading) return@LaunchedEffect
        val target = orders.find { it.id == reference }
        if (target != null) {
            selectedOrder = target
            orderItems = runCatching { api.loadOrderItems(auth, target.id) }.getOrDefault(emptyList())
            screen = "order"
        } else {
            screen = "notifications"
            message = "ไม่พบออเดอร์ที่อ้างอิงจากการแจ้งเตือน"
        }
        onPushConsumed()
    }
    LaunchedEffect(auth.session.accessToken, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refreshNotifications()
                delay(8_000)
            }
        }
    }
    // Key the closure to detail visits, so a reopened closed order gets one final snapshot.
    LaunchedEffect(auth.session.accessToken, selectedOrder?.id, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            realtime.changes(auth.session.accessToken, customerRealtimeSubscriptions(auth.user.id)).collect {
                refreshNotifications()
                refreshOrders()
            }
        }
    }
    LaunchedEffect(cart) { cartStore.save(cart) }
    LaunchedEffect(auth.session.accessToken, screen, selectedOrder?.id, lifecycle) {
        if (screen == "orders" || screen == "order") {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    refreshOrders()
                    if (screen == "order" && selectedOrder?.trackingClosed() == true) break
                    delay(3_000)
                }
            }
        }
    }

    Scaffold(
        containerColor = QgBg,
        bottomBar = {
            CustomerBottomNavigation(screen, cart.sumOf { it.quantity }) { screen = it }
        }
    ) { insets ->
        Column(
            Modifier.fillMaxSize()
                .padding(insets)
                .background(QgBg)
        ) {
            CustomerTopBar(
                home = screen == "home",
                cartCount = cart.sumOf { it.quantity },
                unreadCount = notifications.count { !it.read },
                onHome = { screen = "home" },
                onCart = { screen = "cart" },
                onNotifications = { screen = "notifications" },
                onProfile = { screen = "profile" }
            )
            if (message != null) {
                Text(
                    message!!,
                    color = if (message!!.contains("สำเร็จ") || message!!.contains("แล้ว")) QgRed else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            when (screen) {
                "search" -> CustomerSearchScreen(
                    shops = shops,
                    location = location,
                    onBack = { screen = "home" },
                    onOpenShop = { shop ->
                        if (shop.category.lowercase() == "laundry") screen = "laundry"
                        else openShopFromHome(shop, "search")
                    }
                )
                "notifications" -> CustomerNotificationsScreen(
                    notifications = notifications,
                    busy = busy,
                    onBack = { screen = "home" },
                    onMarkAll = {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                runCatching { extrasApi.markAllRead(auth) }
                                    .onSuccess {
                                        notifications = notifications.map { it.copy(read = true) }
                                        message = "อ่านการแจ้งเตือนทั้งหมดแล้ว"
                                    }
                                    .onFailure { message = it.message ?: "บันทึกแจ้งเตือนไม่สำเร็จ" }
                                busy = false
                            }
                        }
                    },
                    onOpen = { notification ->
                        if (!busy) {
                            busy = true
                            scope.launch {
                                runCatching { extrasApi.markRead(auth, notification.id) }
                                notifications = notifications.map {
                                    if (it.id == notification.id) it.copy(read = true) else it
                                }
                                val ref = notification.referenceId
                                val target = if (!ref.isNullOrBlank()) orders.find { it.id == ref } else null
                                if (target != null) {
                                    selectedOrder = target
                                    orderItems = runCatching { api.loadOrderItems(auth, target.id) }.getOrDefault(emptyList())
                                    screen = "order"
                                }
                                busy = false
                            }
                        }
                    }
                )
                "home" -> CustomerHome(
                    loading = loading,
                    banners = banners,
                    location = location,
                    deliveryAddress = address,
                    shops = shops,
                    onLocation = { screen = "location" },
                    onSearch = { screen = "search" },
                    onCategory = {
                        when (it) {
                            "market" -> screen = "market"
                            "laundry" -> screen = "laundry"
                            "shopping" -> {
                                shoppingMode = "all"
                                screen = "shopping"
                            }
                            else -> {
                                category = it
                                screen = "category"
                            }
                        }
                    },
                    onShop = { shop ->
                        if (shop.category.lowercase() == "laundry") screen = "laundry"
                        else openShopFromHome(shop, "home")
                    },
                    onBannerLink = ::openHomeBannerLink
                )
                "location" -> CustomerLocationPickerScreen(
                    location = location,
                    address = address,
                    busy = busy,
                    onGps = {
                        if (hasLocation(context)) {
                            val p = lastKnownLocation(context)
                            if (p != null) {
                                location = CustomerLocation(p.first, p.second, address)
                                message = "ใช้ตำแหน่งปัจจุบันแล้ว"
                            } else message = "ยังอ่านตำแหน่ง GPS ไม่ได้"
                        } else {
                            permission.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    resolveAddress = { latitude, longitude ->
                        api.reverseGeocode(latitude, longitude)
                    },
                    onSave = { picked ->
                        if (!busy) {
                            busy = true
                            scope.launch {
                                try {
                                    api.saveLocation(auth, picked)
                                    location = picked
                                    address = picked.address
                                    // Account acknowledgement remains successful if the local cache fails.
                                    val cacheFailure = try { locationStore.save(picked); null }
                                        catch (failure: CancellationException) { throw failure }
                                        catch (failure: Exception) { failure }
                                    message = if (cacheFailure == null) "บันทึกที่อยู่แล้ว"
                                        else "บันทึกที่อยู่ในบัญชีแล้ว แต่บันทึกในเครื่องไม่สำเร็จ"
                                    screen = "home"
                                } catch (failure: CancellationException) { throw failure }
                                catch (failure: Exception) { message = failure.message ?: "บันทึกที่อยู่ไม่สำเร็จ" }
                                finally { busy = false }
                            }
                        }
                    },
                    onBack = { screen = "home" }
                )
                "category" -> ServiceCategoryScreen(
                    loading = loading,
                    category = category,
                    serviceBanner = serviceBanners[category],
                    shops = shops,
                    onBack = {
                        category = "all"
                        screen = "home"
                    },
                    onShop = { shop -> openShopFromHome(shop, "category") },
                    onBannerLink = ::openHomeBannerLink
                )
                "shopping" -> CustomerShoppingScreen(
                    loading = loading,
                    mode = shoppingMode,
                    serviceBanners = serviceBanners,
                    shops = shops,
                    location = location,
                    onBack = {
                        if (shoppingMode == "all") screen = "home"
                        else shoppingMode = "all"
                    },
                    onMode = { shoppingMode = it },
                    onShop = { shop -> openShopFromHome(shop, "shopping") },
                    onBannerLink = ::openHomeBannerLink
                )
                "laundry" -> LaundryNativeScreen(
                    auth = auth,
                    location = location,
                    address = address,
                    onAddress = {
                        address = it
                        location = location?.copy(address = it)
                    },
                    onGps = {
                        if (hasLocation(context)) {
                            val p = lastKnownLocation(context)
                            if (p != null) {
                                location = CustomerLocation(p.first, p.second, address)
                                message = "ใช้ตำแหน่งปัจจุบันแล้ว"
                            } else message = "ยังอ่านตำแหน่ง GPS ไม่ได้"
                        } else {
                            permission.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        }
                    },
                    onBack = { screen = "home" },
                    onDone = {
                        message = "ส่งคำขอฝากซักแล้ว"
                        screen = "orders"
                    }
                )
                "market" -> MarketNativeScreen(
                    auth = auth,
                    location = location,
                    address = address,
                    onAddress = {
                        address = it
                        location = location?.copy(address = it)
                    },
                    onGps = {
                        if (hasLocation(context)) {
                            val p = lastKnownLocation(context)
                            if (p != null) {
                                location = CustomerLocation(p.first, p.second, address)
                                message = "ใช้ตำแหน่งปัจจุบันแล้ว"
                            } else message = "ยังอ่านตำแหน่ง GPS ไม่ได้"
                        } else {
                            permission.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        }
                    },
                    onBack = { screen = "home" },
                    onDone = {
                        scope.launch {
                            orders = runCatching { api.loadOrders(auth) }.getOrDefault(orders)
                            screen = "orders"
                        }
                    }
                )
                "promotion" -> CustomerPromotionScreen(
                    onBack = { screen = "home" },
                    onOpenShop = { shopId ->
                        shops.find { it.id == shopId }?.let { openShopFromHome(it, "promotion") }
                    }
                )
                "shop" -> ShopScreen(
                    auth = auth,
                    shop = selectedShop,
                    products = catalogState.products,
                    productsLoading = catalogState.loading,
                    productsError = catalogState.error,
                    onRetryProducts = { selectedShop?.let { shop -> shopCatalog.open(shop.id) { api.loadProducts(auth, shop.id) } } },
                    cart = cart,
                    onBack = { screen = shopReturnScreen },
                    onAdd = { product ->
                        val currentShopId = cart.firstOrNull()?.product?.shopId
                        if (checkoutPending) {
                            message = "กรุณาตรวจผลคำสั่งซื้อเดิมก่อนแก้ตะกร้า"
                        } else if (currentShopId != null && currentShopId != product.shopId) {
                            message = "หนึ่งตะกร้าสั่งได้จากร้านเดียว กรุณาสั่งร้านเดิมให้เสร็จก่อน"
                        } else {
                            val existing = cart.find { it.product.id == product.id }
                            cart = if (existing == null) cart + CartLine(product, 1)
                            else cart.map { if (it.product.id == product.id) it.copy(quantity = it.quantity + 1) else it }
                            message = "เพิ่มลงตะกร้าแล้ว"
                        }
                    },
                    onCart = { screen = "cart" }
                )
                "cart" -> CustomerCartScreen(
                    cart = cart,
                    busy = busy,
                    checkoutPending = checkoutPending,
                    onBack = { screen = "home" },
                    onMinus = { id ->
                        if (!checkoutPending) cart = cart.mapNotNull {
                            if (it.product.id != id) it
                            else if (it.quantity <= 1) null else it.copy(quantity = it.quantity - 1)
                        }
                    },
                    onPlus = { id ->
                        if (!checkoutPending) cart = cart.map {
                            if (it.product.id == id) it.copy(quantity = (it.quantity + 1).coerceAtMost(99)) else it
                        }
                    },
                    onRemove = { id ->
                        if (!checkoutPending) cart = cart.filterNot { it.product.id == id }
                    },
                    onCheckout = {
                        if (checkoutPending) {
                            screen = "checkout"
                        } else if (!busy && cart.isNotEmpty()) {
                            busy = true
                            message = null
                            scope.launch {
                                val current = cart.toList()
                                val shopId = current.firstOrNull()?.product?.shopId
                                runCatching {
                                    require(!shopId.isNullOrBlank()) { "ตะกร้าว่าง" }
                                    val fresh = api.loadProducts(auth, shopId)
                                    val byId = fresh.associateBy { it.id }
                                    val unavailable = current.filter { line ->
                                        byId[line.product.id]?.available != true
                                    }
                                    require(unavailable.isEmpty()) {
                                        "รายการที่ยังสั่งไม่ได้: " + unavailable.joinToString(", ") { it.product.name }
                                    }
                                    var changed = false
                                    val reconciled = current.map { line ->
                                        val latest = byId.getValue(line.product.id)
                                        if (latest.deliveryPrice != line.product.deliveryPrice ||
                                            latest.name != line.product.name ||
                                            latest.image != line.product.image
                                        ) changed = true
                                        line.copy(product = latest)
                                    }
                                    cart = reconciled
                                    if (changed) {
                                        message = "ราคาสินค้ามีการเปลี่ยนแปลง กรุณาตรวจสอบยอดล่าสุดก่อนยืนยัน"
                                    }
                                    screen = "checkout"
                                }.onFailure {
                                    message = it.message ?: "ตรวจสอบตะกร้าไม่สำเร็จ"
                                }
                                busy = false
                            }
                        }
                    }
                )
                "checkout" -> CustomerCheckoutScreen(
                    pendingCheckout = pendingCheckoutRecord,
                    cart = cart,
                    shop = shops.find { it.id == cart.firstOrNull()?.product?.shopId },
                    location = location,
                    address = address,
                    note = note,
                    busy = busy,
                    resolveAddress = { latitude, longitude -> api.reverseGeocode(latitude, longitude) },
                    onBack = { screen = "cart" },
                    onGps = {
                        if (hasLocation(context)) {
                            val p = lastKnownLocation(context)
                            if (p != null) {
                                location = CustomerLocation(p.first, p.second, address)
                                message = "ใช้ตำแหน่งปัจจุบันแล้ว"
                            } else message = "ยังอ่านตำแหน่ง GPS ไม่ได้"
                        } else {
                            permission.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        }
                    },
                    onLocationChange = { next ->
                        location = next
                    },
                    onAddress = {
                        address = it
                        location = location?.copy(address = it)
                    },
                    onNote = { note = it.take(500) },
                    onPlace = {
                        if (!busy) {
                            busy = true
                            val submittedCart = cart.toList()
                            val submittedLocation = location?.copy(address = address.trim())
                            val submittedShop = shops.find { it.id == submittedCart.firstOrNull()?.product?.shopId }
                            val submittedNote = note
                            cartStore.save(submittedCart)
                            scope.launch {
                                try {
                                    if (!checkoutPending) {
                                        val active = api.loadActiveOrder(auth)
                                        if (active != null) {
                                            selectedOrder = active
                                            orderItems = runCatching { api.loadOrderItems(auth, active.id) }.getOrDefault(emptyList())
                                            message = "มีออเดอร์ที่กำลังดำเนินการอยู่ · " + active.number
                                            screen = "order"
                                            return@launch
                                        }
                                    }
                                    val order = checkoutRecovery.submit(
                                        create = {
                                            require(submittedShop != null && submittedLocation != null && submittedLocation.address.isNotBlank()) {
                                                "กรุณาเลือกตำแหน่งและกรอกที่อยู่จัดส่ง"
                                            }
                                            cartStore.pending(
                                                api.checkoutBody(
                                                    auth,
                                                    UUID.randomUUID().toString(),
                                                    submittedShop,
                                                    submittedCart,
                                                    submittedLocation,
                                                    submittedNote
                                                ),
                                                submittedCart
                                            )
                                        },
                                        send = { pending ->
                                            val body = pending.body
                                            api.saveLocation(
                                                auth,
                                                CustomerLocation(
                                                    body.getDouble("p_delivery_lat"),
                                                    body.getDouble("p_delivery_lng"),
                                                    body.getString("p_delivery_address")
                                                )
                                            )
                                            api.placeOrder(auth, body)
                                        },
                                        recover = { api.findPlacedOrder(auth, it.body.getString("p_order_id")) },
                                        definitiveRejection = { e ->
                                            e is com.queuego.shared.QueueGoHttpException &&
                                                e.statusCode in 400..499 &&
                                                e.statusCode !in listOf(401, 403, 408, 429)
                                        }
                                    )
                                    showPlacedOrder(order)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    pendingCheckoutRecord = runCatching { checkoutJournal.read() }.getOrNull()
                                    checkoutPending = pendingCheckoutRecord != null
                                    message = when {
                                        checkoutPending ->
                                            "ยังยืนยันผลไม่ได้ กรุณาตรวจผลคำขอเดิมอีกครั้ง ระบบจะใช้รายการเดิมเพื่อป้องกันออเดอร์ซ้ำ"
                                        e.message?.contains("OUTSIDE_SERVICE_AREA") == true ->
                                            "ตำแหน่งอยู่นอกพื้นที่ให้บริการ QueueGo"
                                        e.message?.contains("DELIVERY_DISTANCE_EXCEEDED") == true ->
                                            "ระยะทางไกลเกินขอบเขตให้บริการ"
                                        else -> e.message ?: "สั่งซื้อไม่สำเร็จ"
                                    }
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                )
                "orders" -> OrdersScreen(
                    loading = loading,
                    orders = orders,
                    marketTrips = marketTrips,
                    laundryOrders = laundryOrders,
                    onOpen = {
                        selectedOrder = it
                        orderItems = emptyList()
                        screen = "order"
                    },
                    onMarketOpen = {
                        selectedMarketTrip = it
                        screen = "market-trip"
                    },
                    onLaundryOpen = {
                        selectedLaundryOrder = it
                        screen = "laundry-order"
                    }
                )
                "market-trip" -> selectedMarketTrip?.let {
                    MarketTripDetailScreen(auth = auth, trip = it, onBack = { screen = "orders" })
                }
                "laundry-order" -> selectedLaundryOrder?.let {
                    LaundryOrderDetailScreen(auth = auth, order = it, onBack = { screen = "orders" })
                }
                "order" -> CustomerOrderTrackingScreen(
                    auth = auth,
                    order = selectedOrder,
                    items = orderItems,
                    context = trackingContext,
                    shopCategory = selectedOrder?.shopId?.let { id -> shops.find { it.id == id }?.category },
                    busy = busy,
                    onChat = { screen = "chat" },
                    onSupport = {
                        supportInitialOrderId = selectedOrder?.id
                        screen = "support"
                    },
                    onCancel = {
                        val current = selectedOrder
                        if (current != null && !busy) {
                            busy = true
                            scope.launch {
                                runCatching { api.cancelOrder(auth, current.id) }
                                    .onSuccess {
                                        message = "ยกเลิกออเดอร์แล้ว"
                                        orders = runCatching { api.loadOrders(auth) }.getOrDefault(orders)
                                        selectedOrder = orders.find { it.id == current.id }
                                            ?: current.copy(status = "cancelled")
                                    }
                                    .onFailure { message = it.message ?: "ยกเลิกออเดอร์ไม่สำเร็จ" }
                                busy = false
                            }
                        }
                    },
                    onBack = {
                        trackingContext = null
                        screen = "orders"
                    }
                )
                "chat" -> selectedOrder?.let { activeOrder ->
                    CustomerChatScreen(
                        auth = auth,
                        order = activeOrder,
                        onBack = { screen = "order" }
                    )
                }
                "support" -> CustomerSupportScreen(
                    auth = auth,
                    orders = orders,
                    initialOrderId = supportInitialOrderId,
                    onBack = {
                        if (supportInitialOrderId != null && selectedOrder != null) screen = "order"
                        else screen = "profile"
                        supportInitialOrderId = null
                    }
                )
                "profile" -> ProfileScreen(
                    auth = auth,
                    onSupport = {
                        supportInitialOrderId = null
                        screen = "support"
                    },
                    logout = logout
                )
            }
        }
    }
}

@Composable
private fun CustomerTopBar(
    home: Boolean,
    cartCount: Int,
    unreadCount: Int,
    onHome: () -> Unit,
    onCart: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit
) {
    val horizontalPadding = if (home) 16.dp else 12.dp
    val verticalPadding = if (home) 10.dp else 6.dp
    val actionSize = if (home) 40.dp else 42.dp
    val actionRadius = if (home) 13.dp else 12.dp

    Column(
        Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color(0xF0FFFFFF))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.clickable(onClick = onHome)) {
                Text(
                    "Queue",
                    color = androidx.compose.ui.graphics.Color(0xFF17191D),
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
            CustomerTopAction(
                resource = R.drawable.qg_nav_bag,
                description = "ตะกร้า",
                size = actionSize,
                radius = actionRadius,
                badge = cartCount,
                onClick = onCart
            )
            Spacer(Modifier.width(8.dp))
            CustomerTopAction(
                resource = R.drawable.qg_top_bell,
                description = "แจ้งเตือน",
                size = actionSize,
                radius = actionRadius,
                badge = unreadCount,
                onClick = onNotifications
            )
            Spacer(Modifier.width(8.dp))
            CustomerTopAction(
                resource = R.drawable.qg_top_user,
                description = "โปรไฟล์",
                size = actionSize,
                radius = actionRadius,
                onClick = onProfile
            )
        }
        HorizontalDivider(thickness = 1.dp, color = androidx.compose.ui.graphics.Color(0x0D000000))
    }
}

@Composable
private fun CustomerTopAction(
    resource: Int,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp,
    badge: Int = 0,
    onClick: () -> Unit
) {
    Box(Modifier.size(size)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(radius))
                .background(androidx.compose.ui.graphics.Color(0xFFF3F4F6))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(resource),
                contentDescription = description,
                modifier = Modifier.size(20.dp),
                tint = androidx.compose.ui.graphics.Color(0xFF24272D)
            )
        }
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .widthIn(min = 17.dp)
                    .height(17.dp)
                    .clip(CircleShape)
                    .background(QgRed)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (badge > 99) "99+" else badge.toString(),
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
internal fun CustomerHome(
    loading: Boolean,
    banners: List<HomeBanner>,
    location: CustomerLocation?,
    deliveryAddress: String,
    shops: List<CustomerShop>,
    onLocation: () -> Unit,
    onSearch: () -> Unit,
    onCategory: (String) -> Unit,
    onShop: (CustomerShop) -> Unit,
    onBannerLink: (String) -> Unit
) {
    var bannerIndex by remember(banners.map { it.image }) { mutableStateOf(0) }
    val rotationMs = banners.firstOrNull()?.rotationMs ?: 5_000L
    LaunchedEffect(banners.size, rotationMs) {
        if (banners.size > 1) {
            while (true) {
                delay(rotationMs)
                bannerIndex = (bannerIndex + 1) % banners.size
            }
        }
    }

    val nearbyShops = remember(shops, location) {
        val eligible = shops.filter { shop ->
            val key = shop.category.lowercase()
            key != "grocery" && key != "shopping" && key !in homeDedicatedMarketCategories
        }
        if (location == null) eligible
        else eligible.sortedBy { customerDistanceKm(location, it) }
    }

    val homeHorizontalPadding = if (LocalConfiguration.current.screenWidthDp <= 420) 12.dp else 14.dp
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = homeHorizontalPadding)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onLocation)
                .padding(start = 2.dp, end = 2.dp, top = 13.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFFFFF0F1)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.qg_home_pin),
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = QgRed
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "ส่งไปที่",
                    color = QgMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
                Text(
                    deliveryAddress.ifBlank { location?.address.orEmpty() }.ifBlank { "เลือกที่อยู่จัดส่ง" },
                    color = androidx.compose.ui.graphics.Color(0xFF17191D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 1
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(15.dp),
                    clip = false,
                    ambientColor = androidx.compose.ui.graphics.Color(0x0914181E),
                    spotColor = androidx.compose.ui.graphics.Color(0x0914181E)
                )
                .clip(RoundedCornerShape(15.dp))
                .background(androidx.compose.ui.graphics.Color.White)
                .border(1.dp, QgLine, RoundedCornerShape(15.dp))
                .clickable(onClick = onSearch)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.qg_nav_search),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = androidx.compose.ui.graphics.Color(0xFF3F434A)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "ค้นหาร้านค้าจากทุกหมวด",
                color = androidx.compose.ui.graphics.Color(0xFF9A9EA5),
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(13.dp))
        CustomerHomeBanner(
            banners = banners,
            selected = bannerIndex,
            onSelect = { bannerIndex = it },
            onOpen = { banner -> banner.link?.let(onBannerLink) }
        )

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            categories.filter { it.first != "all" }.forEach { (key, label) ->
                CustomerCategoryButton(
                    key = key,
                    label = label,
                    onClick = { onCategory(key) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ร้านใกล้คุณ",
                color = androidx.compose.ui.graphics.Color(0xFF17191D),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                lineHeight = 22.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                "ดูทั้งหมด",
                color = QgRed,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onSearch)
            )
        }
        Spacer(Modifier.height(11.dp))

        when {
            loading -> CircularProgressIndicator()
            nearbyShops.isEmpty() -> Text(
                "ยังไม่มีร้านค้า",
                color = QgMuted,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
            )
            else -> nearbyShops.forEach { shop ->
                CustomerBlueprintShopCard(shop = shop, onClick = { onShop(shop) })
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun CustomerCategoryButton(
    key: String,
    label: String,
    onClick: () -> Unit
) {
    val icon = when (key) {
        "food" -> "utensils"
        "cafe" -> "drink"
        "grocery" -> "bottle"
        "laundry" -> "grid"
        "market" -> "basket"
        "shopping" -> "shopping"
        else -> key
    }
    Column(
        Modifier
            .width(64.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(54.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = androidx.compose.ui.graphics.Color(0x0914181E),
                    spotColor = androidx.compose.ui.graphics.Color(0x0914181E)
                )
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Color.White)
                .border(1.dp, QgLine, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            QgIcon(icon, Modifier.size(25.dp), QgRed)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = androidx.compose.ui.graphics.Color(0xFF3A3D42),
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun CustomerBlueprintShopCard(
    shop: CustomerShop,
    onClick: () -> Unit
) {
    val compact = LocalConfiguration.current.screenWidthDp <= 420
    val radius = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .shadow(
                elevation = 4.dp,
                shape = radius,
                clip = false,
                ambientColor = androidx.compose.ui.graphics.Color(0x0914181E),
                spotColor = androidx.compose.ui.graphics.Color(0x0914181E)
            )
            .clip(radius)
            .background(androidx.compose.ui.graphics.Color.White)
            .border(1.dp, QgLine, radius)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QgRemoteImage(
            source = shop.logo ?: shop.cover,
            modifier = if (compact) Modifier.size(82.dp) else Modifier.size(92.dp),
            fallback = shop.name,
            cornerRadius = 14.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                shop.name,
                color = androidx.compose.ui.graphics.Color(0xFF17191D),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    categoryLabel(shop.category),
                    color = QgMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1
                )
                if (!shop.openTime.isNullOrBlank()) {
                    Text(
                        shop.openTime + "–" + (shop.closeTime ?: ""),
                        color = QgMuted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(
                        if (shop.open) androidx.compose.ui.graphics.Color(0xFFEAF9F3)
                        else androidx.compose.ui.graphics.Color(0xFFF1F1F2)
                    )
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Text(
                    if (shop.open) "เปิดอยู่" else "ปิดอยู่",
                    color = if (shop.open) androidx.compose.ui.graphics.Color(0xFF0A9660)
                    else androidx.compose.ui.graphics.Color(0xFF777777),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

internal fun customerDistanceKm(location: CustomerLocation, shop: CustomerShop): Double {
    val lat2 = shop.latitude ?: return Double.POSITIVE_INFINITY
    val lon2 = shop.longitude ?: return Double.POSITIVE_INFINITY
    if (lat2 !in 5.0..21.0 || lon2 !in 97.0..106.0) return Double.POSITIVE_INFINITY
    val lat1Rad = Math.toRadians(location.latitude)
    val lat2Rad = Math.toRadians(lat2)
    val dLat = Math.toRadians(lat2 - location.latitude)
    val dLon = Math.toRadians(lon2 - location.longitude)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(lat1Rad) * kotlin.math.cos(lat2Rad) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    return 6_371.0 * 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
}

@Composable
private fun OrdersScreen(
    loading: Boolean,
    orders: List<CustomerOrder>,
    marketTrips: List<MarketTripSummary>,
    laundryOrders: List<LaundryOrderSummary>,
    onOpen: (CustomerOrder) -> Unit,
    onMarketOpen: (MarketTripSummary) -> Unit,
    onLaundryOpen: (LaundryOrderSummary) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        QgSectionTitle("ออเดอร์ของฉัน", "อาหาร ตลาดสด ฝากซัก และบริการทั้งหมด")
        Spacer(Modifier.height(10.dp))
        if (loading && orders.isEmpty() && marketTrips.isEmpty() && laundryOrders.isEmpty()) {
            CircularProgressIndicator()
        }
        if (orders.isEmpty() && marketTrips.isEmpty() && laundryOrders.isEmpty() && !loading) {
            QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่มีออเดอร์", color = QgMuted) }
        }

        if (marketTrips.isNotEmpty()) {
            Text("ตลาดสด", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 8.dp))
            marketTrips.forEach { trip ->
                QgCard(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onMarketOpen(trip) }) {
                    Column {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Market Trip · " + trip.shopCount + " ร้าน", fontWeight = FontWeight.Black)
                            Spacer(Modifier.weight(1f))
                            QgStatusPill(marketTripStatus(trip.status), trip.status.uppercase() != "CANCELLED")
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(trip.deliveryAddress ?: "", color = QgMuted, style = MaterialTheme.typography.bodySmall)
                        Text("รวม ฿" + "%.0f".format(trip.total), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (laundryOrders.isNotEmpty()) {
            Text("ฝากซัก", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 8.dp))
            laundryOrders.forEach { order ->
                QgCard(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onLaundryOpen(order) }) {
                    Column {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(order.number, fontWeight = FontWeight.Black)
                            Spacer(Modifier.weight(1f))
                            QgStatusPill(laundryStatus(order.status), order.status != "cancelled")
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(order.serviceName, color = QgMuted)
                        val total = order.finalTotal ?: order.estimatedTotal
                        Text(total?.let { "รวม ฿" + "%.0f".format(it) } ?: "รอสรุปราคา", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (orders.isNotEmpty()) {
            Text("Delivery", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 8.dp))
            orders.filter { it.shopId != null }.forEach { order ->
                QgCard(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onOpen(order) }) {
                    Column {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(order.number, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.weight(1f))
                            QgStatusPill(statusLabel(order.status), order.status !in setOf("cancelled", "no_rider_available"))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(order.deliveryAddress ?: "", color = QgMuted, style = MaterialTheme.typography.bodySmall)
                        Text("รวม ฿" + "%.0f".format(order.total), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ProfileScreen(
    auth: NativeAuth,
    onSupport: () -> Unit,
    logout: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        QgSectionTitle("บัญชีของฉัน")
        Spacer(Modifier.height(12.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text(auth.user.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                Text("สถานะบัญชี: " + auth.user.status, color = QgMuted)
            }
        }
        Spacer(Modifier.height(10.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text("QueueGo Production", fontWeight = FontWeight.ExtraBold)
                Text("บัญชีนี้ใช้ Session และ RLS ของระบบจริง", color = QgMuted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onSupport, modifier = Modifier.fillMaxWidth()) {
            Text("ติดต่อฝ่ายช่วยเหลือ")
        }
        Spacer(Modifier.height(10.dp))
        QgAccountDeletionSection(
            accessToken = auth.session.accessToken,
            onDeleted = logout,
            pendingChatUserId = auth.user.id
        )
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = logout, modifier = Modifier.fillMaxWidth()) { Text("ออกจากระบบ") }
    }
}

private fun statusLabel(status: String): String = when (status.lowercase()) {
    "pending" -> "รอร้านรับ"
    "accepted" -> "ร้านรับแล้ว"
    "searching_rider" -> "กำลังหาไรเดอร์"
    "rider_assigned", "assigned" -> "ไรเดอร์รับงานแล้ว"
    "preparing" -> "กำลังเตรียม"
    "ready" -> "พร้อมรับสินค้า"
    "picked_up" -> "รับสินค้าแล้ว"
    "in_progress", "rider_to_customer" -> "กำลังจัดส่ง"
    "arrived" -> "Rider ถึงลูกค้าแล้ว"
    "completed" -> "ส่งสำเร็จ"
    "cancelled" -> "ยกเลิก"
    "no_rider_available" -> "ไม่พบ Rider"
    else -> status
}

private fun categoryLabel(category: String): String = when (category.lowercase()) {
    "food" -> "อาหาร"
    "cafe", "drink", "beverage" -> "เครื่องดื่ม"
    "grocery" -> "ร้านขายของชำ"
    "market", "fresh", "fresh_market" -> "ตลาดสด"
    "laundry" -> "ฝากซัก"
    "shopping" -> "ช้อปปิ้ง"
    else -> "ร้านค้า"
}

internal fun hasLocation(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

internal fun lastKnownLocation(context: Context): Pair<Double, Double>? {
    if (!hasLocation(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return runCatching {
        manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { it.latitude to it.longitude }
    }.getOrNull()
}
