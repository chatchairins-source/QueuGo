package com.queuego.customer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgBg
import com.queuego.shared.QgBottomNav
import com.queuego.shared.QgCard
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var screen by remember { mutableStateOf("home") }
    var category by remember { mutableStateOf("all") }
    var shops by remember { mutableStateOf<List<CustomerShop>>(emptyList()) }
    var orders by remember { mutableStateOf<List<CustomerOrder>>(emptyList()) }
    var banner by remember { mutableStateOf<HomeBanner?>(null) }
    var selectedShop by remember { mutableStateOf<CustomerShop?>(null) }
    var selectedOrder by remember { mutableStateOf<CustomerOrder?>(null) }
    var products by remember { mutableStateOf<List<CustomerProduct>>(emptyList()) }
    var orderItems by remember { mutableStateOf<List<CustomerOrderItem>>(emptyList()) }
    var cart by remember { mutableStateOf<List<CartLine>>(emptyList()) }
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
                val s = api.loadShops(auth)
                val o = api.loadOrders(auth)
                val l = api.loadSavedLocation(auth)
                val b = api.loadHomeBanner(auth)
                listOf(s, o, l, b)
            }.onSuccess {
                shops = it[0] as List<CustomerShop>
                orders = it[1] as List<CustomerOrder>
                val saved = it[2] as CustomerLocation?
                if (saved != null) {
                    location = saved
                    if (address.isBlank()) address = saved.address
                }
                banner = it[3] as HomeBanner?
                message = null
            }.onFailure { message = it.message ?: "โหลดข้อมูลไม่สำเร็จ" }
            loading = false
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

    LaunchedEffect(auth.user.id) { refresh() }
    LaunchedEffect(screen, selectedOrder?.id) {
        if (screen == "orders" || screen == "order") {
            while (true) {
                runCatching { api.loadOrders(auth) }.onSuccess { fresh ->
                    orders = fresh
                    selectedOrder = selectedOrder?.let { old -> fresh.find { it.id == old.id } ?: old }
                }
                if (screen == "order" && selectedOrder != null) {
                    runCatching { api.loadOrderItems(auth, selectedOrder!!.id) }.onSuccess { orderItems = it }
                }
                delay(3_000)
            }
        }
    }

    Scaffold(
        containerColor = QgBg,
        bottomBar = {
            if (screen in setOf("home", "cart", "orders", "profile")) {
                QgBottomNav(
                    selected = screen,
                    items = listOf(
                        QgNavItem("home", "หน้าหลัก", "Q"),
                        QgNavItem("cart", "ตะกร้า", cart.sumOf { it.quantity }.toString()),
                        QgNavItem("orders", "ออเดอร์", "▤"),
                        QgNavItem("profile", "บัญชี", "●")
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
                onHome = { screen = "home" },
                onCart = { screen = "cart" }
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
                "home" -> CustomerHome(
                    loading, banner, category, shops,
                    onCategory = {
                        when (it) {
                            "market" -> screen = "market"
                            "laundry" -> screen = "laundry"
                            else -> category = it
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
                    onOpen = {
                        selectedOrder = it
                        orderItems = emptyList()
                        screen = "order"
                    }
                )
                "order" -> OrderTrackingScreen(
                    order = selectedOrder,
                    items = orderItems,
                    onBack = { screen = "orders" }
                )
                "profile" -> ProfileScreen(auth, logout)
            }
        }
    }
}

@Composable
private fun CustomerTopBar(cartCount: Int, onHome: () -> Unit, onCart: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.clickable(onClick = onHome)) { QueueGoBrand() }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onCart) {
            Text(if (cartCount > 0) "ตะกร้า $cartCount" else "ตะกร้า")
        }
    }
}

@Composable
private fun CustomerHome(
    loading: Boolean,
    banner: HomeBanner?,
    category: String,
    shops: List<CustomerShop>,
    onCategory: (String) -> Unit,
    onShop: (CustomerShop) -> Unit
) {
    val visible = if (category == "all") shops else shops.filter {
        when (category) {
            "cafe" -> it.category in setOf("cafe", "drink", "beverage")
            "market" -> it.category in setOf("market", "fresh", "fresh_market")
            else -> it.category == category
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("บริการใกล้คุณ", color = QgMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        if (banner != null) {
            QgRemoteImage(banner.image, Modifier.fillMaxWidth().height(168.dp), "Q")
            if (!banner.title.isNullOrBlank()) {
                Text(banner.title!!, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            Box(
                Modifier.fillMaxWidth().height(145.dp).background(QgRed, RoundedCornerShape(20.dp)).padding(18.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text("QueueGo", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
                    Text("อาหาร เครื่องดื่ม ของชำ ตลาดสด และบริการใกล้บ้าน", color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { (key, label) ->
                if (category == key) Button(onClick = { onCategory(key) }) { Text(label) }
                else OutlinedButton(onClick = { onCategory(key) }) { Text(label) }
            }
        }
        Spacer(Modifier.height(16.dp))
        QgSectionTitle(if (category == "all") "ร้านค้าแนะนำ" else categories.find { it.first == category }?.second ?: "ร้านค้า")
        Spacer(Modifier.height(8.dp))
        when {
            loading -> CircularProgressIndicator()
            visible.isEmpty() -> QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่พบร้านที่เปิดให้บริการในหมวดนี้", color = QgMuted) }
            else -> visible.forEach { shop ->
                QgCard(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onShop(shop) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QgRemoteImage(shop.logo ?: shop.cover, Modifier.size(72.dp), shop.name)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(shop.name, fontWeight = FontWeight.ExtraBold)
                            Text(shop.address ?: categoryLabel(shop.category), color = QgMuted, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(5.dp))
                            QgStatusPill(if (shop.open) "เปิดอยู่" else "ปิดอยู่", shop.open)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
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
private fun OrdersScreen(loading: Boolean, orders: List<CustomerOrder>, onOpen: (CustomerOrder) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        QgSectionTitle("ออเดอร์", "ติดตามสถานะคำสั่งซื้อแบบ Production")
        Spacer(Modifier.height(10.dp))
        if (loading && orders.isEmpty()) CircularProgressIndicator()
        else if (orders.isEmpty()) QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่มีออเดอร์", color = QgMuted) }
        else orders.forEach { order ->
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
}

@Composable
private fun OrderTrackingScreen(order: CustomerOrder?, items: List<CustomerOrderItem>, onBack: () -> Unit) {
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
                Spacer(Modifier.height(6.dp))
                Text(order.deliveryAddress ?: "", color = QgMuted)
            }
        }
        Spacer(Modifier.height(10.dp))
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
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ProfileScreen(auth: NativeAuth, logout: () -> Unit) {
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
