package com.queuego.customer

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgCard
import com.queuego.shared.QgIcon
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import com.queuego.shared.QgSectionTitle
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.queuego.shared.QueueGoHttpException

@Composable
fun MarketNativeScreen(
    auth: NativeAuth?,
    location: CustomerLocation?,
    address: String,
    onAddress: (String) -> Unit,
    onGps: () -> Unit,
    serviceBanner: ServiceBanner? = null,
    cartPage: Boolean = false,
    onOpenCart: () -> Unit,
    onBrowse: () -> Unit,
    onBack: () -> Unit,
    onDone: (String) -> Unit,
    onRequireLogin: () -> Unit = {},
    onBannerLink: (String) -> Unit = {}
) {
    val api = remember { CustomerMarketApi() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val actorId = auth?.user?.id ?: "guest"
    val cartStore = remember(actorId) { MarketCartStore(context, actorId) }
    val checkout = remember(actorId) { CustomerCheckoutRecovery<PendingMarketCheckout, org.json.JSONObject>(cartStore.journal()) }
    var checkoutPending by remember(actorId) { mutableStateOf(cartStore.hasPendingCheckout()) }
    var pricing by remember { mutableStateOf<MarketPricing?>(null) }
    var marketMenuOpen by remember { mutableStateOf(false) }
    var markets by remember { mutableStateOf<List<MarketInfo>>(emptyList()) }
    var shops by remember { mutableStateOf<List<MarketShop>>(emptyList()) }
    var catalog by remember { mutableStateOf<List<MarketProduct>>(emptyList()) }
    var selectedMarket by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("all") }
    var selectedShop by remember { mutableStateOf<String?>(null) }
    var cart by remember(actorId) { mutableStateOf<List<MarketCartLine>>(emptyList()) }
    var restoredCart by remember(actorId) { mutableStateOf(false) }
    var activeTrip by remember { mutableStateOf<ActiveMarketTrip?>(null) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var loadFailed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    var hasResumed by remember(actorId) { mutableStateOf(false) }
    LifecycleResumeEffect(actorId) {
        if (hasResumed) reload++ else hasResumed = true
        onPauseOrDispose { }
    }

    LaunchedEffect(actorId, location?.latitude, location?.longitude, reload) {
        loading = true
        loadFailed = false
        pricing = null
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
                val guestCart = if (checkoutPending) emptyList() else guestStore?.let { restore(it.load()) }.orEmpty()
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
            if (restoredCart && !checkoutPending) {
                cart = cart.mapNotNull { line ->
                    catalog.find { it.id == line.product.id && it.marketId == line.product.marketId && it.availablePacks > 0 }
                        ?.let { fresh -> MarketCartLine(fresh, line.quantity.coerceAtMost(fresh.availablePacks).coerceAtMost(99)) }
                }
            }
            activeTrip = if (auth == null) null else api.activeTrip(auth)
            if (selectedMarket == null) {
                selectedMarket = activeTrip?.marketId ?: cart.firstOrNull()?.product?.marketId ?: api.nearest(m, location)?.id
            }
            pricing = api.loadPricing(auth)
        }.onFailure { if (it is CancellationException) throw it; loadFailed = true; message = it.message ?: "โหลดตลาดสดไม่สำเร็จ" }
        loading = false
    }

    LaunchedEffect(cartPage) { if (cartPage) reload++ }

    LaunchedEffect(cart, restoredCart, checkoutPending) {
        if (restoredCart && !checkoutPending) cartStore.save(cart)
    }

    val market = markets.find { it.id == selectedMarket }
    val marketShops = shops.filter { it.marketId == selectedMarket }
    val chosenShop = marketShops.find { it.id == selectedShop }
    val products = catalog.filter {
        it.marketId == selectedMarket && (selectedShop == null || it.shopId == selectedShop) &&
            (selectedCategory == "all" || marketCategoryKey(it.category, it.name) == selectedCategory)
    }
    val shopCount = cart.map { it.product.shopId }.toSet().size
    val subtotal = cart.sumOf { it.product.price * it.quantity }
    val serviceFee = pricing?.multiShopFee(shopCount)
    val tripBlocksCart = activeTrip?.let { it.locked || (cart.isNotEmpty() && it.marketId != cart.first().product.marketId) } == true
    val firstShop = cart.firstOrNull()?.product?.shopId?.let { id -> shops.find { it.id == id } }
    val deliveryEstimate = if (location != null && firstShop?.latitude != null && firstShop.longitude != null) {
        pricing?.deliveryEstimate(api.distanceKm(firstShop.latitude, firstShop.longitude, location.latitude, location.longitude))
    } else null


    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    key(cartPage) {
    Column(Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Row(
            Modifier.padding(top = 7.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .clickable { if (cartPage) onBrowse() else onBack() },
                contentAlignment = Alignment.Center
            ) {
                QgIcon("back", Modifier.size(20.dp), Color(0xFF17191D))
            }
            Spacer(Modifier.width(8.dp))
            if (cartPage) Text("ตะกร้าตลาดสด", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            else Text("ตลาดสด", fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
        }

        if (!cartPage) CustomerServiceBanner(
            banner = serviceBanner,
            fallbackDrawable = R.drawable.qg_market_banner,
            fallbackTitle = "ตลาดสด",
            fallbackSubtitle = "ตลาดสดใกล้คุณ สดใหม่ทุกวัน",
            onLink = onBannerLink
        )

        if (checkoutPending) {
            Text("กรุณาตรวจผล Market Trip เดิมก่อนแก้ตะกร้า · ระบบใช้รายการ ที่อยู่ และหมายเหตุเดิม", color = QgRed, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            if (loadFailed) OutlinedButton(onClick = { reload++ }, enabled = !loading) { Text("ลองอีกครั้ง") }
            Text(message!!, color = if (message!!.contains("สำเร็จ") || message!!.contains("แล้ว")) QgRed else MaterialTheme.colorScheme.error)
        }

        if (!cartPage) {
        Spacer(Modifier.height(14.dp))
        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text("เลือกตลาด")
                Box {
                    OutlinedButton(onClick = { marketMenuOpen = true }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                        Text(market?.name ?: "ยังไม่มีตลาดที่เปิดใช้งาน", Modifier.weight(1f))
                        Text("⌄")
                    }
                    DropdownMenu(expanded = marketMenuOpen, onDismissRequest = { marketMenuOpen = false }) {
                        markets.forEach { m ->
                            DropdownMenuItem(text = { Text(m.name) }, onClick = {
                                selectedMarket = m.id
                                selectedShop = null
                                selectedCategory = "all"
                                marketMenuOpen = false
                            })
                        }
                    }
                }
                if (location != null && market?.latitude != null && market.longitude != null) {
                    Text("เลือกตลาดใกล้ตำแหน่งคุณก่อน · " + "%.1f".format(api.distanceKm(location.latitude, location.longitude, market.latitude, market.longitude)) + " กม.", color = QgMuted, style = MaterialTheme.typography.bodySmall)
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
        QgSectionTitle("เลือกประเภทสินค้าในตลาด", "เนื้อสัตว์ ปลา ผักสด ผลไม้ และของตลาดประเภทอื่น ๆ")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            marketCategories.forEach { (id, label) ->
                if (id == selectedCategory) Button(onClick = { selectedCategory = id; selectedShop = null }) { Text(label) }
                else OutlinedButton(onClick = { selectedCategory = id; selectedShop = null }) { Text(label) }
            }
        }
        Spacer(Modifier.height(16.dp))
        QgSectionTitle("ร้านใน " + (market?.name ?: "ตลาดสด"))
        Spacer(Modifier.height(8.dp))

        when {
            loading -> CircularProgressIndicator()
            marketShops.isEmpty() -> QgCard(Modifier.fillMaxWidth()) { Text("ยังไม่พบร้านในตลาดนี้", color = QgMuted) }
            else -> marketShops.forEach { s ->
                val count = catalog.count { it.shopId == s.id && it.marketId == selectedMarket && it.availablePacks > 0 }
                QgCard(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        .clickable(enabled = count > 0) { selectedShop = s.id; selectedCategory = "all" }
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
                        Text(p.shopName + " · " + marketCategories.getValue(marketCategoryKey(p.category, p.name)), color = QgMuted, style = MaterialTheme.typography.bodySmall)
                        Text("฿" + "%.0f".format(p.price) + (p.unit?.let { " / $it" } ?: ""), fontWeight = FontWeight.ExtraBold)
                        Text("พร้อมขาย " + p.availablePacks, color = QgMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    Button(enabled = !busy && !checkoutPending, onClick = {
                        val sameMarket = cart.isEmpty() || cart.first().product.marketId == p.marketId
                        if (!sameMarket) {
                            message = "ตะกร้าตลาดสดซื้อข้ามตลาดไม่ได้"
                        } else if (activeTrip?.shopIds?.contains(p.shopId) == true) {
                            message = "ร้านนี้อยู่ใน Market Trip แล้ว"
                        } else {
                            val old = cart.find { it.product.id == p.id }
                            cart = if (old == null) cart + MarketCartLine(p, 1)
                            else if (old.quantity < minOf(99, p.availablePacks)) {
                                cart.map { if (it.product.id == p.id) it.copy(quantity = it.quantity + 1) else it }
                            } else cart
                            message = "เพิ่มลงตะกร้าตลาดสดแล้ว"
                        }
                    }) { Text("+") }
                }
                HorizontalDivider()
            }
        }

        } // Browse: checkout fields belong only to market-cart.

        if (cartPage && loading) CircularProgressIndicator()
        if (cartPage && !loading && cart.isEmpty() && !checkoutPending) {
            Text("ตะกร้าตลาดสดว่าง", color = QgMuted, modifier = Modifier.padding(vertical = 24.dp))
            Button(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) { Text("เลือกสินค้าในตลาด") }
        }
        if (cartPage && (cart.isNotEmpty() || checkoutPending)) {
            Spacer(Modifier.height(16.dp))
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("ตะกร้าตลาดสด", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Text(shopCount.toString() + " ร้าน · " + cart.sumOf { it.quantity } + " รายการ", color = QgMuted)
                    Spacer(Modifier.height(8.dp))
                    cart.groupBy { it.product.shopId }.values.forEach { shopLines ->
                        Text(shopLines.first().product.shopName, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(top = 12.dp))
                        shopLines.forEach { line ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                QgRemoteImage(line.product.image, Modifier.size(62.dp), line.product.name)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(line.product.name, fontWeight = FontWeight.Bold)
                                    Text("฿" + "%.0f".format(line.product.price) + (line.product.unit?.let { " / $it" } ?: ""), color = QgRed)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(enabled = !busy && !checkoutPending, onClick = {
                                            cart = cart.mapNotNull {
                                                if (it.product.id != line.product.id) it
                                                else if (it.quantity <= 1) null else it.copy(quantity = it.quantity - 1)
                                            }
                                        }) { Text("−") }
                                        Text(line.quantity.toString(), Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
                                        OutlinedButton(enabled = !busy && !checkoutPending && line.quantity < minOf(99, line.product.availablePacks), onClick = {
                                            if (line.quantity < minOf(99, line.product.availablePacks)) {
                                                cart = cart.map { if (it.product.id == line.product.id) it.copy(quantity = it.quantity + 1) else it }
                                            }
                                        }) { Text("+") }
                                    }
                                    Text("ลบรายการ", color = QgMuted, modifier = Modifier.clickable(enabled = !busy && !checkoutPending) {
                                        cart = cart.filterNot { it.product.id == line.product.id }
                                    }.padding(vertical = 8.dp))
                                }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    MarketSummary("ค่าสินค้า", subtotal)
                    if (activeTrip != null) {
                        Text("ค่ารับหลายร้าน · คำนวณจากราคาที่ล็อกไว้ใน Trip เดิม", color = QgMuted)
                    } else {
                        serviceFee?.let { MarketSummary("ค่ารับหลายร้าน", it) }
                            ?: Text("ยังไม่ได้รับค่าบริการล่าสุด", color = QgMuted)
                        deliveryEstimate?.let { MarketSummary("ค่ารอบ/จัดส่งโดยประมาณ", it) }
                            ?: Text("ค่ารอบ/จัดส่งโดยประมาณ · เลือกที่อยู่จัดส่ง", color = QgMuted)
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
                    OutlinedButton(onClick = onGps, enabled = !busy && !checkoutPending, modifier = Modifier.fillMaxWidth()) { Text("ใช้ตำแหน่งปัจจุบัน") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        enabled = !busy && !checkoutPending,
                        value = address,
                        onValueChange = onAddress,
                        label = { Text("ที่อยู่ / จุดสังเกต") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        enabled = !busy && !checkoutPending,
                        value = note,
                        onValueChange = { note = it.take(500) },
                        label = { Text("หมายเหตุ (ถ้ามี)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (tripBlocksCart && !checkoutPending) Text("มี Market Trip ที่ยังไม่จบ หรือ Rider ออกจากตลาดแล้ว จึงเริ่ม/เพิ่มร้านไม่ได้", color = QgRed)
            Button(
                onClick = {
                    if (busy) return@Button
                    val loc = location?.copy(address = address.trim())
                    if (!checkoutPending && (loc == null || address.isBlank())) {
                        message = "กรุณาเลือกตำแหน่งและกรอกที่อยู่จัดส่ง"
                    } else if (!checkoutPending && activeTrip?.locked == true) {
                        message = "Rider ออกจากตลาดแล้ว ไม่สามารถเพิ่มร้านได้"
                    } else if (auth == null) {
                        onRequireLogin()
                    } else {
                        busy = true
                        scope.launch {
                            try {
                                val receipt = checkout.submit(
                                    create = { api.prepareCheckout(auth, cart, requireNotNull(loc), note, cartStore) },
                                    send = { pending ->
                                        checkoutPending = true
                                        api.sendCheckout(auth, pending)
                                    },
                                    // Add-request receipts are server-only. Retry replays the same RPC/IDs;
                                    // never infer success from an active trip or manufacture a receipt.
                                    recover = { null },
                                    definitiveRejection = { e -> e is QueueGoHttpException && e.statusCode in setOf(400, 422) }
                                )
                                cart = emptyList()
                                message = "สร้าง Market Trip สำเร็จ"
                                onDone(receipt.getString("market_order_id"))
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (e: Exception) {
                                message = e.message ?: "ยืนยันตลาดสดไม่สำเร็จ"
                            } finally {
                                checkoutPending = cartStore.hasPendingCheckout()
                                busy = false
                            }
                        }
                    }
                },
                enabled = !busy && (checkoutPending || (!loading && !loadFailed && !tripBlocksCart && location != null && address.isNotBlank())),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text(
                    if (checkoutPending) "ตรวจผล Market Trip เดิม"
                    else if (auth == null) "เข้าสู่ระบบเพื่อสั่งซื้อ"
                    else if (activeTrip == null) "ยืนยัน Market Trip"
                    else "เพิ่มร้านเข้า Market Trip",
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(Modifier.height(if (!cartPage && (cart.isNotEmpty() || checkoutPending)) 100.dp else 40.dp))
    }
    }
    if (!cartPage && (cart.isNotEmpty() || checkoutPending)) {
        Button(
            onClick = onOpenCart,
            modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp)
                .fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp).height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (checkoutPending) "ตรวจผล Market Trip เดิม" else "ตะกร้าตลาดสด · $shopCount ร้าน · ${cart.sumOf { it.quantity }} รายการ", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            if (!checkoutPending) Text("฿" + "%.0f".format(subtotal), fontWeight = FontWeight.Bold)
        }
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
