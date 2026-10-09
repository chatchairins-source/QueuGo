package com.queuego.customer

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private val categories = listOf(
    "all" to "ทั้งหมด",
    "food" to "อาหาร",
    "cafe" to "เครื่องดื่ม",
    "grocery" to "ของชำ",
    "market" to "ตลาดสด",
    "laundry" to "ฝากซัก",
    "shopping" to "ช้อปปิ้ง"
)

@Composable
fun QueueGoCustomerApp() {
    QueueGoAuthHost(expectedRole = "customer", appLabel = "Customer") { auth, logout ->
        CustomerShell(auth, logout)
    }
}

@Composable
private fun CustomerShell(auth: NativeAuth, logout: () -> Unit) {
    val api = remember { CustomerApi() }
    val marketApi = remember { CustomerMarketApi() }
    val laundryApi = remember { CustomerLaundryApi() }
    val extrasApi = remember { CustomerExtrasApi() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cartStore = remember(auth.user.id) { CustomerCartStore(context, auth.user.id) }

    var screen by remember { mutableStateOf("home") }
    var category by remember { mutableStateOf("all") }
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
    var products by remember { mutableStateOf<List<CustomerProduct>>(emptyList()) }
    var orderItems by remember { mutableStateOf<List<CustomerOrderItem>>(emptyList()) }
    var cart by remember(auth.user.id) { mutableStateOf(cartStore.load()) }
    var location by remember { mutableStateOf<CustomerLocation?>(null) }
    var address by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            runCatching {
                shops = api.loadShops(auth)
                orders = api.loadOrders(auth)
                marketTrips = runCatching { marketApi.trips(auth) }.getOrDefault(emptyList())
                laundryOrders = runCatching { laundryApi.orders(auth) }.getOrDefault(emptyList())
                val saved = api.loadSavedLocation(auth)
                if (saved != null) {
                    location = saved
                    if (address.isBlank()) address = saved.address
                }
                banners = api.loadHomeBanners(auth)
                serviceBanners = api.loadServiceBanners(auth)
                notifications = runCatching { extrasApi.notifications(auth) }.getOrDefault(emptyList())
                message = null
            }.onFailure { message = it.message ?: "โหลดข้อมูลไม่สำเร็จ" }
            loading = false
        }
    }

    BackHandler(enabled = screen != "home") {
        screen = "home"
        selectedOrder = null
        trackingContext = null
        selectedMarketTrip = null
        selectedLaundryOrder = null
        selectedShop = null
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

    LaunchedEffect(auth.user.id) { refresh() }
    LaunchedEffect(auth.user.id) {
        while (true) {
            runCatching { extrasApi.notifications(auth) }.onSuccess { notifications = it }
            delay(8_000)
        }
    }
    LaunchedEffect(cart) { cartStore.save(cart) }
    LaunchedEffect(screen, selectedOrder?.id) {
        if (screen == "orders" || screen == "order") {
            while (true) {
                runCatching { api.loadOrders(auth) }.onSuccess { fresh ->
                    orders = fresh
                    selectedOrder = selectedOrder?.let { old -> fresh.find { it.id == old.id } ?: old }
                }
                runCatching { marketApi.trips(auth) }.onSuccess { fresh ->
                    marketTrips = fresh
                    selectedMarketTrip = selectedMarketTrip?.let { old -> fresh.find { it.id == old.id } ?: old }
                }
                runCatching { laundryApi.orders(auth) }.onSuccess { fresh ->
                    laundryOrders = fresh
                    selectedLaundryOrder = selectedLaundryOrder?.let { old -> fresh.find { it.id == old.id } ?: old }
                }
                if (screen == "order" && selectedOrder != null) {
                    val orderId = selectedOrder!!.id
                    runCatching { api.loadOrderItems(auth, orderId) }.onSuccess { orderItems = it }
                    runCatching { api.loadOrderContext(auth, orderId) }.onSuccess { trackingContext = it }
                }
                // A closed tracking page must not keep requesting live context.
                if (screen == "order" && selectedOrder?.status in setOf("completed", "cancelled", "no_rider_available")) break
                delay(3_000)
            }
        }
    }

    Scaffold(
        containerColor = QgBg,
        bottomBar = {
            if (screen in setOf("home", "search", "cart", "orders")) {
                QgBottomNav(
                    selected = screen,
                    items = listOf(
                        QgNavItem("home", "หน้าหลัก", "home"),
                        QgNavItem("search", "ค้นหา", "search"),
                        QgNavItem("cart", if (cart.sumOf { it.quantity } > 0) "ตะกร้า " + cart.sumOf { it.quantity } else "ตะกร้า", "bag"),
                        QgNavItem("orders", "ออเดอร์", "orders")
                    )
                ) { screen = it }
            }
        }
    ) { insets ->
        Column(
            Modifier.fillMaxSize()
                .padding(insets)
                .background(QgBg)
        ) {
            CustomerTopBar(
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
                    onOpenShop = { shop ->
                        selectedShop = shop
                        products = emptyList()
                        screen = "shop"
                        scope.launch {
                            runCatching { api.loadProducts(auth, shop.id) }
                                .onSuccess { products = it }
                                .onFailure { message = it.message }
                        }
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
                    loading, banners, serviceBanners, category, shops,
                    onCategory = {
                        when (it) {
                            "all" -> category = "all"
                            "market" -> screen = "market"
                            "laundry" -> screen = "laundry"
                            else -> {
                                category = it
                                screen = "category"
                            }
                        }
                    },
                    onShop = { shop ->
                        selectedShop = shop
                        products = emptyList()
                        screen = "shop"
                        scope.launch {
                            runCatching { api.loadProducts(auth, shop.id) }
                                .onSuccess { products = it }
                                .onFailure { message = it.message }
                        }
                    }
                )
                "category" -> ServiceCategoryScreen(
                    loading = loading,
                    category = category,
                    serviceBanner = serviceBanners[if (category == "cafe") "drink" else category],
                    shops = shops,
                    onBack = {
                        category = "all"
                        screen = "home"
                    },
                    onShop = { shop ->
                        selectedShop = shop
                        products = emptyList()
                        screen = "shop"
                        scope.launch {
                            runCatching { api.loadProducts(auth, shop.id) }
                                .onSuccess { products = it }
                                .onFailure { message = it.message }
                        }
                    }
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
                "shop" -> ShopScreen(
                    shop = selectedShop,
                    products = products,
                    cart = cart,
                    onBack = { screen = "home" },
                    onAdd = { product ->
                        val currentShopId = cart.firstOrNull()?.product?.shopId
                        if (currentShopId != null && currentShopId != product.shopId) {
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
                "cart" -> CartScreen(
                    cart = cart,
                    shop = shops.find { it.id == cart.firstOrNull()?.product?.shopId },
                    location = location,
                    address = address,
                    note = note,
                    busy = busy,
                    onAddress = {
                        address = it
                        location = location?.copy(address = it)
                    },
                    onNote = { note = it },
                    onGps = {
                        if (hasLocation(context)) {
                            val p = lastKnownLocation(context)
                            if (p != null) {
                                location = CustomerLocation(p.first, p.second, address)
                                message = "ใช้ตำแหน่งปัจจุบันแล้ว"
                            } else message = "ยังอ่านตำแหน่ง GPS ไม่ได้"
                        } else {
                            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    },
                    onMinus = { id ->
                        cart = cart.mapNotNull {
                            if (it.product.id != id) it
                            else if (it.quantity <= 1) null else it.copy(quantity = it.quantity - 1)
                        }
                    },
                    onPlus = { id ->
                        cart = cart.map { if (it.product.id == id) it.copy(quantity = it.quantity + 1) else it }
                    },
                    onPlace = {
                        val shop = shops.find { it.id == cart.firstOrNull()?.product?.shopId }
                        val loc = location?.copy(address = address.trim())
                        if (shop == null || loc == null || address.isBlank()) {
                            message = "กรุณาเลือกตำแหน่งและกรอกที่อยู่จัดส่ง"
                        } else {
                            busy = true
                            val requestId = UUID.randomUUID().toString()
                            scope.launch {
                                runCatching {
                                    api.saveLocation(auth, loc)
                                    api.placeOrder(auth, requestId, shop, cart, loc, note)
                                }.onSuccess { order ->
                                    cart = emptyList()
                                    note = ""
                                    selectedOrder = order
                                    orderItems = emptyList()
                                    message = "สั่งซื้อสำเร็จ · " + order.number
                                    screen = "order"
                                }.onFailure { e ->
                                    message = when {
                                        e.message?.contains("OUTSIDE_SERVICE_AREA") == true -> "ตำแหน่งอยู่นอกพื้นที่ให้บริการ QueueGo"
                                        e.message?.contains("DELIVERY_DISTANCE_EXCEEDED") == true -> "ระยะทางไกลเกินขอบเขตให้บริการ"
                                        else -> e.message ?: "สั่งซื้อไม่สำเร็จ"
                                    }
                                }
                                busy = false
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
                "order" -> OrderTrackingScreen(
                    auth = auth,
                    order = selectedOrder,
                    items = orderItems,
                    context = trackingContext,
                    shopCategory = selectedOrder?.shopId?.let { id -> shops.find { it.id == id }?.category },
                    busy = busy,
                    onChat = { screen = "chat" },
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
                    orders = orders.filter { it.status == "completed" },
                    onBack = { screen = "profile" }
                )
                "profile" -> ProfileScreen(
                    auth = auth,
                    onSupport = { screen = "support" },
                    logout = logout
                )
            }
        }
    }
}

@Composable
private fun CustomerTopBar(
    cartCount: Int,
    unreadCount: Int,
    onHome: () -> Unit,
    onCart: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color(0xFAFFFFFF))
            .border(0.5.dp, androidx.compose.ui.graphics.Color(0x0D000000))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.clickable(onClick = onHome)) { QueueGoBrand() }
        Spacer(Modifier.weight(1f))
        QgIconButton("bag", badge = cartCount, onClick = onCart)
        Spacer(Modifier.width(8.dp))
        QgIconButton("bell", badge = unreadCount, onClick = onNotifications)
        Spacer(Modifier.width(8.dp))
        QgIconButton("user", onClick = onProfile)
    }
}

@Composable
private fun CustomerHome(
    loading: Boolean,
    banners: List<HomeBanner>,
    serviceBanners: Map<String, ServiceBanner>,
    category: String,
    shops: List<CustomerShop>,
    onCategory: (String) -> Unit,
    onShop: (CustomerShop) -> Unit
) {
    var bannerIndex by remember(banners.size) { mutableStateOf(0) }
    LaunchedEffect(banners.size) {
        if (banners.size > 1) {
            while (true) {
                delay(5_000)
                bannerIndex = (bannerIndex + 1) % banners.size
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(13.dp))
        val banner = banners.getOrNull(bannerIndex)
        if (banner != null) {
            QgRemoteImage(
                banner.image,
                Modifier.fillMaxWidth().height(178.dp),
                "Q"
            )
            if (banners.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    banners.indices.forEach { i ->
                        Box(
                            Modifier
                                .padding(horizontal = 2.dp)
                                .size(if (i == bannerIndex) 7.dp else 5.dp)
                                .clip(CircleShape)
                                .background(if (i == bannerIndex) QgRed else androidx.compose.ui.graphics.Color(0xFFC9CCD1))
                        )
                    }
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(178.dp)
                    .background(QgRed, RoundedCornerShape(20.dp))
                    .padding(18.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text(
                        "QueueGo",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        "บริการใกล้บ้าน ใช้ง่ายในทุกวัน",
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "บริการ",
                color = androidx.compose.ui.graphics.Color(0xFF17191D),
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(11.dp))

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            categories.filter { it.first != "all" }.forEach { (key, label) ->
                CustomerCategoryButton(
                    key = key,
                    label = label,
                    selected = category == key,
                    onClick = { onCategory(key) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ร้านค้าแนะนำ",
                color = androidx.compose.ui.graphics.Color(0xFF17191D),
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            Text("ใกล้คุณ", color = QgRed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(11.dp))

        when {
            loading -> CircularProgressIndicator()
            shops.isEmpty() -> QgCard(Modifier.fillMaxWidth()) {
                Text("ยังไม่พบร้านที่เปิดให้บริการ", color = QgMuted)
            }
            else -> shops.take(12).forEach { shop ->
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
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .width(64.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(if (selected) androidx.compose.ui.graphics.Color(0xFFFFF3F5) else androidx.compose.ui.graphics.Color.White)
                .border(
                    1.dp,
                    if (selected) androidx.compose.ui.graphics.Color(0xFFFFCCD5) else QgLine,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            QgIcon(key, Modifier.size(25.dp), QgRed)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = androidx.compose.ui.graphics.Color(0xFF3A3D42),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun CustomerBlueprintShopCard(
    shop: CustomerShop,
    onClick: () -> Unit
) {
    QgCard(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QgRemoteImage(shop.logo ?: shop.cover, Modifier.size(92.dp), shop.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    shop.name,
                    color = androidx.compose.ui.graphics.Color(0xFF17191D),
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    shop.address ?: categoryLabel(shop.category),
                    color = QgMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
                Spacer(Modifier.height(7.dp))
                QgStatusPill(if (shop.open) "เปิดอยู่" else "ปิดอยู่", shop.open)
            }
        }
    }
}

@Composable
private fun ServiceCategoryScreen(
    loading: Boolean,
    category: String,
    serviceBanner: ServiceBanner?,
    shops: List<CustomerShop>,
    onBack: () -> Unit,
    onShop: (CustomerShop) -> Unit
) {
    val visible = shops.filter {
        when (category) {
            "cafe" -> it.category in setOf("cafe", "drink", "beverage")
            "grocery" -> it.category in setOf("grocery", "convenience")
            "food" -> it.category in setOf("food", "restaurant")
            "shopping" -> it.category == "shopping"
            else -> it.category == category
        }
    }
    val title = categories.find { it.first == category }?.second ?: "บริการ"
    val fallbackSubtitle = when (category) {
        "food" -> "อาหารใกล้คุณ สั่งง่าย ส่งถึงบ้าน"
        "cafe" -> "เครื่องดื่มและคาเฟ่ใกล้คุณ"
        "grocery" -> "ซื้อของใกล้บ้าน เงินหมุนเวียนในชุมชน"
        "shopping" -> "ร้านค้าใกล้บ้าน เลือกซื้อได้สะดวก"
        else -> "บริการใกล้คุณ"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("ย้อนกลับ") }
            Spacer(Modifier.width(10.dp))
            Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(12.dp))

        if (serviceBanner != null && serviceBanner.active && !serviceBanner.image.isNullOrBlank()) {
            Box {
                QgRemoteImage(
                    serviceBanner.image,
                    Modifier.fillMaxWidth().height(172.dp),
                    "Q"
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color(0x66000000))
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text(
                        serviceBanner.title ?: title,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        serviceBanner.subtitle ?: fallbackSubtitle,
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(162.dp)
                    .background(QgRed, RoundedCornerShape(20.dp))
                    .padding(18.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text(
                        title,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        fallbackSubtitle,
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "ร้านที่เปิดให้บริการ",
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(10.dp))
        when {
            loading -> CircularProgressIndicator()
            visible.isEmpty() -> QgCard(Modifier.fillMaxWidth()) {
                Text("ยังไม่พบร้านที่เปิดให้บริการในหมวดนี้", color = QgMuted)
            }
            else -> visible.forEach { shop ->
                CustomerBlueprintShopCard(shop = shop, onClick = { onShop(shop) })
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ShopScreen(
    shop: CustomerShop?,
    products: List<CustomerProduct>,
    cart: List<CartLine>,
    onBack: () -> Unit,
    onAdd: (CustomerProduct) -> Unit,
    onCart: () -> Unit
) {
    if (shop == null) return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box {
            QgRemoteImage(shop.cover ?: shop.logo, Modifier.fillMaxWidth().height(190.dp), shop.name)
            OutlinedButton(onClick = onBack, modifier = Modifier.padding(12.dp).background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(14.dp))) { Text("ย้อนกลับ") }
        }
        Column(Modifier.padding(14.dp)) {
            Text(shop.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
            Text(shop.address ?: categoryLabel(shop.category), color = QgMuted)
            Spacer(Modifier.height(8.dp))
            QgStatusPill(if (shop.open) "เปิดอยู่" else "ปิดอยู่", shop.open)
            Spacer(Modifier.height(18.dp))
            QgSectionTitle("เมนู / สินค้า")
            Spacer(Modifier.height(8.dp))
            if (products.isEmpty()) {
                QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่มีสินค้าพร้อมขาย", color = QgMuted) }
            } else products.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    QgRemoteImage(p.image, Modifier.size(68.dp), p.name)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Bold)
                        if (!p.description.isNullOrBlank()) Text(p.description!!, color = QgMuted, style = MaterialTheme.typography.bodySmall)
                        Text("฿" + "%.0f".format(p.deliveryPrice), fontWeight = FontWeight.ExtraBold)
                    }
                    Button(onClick = { onAdd(p) }, enabled = shop.open) { Text("+") }
                }
                HorizontalDivider()
            }
            if (cart.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = onCart, modifier = Modifier.fillMaxWidth()) {
                    Text("ดูตะกร้า · " + cart.sumOf { it.quantity } + " รายการ · ฿" + "%.0f".format(cart.sumOf { it.product.deliveryPrice * it.quantity }))
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun CartScreen(
    cart: List<CartLine>,
    shop: CustomerShop?,
    location: CustomerLocation?,
    address: String,
    note: String,
    busy: Boolean,
    onAddress: (String) -> Unit,
    onNote: (String) -> Unit,
    onGps: () -> Unit,
    onMinus: (String) -> Unit,
    onPlus: (String) -> Unit,
    onPlace: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        QgSectionTitle("ตะกร้าของคุณ", shop?.name)
        Spacer(Modifier.height(10.dp))
        if (cart.isEmpty()) {
            QgCard(Modifier.fillMaxWidth()) { Text("ตะกร้ายังว่าง", color = QgMuted) }
            return
        }
        cart.forEach { line ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                QgRemoteImage(line.product.image, Modifier.size(58.dp), line.product.name)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(line.product.name, fontWeight = FontWeight.Bold)
                    Text("฿" + "%.0f".format(line.product.deliveryPrice * line.quantity))
                }
                OutlinedButton(onClick = { onMinus(line.product.id) }) { Text("−") }
                Text(line.quantity.toString(), Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { onPlus(line.product.id) }) { Text("+") }
            }
        }
        Spacer(Modifier.height(12.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text("จุดจัดส่ง", fontWeight = FontWeight.ExtraBold)
                Text(
                    if (location == null) "ยังไม่ได้ระบุตำแหน่ง GPS" else "%.5f, %.5f".format(location.latitude, location.longitude),
                    color = QgMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onGps, modifier = Modifier.fillMaxWidth()) { Text("ใช้ตำแหน่งปัจจุบัน") }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = address, onValueChange = onAddress,
                    label = { Text("ที่อยู่ / จุดสังเกตสำหรับจัดส่ง") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = onNote,
                    label = { Text("หมายเหตุสำหรับร้าน (ถ้ามี)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val subtotal = cart.sumOf { it.product.deliveryPrice * it.quantity }
        val fee = runCatching {
            if (shop != null && location != null) CustomerApi().deliveryFee(shop, location.copy(address = address)) else null
        }.getOrNull()
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                SummaryRow("ค่าสินค้า", subtotal)
                SummaryRow("ค่าส่ง", fee)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SummaryRow("รวม", if (fee == null) null else subtotal + fee, true)
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onPlace,
            enabled = !busy && location != null && address.isNotBlank() && fee != null,
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("ยืนยันสั่งซื้อ", fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SummaryRow(label: String, amount: Double?, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.Normal)
        Text(if (amount == null) "--" else "฿" + "%.0f".format(amount), fontWeight = if (strong) FontWeight.Black else FontWeight.Bold)
    }
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
private fun OrderTrackingScreen(
    auth: NativeAuth,
    order: CustomerOrder?,
    items: List<CustomerOrderItem>,
    context: CustomerOrderContext?,
    shopCategory: String?,
    busy: Boolean,
    onChat: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("ย้อนกลับ") }
        Spacer(Modifier.height(12.dp))
        if (order == null) {
            Text("ไม่พบออเดอร์")
            return
        }
        Text(order.number, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        QgStatusPill(statusLabel(order.status), order.status !in setOf("cancelled", "no_rider_available"))
        Spacer(Modifier.height(14.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text("สถานะปัจจุบัน", fontWeight = FontWeight.ExtraBold)
                Text(statusLabel(order.status), color = QgRed, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val stage = when (order.status.lowercase()) {
                    "completed" -> 4
                    "picked_up", "in_progress", "rider_to_customer", "arrived" -> 3
                    "rider_assigned", "assigned", "preparing", "ready" -> 2
                    "accepted", "searching_rider" -> 1
                    else -> 0
                }
                val stages = listOf("สั่งซื้อ", "หา Rider", "รับสินค้า", "กำลังส่ง", "สำเร็จ")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    stages.forEachIndexed { index, label ->
                        QgStatusPill(label, index <= stage)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(order.deliveryAddress ?: "", color = QgMuted)
                context?.shop?.let { shop ->
                    Spacer(Modifier.height(4.dp))
                    Text("ร้าน " + shop.name, color = QgMuted)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (order.status == "searching_rider") {
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("กำลังติดต่อ Rider ที่พร้อมรับงาน", color = QgRed, fontWeight = FontWeight.Black)
                    Text(
                        "QueueGo เสนอออเดอร์ให้ Rider ทีละคน รอบละสูงสุด 30 วินาที หากปฏิเสธหรือหมดเวลา ระบบส่งต่อคนถัดไปอัตโนมัติ",
                        color = QgMuted
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        context?.rider?.let { rider ->
            QgCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QgRemoteImage(rider.photo, Modifier.size(62.dp), rider.name)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (order.status == "completed") "ผู้จัดส่ง" else "Rider ของคุณ",
                            color = QgMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(rider.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        val vehicle = listOfNotNull(rider.vehicleType, rider.vehiclePlate).joinToString(" ")
                        if (vehicle.isNotBlank()) Text(vehicle, color = QgMuted)
                        if (rider.latitude != null && rider.longitude != null &&
                            order.status !in setOf("completed", "cancelled")
                        ) {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "ตำแหน่งล่าสุด %.5f, %.5f".format(rider.latitude, rider.longitude),
                                color = QgRed,
                                fontWeight = FontWeight.Bold
                            )
                            if (!rider.updatedAt.isNullOrBlank()) {
                                Text("อัปเดต " + rider.updatedAt, color = QgMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (order.chatAvailable()) {
                Button(
                    onClick = onChat,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (order.status == "completed") "แชทกับ Rider (ภายใน 30 นาที)" else "แชทกับ Rider")
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (order.status in setOf("pending", "searching_rider") && context?.rider == null) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "กำลังยกเลิก..." else "ยกเลิกคำสั่งซื้อ")
            }
            Spacer(Modifier.height(10.dp))
        }
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text("รายการสินค้า", fontWeight = FontWeight.ExtraBold)
                if (items.isEmpty()) Text("กำลังโหลดรายการ...", color = QgMuted)
                else items.forEach {
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        QgRemoteImage(it.image, Modifier.size(44.dp), it.name)
                        Spacer(Modifier.width(8.dp))
                        Text(it.name + " × " + it.quantity, Modifier.weight(1f))
                        Text("฿" + "%.0f".format(it.totalPrice), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                SummaryRow("ค่าสินค้า", order.subtotal)
                SummaryRow("ค่าส่ง", order.deliveryFee)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SummaryRow("รวม", order.total, true)
            }
        }
        if (order.status == "completed") {
            Spacer(Modifier.height(10.dp))
            CustomerReviewCard(
                auth = auth,
                order = order,
                shopCategory = shopCategory
            )
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
            onDeleted = logout
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

private fun hasLocation(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun lastKnownLocation(context: Context): Pair<Double, Double>? {
    if (!hasLocation(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return runCatching {
        manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { it.latitude to it.longitude }
    }.getOrNull()
}
