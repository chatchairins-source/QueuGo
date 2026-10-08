package com.queuego.merchant

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgAccountDeletionSection
import com.queuego.shared.QgBg
import com.queuego.shared.QgBottomNav
import com.queuego.shared.QgCard
import com.queuego.shared.QgGreen
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun QueueGoMerchantApp() {
    QueueGoAuthHost(expectedRole = "shop", appLabel = "Merchant") { auth, logout ->
        MerchantShell(auth, logout)
    }
}

@Composable
private fun MerchantShell(auth: NativeAuth, logout: () -> Unit) {
    val api = remember { MerchantApi() }
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf("home") }
    var shop by remember { mutableStateOf<MerchantShop?>(null) }
    var orders by remember { mutableStateOf<List<MerchantOrder>>(emptyList()) }
    var products by remember { mutableStateOf<List<MerchantProduct>>(emptyList()) }
    var readiness by remember { mutableStateOf<ShopReadiness?>(null) }
    var shopOpen by remember { mutableStateOf(true) }
    var todayRevenue by remember { mutableStateOf(MerchantTodayRevenue(0, 0.0, 0.0, 0.0)) }
    var knownPendingIds by remember { mutableStateOf<Set<String>?>(null) }
    var selectedOrder by remember { mutableStateOf<MerchantOrder?>(null) }
    var orderItems by remember { mutableStateOf<List<MerchantOrderItem>>(emptyList()) }
    var gpRate by remember { mutableStateOf(0.0) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = screen != "home") {
        screen = "home"
        selectedOrder = null

    }

    suspend fun refreshAll() {
        loading = true
        runCatching {
            val s = api.loadShop(auth)
            shop = s
            orders = api.loadOrders(auth)
            knownPendingIds = orders.filter { it.status == "pending" }.map { it.id }.toSet()
            if (s != null) {
                products = api.loadProducts(auth, s.id)
                readiness = api.readiness(auth, s.id)
                shopOpen = runCatching { api.shopOpenState(auth, s.id) }.getOrDefault(true)
                val today = LocalDate.now(ZoneId.of("Asia/Bangkok")).toString()
                todayRevenue = runCatching { api.todayRevenue(auth, today) }.getOrDefault(MerchantTodayRevenue(0, 0.0, 0.0, 0.0))
                gpRate = runCatching { api.effectiveGp(auth, s.id) }.getOrDefault(0.0)
            }
        }.onFailure { message = it.message ?: "โหลดข้อมูลร้านไม่สำเร็จ" }
        loading = false
    }

    LaunchedEffect(auth.user.id) { refreshAll() }
    LaunchedEffect(screen) {
        if (screen == "home" || screen == "orders" || screen == "order") {
            while (true) {
                runCatching { api.loadOrders(auth) }.onSuccess { fresh ->
                    val pendingNow = fresh.filter { it.status == "pending" }.map { it.id }.toSet()
                    val before = knownPendingIds
                    if (before != null && pendingNow.any { it !in before }) {
                        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
                        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 650)
                        scope.launch {
                            delay(750)
                            runCatching { tone.release() }
                        }
                    }
                    knownPendingIds = pendingNow
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

    Scaffold(
        containerColor = QgBg,
        bottomBar = {
            if (screen in setOf("home", "orders", "products", "laundry", "profile")) {
                QgBottomNav(
                    selected = screen,
                    items = listOf(
                        QgNavItem("home", "ภาพรวม", "Q"),
                        QgNavItem("orders", "ออเดอร์", "▤"),
                        QgNavItem("products", "สินค้า", "□"),
                        QgNavItem("laundry", "ฝากซัก", "◎"),
                        QgNavItem("profile", "ร้านค้า", "●")
                    )
                ) { screen = it }
            }
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).background(QgBg)) {
            MerchantTopBar(shop?.name ?: "QueueGo Merchant") { screen = "home" }
            if (!message.isNullOrBlank()) {
                Text(
                    message!!,
                    color = if (message!!.contains("แล้ว")) QgGreen else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            when (screen) {
                "home" -> DashboardScreen(
                    shop = shop,
                    orders = orders,
                    readiness = readiness,
                    loading = loading,
                    shopOpen = shopOpen,
                    revenue = todayRevenue,
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
                    onOpen = {
                        selectedOrder = it
                        orderItems = emptyList()
                        screen = "order"
                    }
                )
                "orders" -> MerchantOrdersScreen(orders, loading) {
                    selectedOrder = it
                    orderItems = emptyList()
                    screen = "order"
                }
                "order" -> MerchantOrderDetail(
                    order = selectedOrder,
                    items = orderItems,
                    busy = busy,
                    onBack = { screen = "orders" },
                    onAction = { action, reason -> selectedOrder?.let { runAction(it, action, reason) } }
                )
                "products" -> ProductsScreen(
                    products = products,
                    gpRate = gpRate,
                    busy = busy,
                    onToggle = { product, next ->
                        busy = true
                        scope.launch {
                            runCatching { api.setProductAvailable(auth, product.id, next) }
                                .onSuccess {
                                    products = products.map { if (it.id == product.id) it.copy(available = next) else it }
                                    message = if (next) "เปิดขายสินค้าแล้ว" else "ปิดขายสินค้าแล้ว"
                                }
                                .onFailure { message = it.message }
                            busy = false
                        }
                    },
                    onCreate = { name, desc, price ->
                        val s = shop
                        if (s == null) {
                            message = "ยังไม่พบข้อมูลร้าน"
                        } else {
                            busy = true
                            scope.launch {
                                runCatching { api.createProduct(auth, s.id, name, desc, price, gpRate) }
                                    .onSuccess {
                                        products = api.loadProducts(auth, s.id)
                                        message = "เพิ่มสินค้าแล้ว"
                                    }
                                    .onFailure { message = it.message }
                                busy = false
                            }
                        }
                    }
                )
                "laundry" -> MerchantLaundryScreen(auth)
                "support" -> MerchantSupportScreen(auth) { screen = "profile" }
                "profile" -> MerchantProfileScreen(
                    auth = auth,
                    shop = shop,
                    readiness = readiness,
                    onSupport = { screen = "support" },
                    logout = logout
                )
            }
        }
    }
}

@Composable
private fun MerchantTopBar(title: String, onHome: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.clickable(onClick = onHome)) { QueueGoBrand(suffix = "Merchant") }
        Spacer(Modifier.weight(1f))
        Text(title, color = QgMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun DashboardScreen(
    shop: MerchantShop?,
    orders: List<MerchantOrder>,
    readiness: ShopReadiness?,
    loading: Boolean,
    shopOpen: Boolean,
    revenue: MerchantTodayRevenue,
    busy: Boolean,
    onToggleOpen: (Boolean) -> Unit,
    onOpen: (MerchantOrder) -> Unit
) {
    val todaySales = orders.filter { it.status != "cancelled" }.sumOf { it.subtotal }
    val incoming = orders.filter { it.status in setOf("pending", "accepted", "searching_rider", "rider_assigned", "preparing", "ready") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        if (shop != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QgRemoteImage(shop.logo ?: shop.cover, Modifier.size(64.dp), shop.name)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(shop.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    Text(shop.address ?: "QueueGo Merchant", color = QgMuted, style = MaterialTheme.typography.bodySmall)
                }
                QgStatusPill(if (shop.status == "active") "เปิดใช้งาน" else shop.status, shop.status == "active")
            }
        }
        Spacer(Modifier.height(10.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (shopOpen) "ร้านเปิดรับออเดอร์" else "ร้านปิดชั่วคราว", fontWeight = FontWeight.ExtraBold)
                    Text(if (shopOpen) "ลูกค้าสามารถสั่งซื้อได้" else "ลูกค้าจะเห็นร้านแต่ไม่สามารถสั่งใหม่", color = QgMuted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = shopOpen, onCheckedChange = onToggleOpen, enabled = !busy && shop != null)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (readiness != null && !readiness.complete) {
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("ร้านยังตั้งค่าไม่ครบ", fontWeight = FontWeight.ExtraBold)
                    Text("กรอกข้อมูลร้าน รูป พิกัด และสินค้าให้ครบก่อนส่งตรวจ", color = QgMuted)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("ออเดอร์วันนี้", revenue.orderCount.toString(), Modifier.weight(1f))
            MetricCard("ยอดขาย", "฿" + "%.0f".format(revenue.grossSales), Modifier.weight(1f))
            MetricCard("เงินสดรับ", "฿" + "%.0f".format(revenue.cashReceived), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("ออเดอร์ใหม่", orders.count { it.status == "pending" }.toString(), Modifier.weight(1f))
            MetricCard("กำลังทำ", orders.count { it.status in setOf("preparing", "ready") }.toString(), Modifier.weight(1f))
            MetricCard("GP วันนี้", "฿" + "%.0f".format(revenue.gpDue), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        QgSectionTitle("ออเดอร์ที่ต้องจัดการ", "ข้อมูลจริงจาก QueueGo Production")
        Spacer(Modifier.height(8.dp))
        when {
            loading && orders.isEmpty() -> CircularProgressIndicator()
            incoming.isEmpty() -> QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่มีออเดอร์ที่ต้องจัดการ", color = QgMuted) }
            else -> incoming.take(8).forEach { MerchantOrderCard(it, onOpen) }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    QgCard(modifier) {
        Column {
            Text(label, color = QgMuted, style = MaterialTheme.typography.labelSmall)
            Text(value, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun MerchantOrdersScreen(orders: List<MerchantOrder>, loading: Boolean, onOpen: (MerchantOrder) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        QgSectionTitle("ออเดอร์", "รับ → เตรียม → พร้อมส่ง")
        Spacer(Modifier.height(10.dp))
        if (loading && orders.isEmpty()) CircularProgressIndicator()
        else if (orders.isEmpty()) QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่มีออเดอร์", color = QgMuted) }
        else orders.forEach { MerchantOrderCard(it, onOpen) }
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
                QgStatusPill(merchantStatus(order.status), order.status != "cancelled")
            }
            Spacer(Modifier.height(6.dp))
            Text("ยอดสินค้า ฿" + "%.0f".format(order.subtotal), fontWeight = FontWeight.Bold)
            if (!order.note.isNullOrBlank()) Text(order.note!!, color = QgMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MerchantOrderDetail(
    order: MerchantOrder?,
    items: List<MerchantOrderItem>,
    busy: Boolean,
    onBack: () -> Unit,
    onAction: (String, String?) -> Unit
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
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
        QgStatusPill(merchantStatus(order.status), order.status != "cancelled")
        Spacer(Modifier.height(12.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text("รายการสินค้า", fontWeight = FontWeight.ExtraBold)
                if (items.isEmpty()) Text("กำลังโหลดรายการ...", color = QgMuted)
                else items.forEach {
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        QgRemoteImage(it.image, Modifier.size(48.dp), it.name)
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
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text("ยอดที่ร้านได้รับจาก Rider", color = QgMuted)
                Text("฿" + "%.0f".format(order.subtotal), color = QgRed, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
                Text("แสดงยอดเงินสดเท่านั้น ไม่มีขั้นตอนยืนยันรับเงินซ้ำ", color = QgMuted, style = MaterialTheme.typography.bodySmall)
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
                    onClick = { onAction("cancel", "ร้านไม่สะดวกรับออเดอร์") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ปฏิเสธออเดอร์") }
            }
            "accepted", "searching_rider", "rider_assigned", "assigned" -> {
                Button(
                    onClick = { onAction("preparing", null) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) { Text("เริ่มเตรียมออเดอร์", fontWeight = FontWeight.ExtraBold) }
            }
            "preparing" -> {
                val left = preparationLeft(order.preparingAt, now)
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
                Spacer(Modifier.height(6.dp))
                Text("ตัวนับเวลาอยู่บนปุ่มตาม Flow ร้านค้า", color = QgMuted, style = MaterialTheme.typography.bodySmall)
            }
            "ready" -> QgCard(Modifier.fillMaxWidth()) {
                Text("พร้อมส่งแล้ว · รอ Rider มารับสินค้า", fontWeight = FontWeight.ExtraBold)
            }
            "picked_up", "in_progress" -> QgCard(Modifier.fillMaxWidth()) {
                Text("Rider รับสินค้าออกจากร้านแล้ว", fontWeight = FontWeight.ExtraBold)
            }
            "completed" -> QgCard(Modifier.fillMaxWidth()) {
                Text("ออเดอร์เสร็จสมบูรณ์", color = QgGreen, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ProductsScreen(
    products: List<MerchantProduct>,
    gpRate: Double,
    busy: Boolean,
    onToggle: (MerchantProduct, Boolean) -> Unit,
    onCreate: (String, String?, Double) -> Unit
) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            QgSectionTitle("สินค้า / เมนู", "ราคาขายหน้าร้าน + GP " + "%.0f".format(gpRate) + "%")
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = { adding = !adding }, modifier = Modifier.fillMaxWidth()) {
            Text(if (adding) "ปิดแบบฟอร์ม" else "เพิ่มสินค้า")
        }
        if (adding) {
            Spacer(Modifier.height(10.dp))
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("ชื่อสินค้า") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(desc, { desc = it }, label = { Text("รายละเอียด") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(price, { price = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("ราคาขายหน้าร้าน") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val p = price.toDoubleOrNull()
                            if (name.isNotBlank() && p != null && p >= 0) {
                                onCreate(name, desc, p)
                                name = ""; desc = ""; price = ""; adding = false
                            }
                        },
                        enabled = !busy && name.isNotBlank() && price.toDoubleOrNull() != null,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("บันทึกสินค้า") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (products.isEmpty()) QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่มีสินค้า", color = QgMuted) }
        else products.forEach { p ->
            QgCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QgRemoteImage(p.image, Modifier.size(62.dp), p.name)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.ExtraBold)
                        Text("หน้าร้าน ฿" + "%.0f".format(p.price) + " · Delivery ฿" + "%.0f".format(p.deliveryPrice), color = QgMuted, style = MaterialTheme.typography.bodySmall)
                        Text(if (p.available) "เปิดขาย" else "ปิดขาย", color = if (p.available) QgGreen else QgMuted, fontWeight = FontWeight.Bold)
                    }
                    Switch(checked = p.available, onCheckedChange = { onToggle(p, it) }, enabled = !busy)
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun MerchantProfileScreen(
    auth: NativeAuth,
    shop: MerchantShop?,
    readiness: ShopReadiness?,
    onSupport: () -> Unit,
    logout: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        QgSectionTitle("ร้านค้า")
        Spacer(Modifier.height(12.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text(shop?.name ?: auth.user.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                Text(shop?.address ?: "ยังไม่ได้กรอกที่อยู่ร้าน", color = QgMuted)
                Spacer(Modifier.height(6.dp))
                QgStatusPill(if (readiness?.complete == true) "ข้อมูลพร้อมขาย" else "ตั้งค่ายังไม่ครบ", readiness?.complete == true)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (readiness != null) {
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("Store Readiness", fontWeight = FontWeight.ExtraBold)
                    val labels = listOf(
                        "shop_info" to "ข้อมูลร้าน",
                        "storefront_image" to "รูปหน้าร้าน",
                        "cover_image" to "รูปหน้าปก",
                        "location" to "ตำแหน่งร้าน",
                        "catalog" to "สินค้า/บริการ"
                    )
                    labels.forEach { (key, label) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(label, Modifier.weight(1f))
                            Text(if (readiness.checks.optBoolean(key, false)) "พร้อม" else "ยังไม่ครบ", color = if (readiness.checks.optBoolean(key, false)) QgGreen else QgRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSupport, modifier = Modifier.fillMaxWidth()) {
            Text("ข้อความถึง QueueGo Admin")
        }
        Spacer(Modifier.height(10.dp))
        QgAccountDeletionSection(
            accessToken = auth.session.accessToken,
            onDeleted = logout
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = logout, modifier = Modifier.fillMaxWidth()) { Text("ออกจากระบบ") }
    }
}

private fun merchantStatus(status: String): String = when (status.lowercase()) {
    "pending" -> "ออเดอร์ใหม่"
    "accepted" -> "รับแล้ว"
    "searching_rider" -> "กำลังหา Rider"
    "rider_assigned", "assigned" -> "Rider รับงานแล้ว"
    "preparing" -> "กำลังเตรียม"
    "ready" -> "พร้อมส่ง"
    "picked_up" -> "Rider รับสินค้าแล้ว"
    "in_progress" -> "กำลังจัดส่ง"
    "completed" -> "สำเร็จ"
    "cancelled" -> "ยกเลิก"
    else -> status
}

private fun preparationLeft(preparingAt: String?, now: Long): Int {
    val start = runCatching { Instant.parse(preparingAt ?: "").toEpochMilli() }.getOrNull() ?: now
    val target = start + 20 * 60 * 1000L
    return (((target - now).coerceAtLeast(0L) + 999L) / 1000L).toInt()
}

private fun formatSeconds(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return min.toString().padStart(2, '0') + ":" + sec.toString().padStart(2, '0')
}
