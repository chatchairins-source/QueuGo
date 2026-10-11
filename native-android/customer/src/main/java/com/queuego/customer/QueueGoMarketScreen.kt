package com.queuego.customer

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgCard
import com.queuego.shared.QgIcon
import com.queuego.shared.QgInk
import com.queuego.shared.QgLine
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRedDark
import com.queuego.shared.QgRedSoft
import com.queuego.shared.QgRemoteImage
import com.queuego.shared.QgSectionTitle
import kotlinx.coroutines.launch

@Composable
fun MarketNativeScreen(
    auth: NativeAuth?,
    location: CustomerLocation?,
    address: String,
    onAddress: (String) -> Unit,
    onGps: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onRequireLogin: () -> Unit = {}
) {
    val api = remember { CustomerMarketApi() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val actorId = auth?.user?.id ?: "guest"
    val cartStore = remember(actorId) { MarketCartStore(context, actorId) }
    var markets by remember { mutableStateOf<List<MarketInfo>>(emptyList()) }
    var shops by remember { mutableStateOf<List<MarketShop>>(emptyList()) }
    var catalog by remember { mutableStateOf<List<MarketProduct>>(emptyList()) }
    var selectedMarket by remember { mutableStateOf<String?>(null) }
    var selectedShop by remember { mutableStateOf<String?>(null) }
    var cart by remember(actorId) { mutableStateOf<List<MarketCartLine>>(emptyList()) }
    var restoredCart by remember(actorId) { mutableStateOf(false) }
    var activeTrip by remember { mutableStateOf<ActiveMarketTrip?>(null) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(actorId, location?.latitude, location?.longitude) {
        loading = true
        runCatching {
            val m = if (auth == null) api.loadMarketsPublic() else api.loadMarkets(auth)
            markets = m
            shops = if (auth == null) api.loadShopsPublic() else api.loadShops(auth)
            catalog = if (auth == null) api.loadCatalogPublic() else api.loadCatalog(auth)
            if (!restoredCart) {
                fun restore(saved: List<MarketCartStore.Saved>): List<MarketCartLine> =
                    saved.mapNotNull { line ->
                        catalog.find { it.id == line.productId && it.availablePacks > 0 }?.let { fresh ->
                            MarketCartLine(
                                fresh,
                                line.quantity.coerceAtMost(fresh.availablePacks).coerceAtLeast(1)
                            )
                        }
                    }.let { restored ->
                        if (restored.map { it.product.marketId }.toSet().size <= 1) restored else emptyList()
                    }

                val accountCart = restore(cartStore.load())
                val guestStore = if (auth == null) null else MarketCartStore(context, "guest")
                val guestCart = guestStore?.let { restore(it.load()) }.orEmpty()
                cart = when {
                    guestCart.isEmpty() -> accountCart
                    accountCart.isEmpty() -> {
                        guestStore?.save(emptyList())
                        guestCart
                    }
                    accountCart.first().product.marketId == guestCart.first().product.marketId -> {
                        val merged = (accountCart + guestCart)
                            .groupBy { it.product.id }
                            .values
                            .map { rows ->
                                val product = rows.last().product
                                MarketCartLine(
                                    product,
                                    rows.sumOf { it.quantity }
                                        .coerceAtMost(product.availablePacks)
                                        .coerceAtLeast(1)
                                )
                            }
                        guestStore?.save(emptyList())
                        merged
                    }
                    else -> {
                        message = "ตะกร้าตลาดสดก่อนเข้าสู่ระบบเป็นคนละตลาด จึงเก็บไว้แยกกัน"
                        accountCart
                    }
                }
                restoredCart = true
            }
            activeTrip = if (auth == null) null else api.activeTrip(auth)
            if (selectedMarket == null) {
                selectedMarket = activeTrip?.marketId ?: api.nearest(m, location)?.id
            }
        }.onFailure { message = it.message ?: "โหลดตลาดสดไม่สำเร็จ" }
        loading = false
    }

    LaunchedEffect(cart, restoredCart) {
        if (restoredCart) cartStore.save(cart)
    }

    val market = markets.find { it.id == selectedMarket }
    val marketShops = shops.filter { it.marketId == selectedMarket }
    val chosenShop = marketShops.find { it.id == selectedShop }
    val products = catalog.filter {
        it.marketId == selectedMarket && (selectedShop == null || it.shopId == selectedShop)
    }
    val shopCount = cart.map { it.product.shopId }.toSet().size
    val subtotal = cart.sumOf { it.product.price * it.quantity }
    val serviceFee = when {
        shopCount <= 1 -> 0.0
        shopCount == 2 -> 10.0
        else -> 10.0 + (shopCount - 2) * 5.0
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                QgIcon("back", Modifier.size(16.dp), QgInk)
                Spacer(Modifier.width(6.dp))
                Text("ย้อนกลับ", color = QgInk, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "QueueGo Market",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge,
                    color = QgInk
                )
                Text(
                    "ตลาดสดใกล้คุณ · สดใหม่ทุกวัน",
                    color = QgMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        MarketHeroBanner()

        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message!!, color = if (message!!.contains("สำเร็จ") || message!!.contains("แล้ว")) QgRed else MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(14.dp))
        QgSectionTitle("เลือกตลาด", "ระบบแนะนำตลาดที่ใกล้ตำแหน่งของคุณ")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            markets.forEach { m ->
                if (m.id == selectedMarket) {
                    Button(onClick = {
                        selectedMarket = m.id
                        selectedShop = null
                        if (cart.isNotEmpty() && cart.first().product.marketId != m.id) cart = emptyList()
                    }) { Text(m.name) }
                } else {
                    OutlinedButton(onClick = {
                        selectedMarket = m.id
                        selectedShop = null
                        if (cart.isNotEmpty() && cart.first().product.marketId != m.id) cart = emptyList()
                    }) { Text(m.name) }
                }
            }
        }

        if (activeTrip != null) {
            Spacer(Modifier.height(10.dp))
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("Market Trip ที่กำลังดำเนินการ", fontWeight = FontWeight.ExtraBold)
                    Text("สถานะ " + activeTrip!!.status, color = QgMuted)
                    if (activeTrip!!.locked) Text("Rider ออกจากตลาดแล้ว ไม่สามารถเพิ่มร้านได้", color = QgRed)
                    else Text("ยังเพิ่มร้านจากตลาดเดิมได้", color = QgMuted)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        QgSectionTitle("ร้านใน " + (market?.name ?: "ตลาดสด"))
        Spacer(Modifier.height(8.dp))

        when {
            loading -> CircularProgressIndicator()
            marketShops.isEmpty() -> QgCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(52.dp).background(QgRedSoft, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        QgIcon("market", Modifier.size(27.dp), QgRed)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "ยังไม่มีร้านที่เปิดขายในตลาดนี้",
                            color = QgInk,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "เมื่อร้านผ่านการอนุมัติและเปิด Delivery ร้านจะปรากฏที่นี่อัตโนมัติ",
                            color = QgMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            else -> marketShops.forEach { s ->
                val count = catalog.count { it.shopId == s.id && it.marketId == selectedMarket && it.availablePacks > 0 }
                QgCard(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .clickable(enabled = count > 0) { selectedShop = s.id }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QgRemoteImage(s.logo ?: s.cover, Modifier.size(64.dp), s.name)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontWeight = FontWeight.ExtraBold)
                            if (!s.description.isNullOrBlank()) Text(s.description!!, color = QgMuted, style = MaterialTheme.typography.bodySmall)
                            Text(
                                when {
                                    !s.deliveryEnabled -> "ยังไม่เปิดรับ Delivery"
                                    count == 0 -> "ยังไม่มีสินค้าพร้อมขาย"
                                    else -> "พร้อมขาย $count รายการ"
                                },
                                color = if (s.deliveryEnabled && count > 0) QgRed else QgMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (count > 0) Text("ดูสินค้า ›", color = QgRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (chosenShop != null) {
            Spacer(Modifier.height(10.dp))
            QgSectionTitle("สินค้า · " + chosenShop.name)
            Spacer(Modifier.height(6.dp))
            if (products.isEmpty()) {
                QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่มีสินค้าพร้อมขาย", color = QgMuted) }
            } else products.forEach { p ->
                if (p.availablePacks <= 0) return@forEach
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    QgRemoteImage(p.image, Modifier.size(62.dp), p.name)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Bold)
                        Text(p.shopName, color = QgMuted, style = MaterialTheme.typography.bodySmall)
                        Text("฿" + "%.0f".format(p.price) + (p.unit?.let { " / $it" } ?: ""), fontWeight = FontWeight.ExtraBold)
                        Text("พร้อมขาย " + p.availablePacks, color = QgMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    Button(onClick = {
                        val sameMarket = cart.isEmpty() || cart.first().product.marketId == p.marketId
                        if (!sameMarket) {
                            message = "ตะกร้าตลาดสดซื้อข้ามตลาดไม่ได้"
                        } else if (activeTrip?.shopIds?.contains(p.shopId) == true) {
                            message = "ร้านนี้อยู่ใน Market Trip แล้ว"
                        } else {
                            val old = cart.find { it.product.id == p.id }
                            cart = if (old == null) cart + MarketCartLine(p, 1)
                            else if (old.quantity < p.availablePacks) {
                                cart.map { if (it.product.id == p.id) it.copy(quantity = it.quantity + 1) else it }
                            } else cart
                            message = "เพิ่มลงตะกร้าตลาดสดแล้ว"
                        }
                    }) { Text("+") }
                }
                HorizontalDivider()
            }
        }

        if (cart.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("ตะกร้าตลาดสด", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Text(shopCount.toString() + " ร้าน · " + cart.sumOf { it.quantity } + " รายการ", color = QgMuted)
                    Spacer(Modifier.height(8.dp))
                    cart.forEach { line ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(line.product.name, fontWeight = FontWeight.Bold)
                                Text(line.product.shopName, color = QgMuted, style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(onClick = {
                                cart = cart.mapNotNull {
                                    if (it.product.id != line.product.id) it
                                    else if (it.quantity <= 1) null else it.copy(quantity = it.quantity - 1)
                                }
                            }) { Text("−") }
                            Text(line.quantity.toString(), Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
                            OutlinedButton(
                                onClick = {
                                    if (line.quantity < line.product.availablePacks) {
                                        cart = cart.map { if (it.product.id == line.product.id) it.copy(quantity = it.quantity + 1) else it }
                                    }
                                }
                            ) { Text("+") }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    MarketSummary("ค่าสินค้า", subtotal)
                    MarketSummary("ค่ารับหลายร้าน", serviceFee)
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text("ค่าส่ง", Modifier.weight(1f))
                        Text("คำนวณโดยระบบ", color = QgMuted)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("จุดจัดส่ง", fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (location == null) "ยังไม่ได้ระบุตำแหน่ง GPS"
                        else "%.5f, %.5f".format(location.latitude, location.longitude),
                        color = QgMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onGps, modifier = Modifier.fillMaxWidth()) { Text("ใช้ตำแหน่งปัจจุบัน") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = address,
                        onValueChange = onAddress,
                        label = { Text("ที่อยู่ / จุดสังเกต") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(500) },
                        label = { Text("หมายเหตุ (ถ้ามี)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val loc = location?.copy(address = address.trim())
                    if (loc == null || address.isBlank()) {
                        message = "กรุณาเลือกตำแหน่งและกรอกที่อยู่จัดส่ง"
                    } else if (activeTrip?.locked == true) {
                        message = "Rider ออกจากตลาดแล้ว ไม่สามารถเพิ่มร้านได้"
                    } else if (auth == null) {
                        onRequireLogin()
                    } else {
                        busy = true
                        scope.launch {
                            runCatching { api.place(auth, cart, loc, note) }
                                .onSuccess {
                                    cart = emptyList()
                                    message = "สร้าง Market Trip สำเร็จ"
                                    onDone()
                                }
                                .onFailure { message = it.message ?: "ยืนยันตลาดสดไม่สำเร็จ" }
                            busy = false
                        }
                    }
                },
                enabled = !busy && location != null && address.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text(
                    if (auth == null) "เข้าสู่ระบบเพื่อสั่งซื้อ"
                    else if (activeTrip == null) "ยืนยัน Market Trip"
                    else "เพิ่มร้านเข้า Market Trip",
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}


@Composable
private fun MarketHeroBanner() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(156.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFFF04455),
                        QgRed,
                        QgRedDark
                    )
                ),
                RoundedCornerShape(22.dp)
            )
    ) {
        Canvas(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
                .size(width = 154.dp, height = 132.dp)
        ) {
            val white = Color.White.copy(alpha = 0.96f)
            val soft = Color.White.copy(alpha = 0.20f)
            val shadow = Color(0x33000000)

            drawCircle(soft, radius = size.minDimension * 0.40f, center = Offset(size.width * .72f, size.height * .50f))
            drawRoundRect(
                color = shadow,
                topLeft = Offset(size.width * .22f, size.height * .30f),
                size = Size(size.width * .68f, size.height * .56f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
            )
            drawRoundRect(
                color = white,
                topLeft = Offset(size.width * .18f, size.height * .25f),
                size = Size(size.width * .68f, size.height * .56f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
            )
            drawRect(
                color = QgRedSoft,
                topLeft = Offset(size.width * .18f, size.height * .25f),
                size = Size(size.width * .68f, size.height * .18f)
            )
            val awningY = size.height * .25f
            val stripeW = size.width * .68f / 6f
            repeat(6) { index ->
                drawRect(
                    color = if (index % 2 == 0) Color.White else QgRed.copy(alpha = .88f),
                    topLeft = Offset(size.width * .18f + stripeW * index, awningY),
                    size = Size(stripeW, size.height * .14f)
                )
            }
            drawRect(
                color = Color(0xFFF6E7D5),
                topLeft = Offset(size.width * .26f, size.height * .50f),
                size = Size(size.width * .52f, size.height * .20f)
            )
            listOf(
                Triple(.34f, .56f, Color(0xFFFFC857)),
                Triple(.45f, .60f, Color(0xFF6BCB77)),
                Triple(.56f, .56f, Color(0xFFFF6B6B)),
                Triple(.67f, .60f, Color(0xFF4D96FF))
            ).forEach { (x, y, color) ->
                drawCircle(
                    color = color,
                    radius = size.minDimension * .055f,
                    center = Offset(size.width * x, size.height * y)
                )
            }
            drawRect(
                color = Color(0xFFD8B28B),
                topLeft = Offset(size.width * .30f, size.height * .70f),
                size = Size(size.width * .44f, size.height * .08f)
            )
        }

        Column(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.64f)
                .padding(start = 18.dp, end = 6.dp)
        ) {
            Text(
                "ตลาดสดใกล้คุณ",
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "เลือกร้านก่อน แล้วเลือกสินค้าได้หลายร้านในตลาดเดียวกัน",
                color = Color.White.copy(alpha = .95f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MarketSummary(label: String, value: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f))
        Text("฿" + "%.0f".format(value), fontWeight = FontWeight.Bold)
    }
}
