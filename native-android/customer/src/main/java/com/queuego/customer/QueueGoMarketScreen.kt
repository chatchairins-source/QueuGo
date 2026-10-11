package com.queuego.customer

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgCard
import com.queuego.shared.QgIcon
import com.queuego.shared.QgInk
import com.queuego.shared.QgLine
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.atan2

private val MARKET_CATEGORY_ORDER = listOf(
    "meat",
    "seafood",
    "vegetable",
    "fruit",
    "egg_tofu_fresh",
    "seasoning_herb",
    "rice_dry",
    "processed_ready_to_cook",
    "prepared_food",
    "dessert",
    "local",
    "other_market"
)

private val MARKET_CATEGORY_LABELS = mapOf(
    "meat" to "เนื้อสัตว์",
    "seafood" to "ปลา / อาหารทะเล",
    "vegetable" to "ผักสด",
    "fruit" to "ผลไม้",
    "egg_tofu_fresh" to "ไข่ / เต้าหู้ / ของสด",
    "seasoning_herb" to "เครื่องปรุง / สมุนไพร",
    "rice_dry" to "ข้าว / ของแห้ง / ธัญพืช",
    "processed_ready_to_cook" to "อาหารแปรรูป / พร้อมปรุง",
    "prepared_food" to "อาหารทำสำเร็จจากตลาด",
    "dessert" to "ขนม / ของหวาน",
    "local" to "ของพื้นบ้าน / สินค้าชุมชน",
    "other_market" to "อื่นๆ ในตลาดสด"
)

@Composable
fun MarketNativeScreen(
    auth: NativeAuth?,
    location: CustomerLocation?,
    address: String,
    serviceBanner: ServiceBanner?,
    onAddress: (String) -> Unit,
    onGps: () -> Unit,
    onChooseAddress: () -> Unit,
    onBannerLink: (String) -> Unit = {},
    onBack: () -> Unit,
    onDone: () -> Unit,
    onRequireLogin: () -> Unit = {}
) {
    val api = remember { CustomerMarketApi() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val marketFallbackDataUri = remember {
        context.resources.openRawResource(R.raw.qg_market_banner_data_uri)
            .bufferedReader()
            .use { it.readText() }
    }
    val effectiveMarketBanner = remember(serviceBanner, marketFallbackDataUri) {
        ServiceBanner(
            key = "market",
            image = serviceBanner?.image?.takeIf { it.isNotBlank() } ?: marketFallbackDataUri,
            title = serviceBanner?.title?.takeIf { it.isNotBlank() } ?: "ตลาดสด",
            subtitle = serviceBanner?.subtitle?.takeIf { it.isNotBlank() }
                ?: "ตลาดสดใกล้คุณ สดใหม่ทุกวัน",
            active = serviceBanner?.active ?: true,
            link = serviceBanner?.link
        )
    }
    val actorId = auth?.user?.id ?: "guest"
    val cartStore = remember(actorId) { MarketCartStore(context, actorId) }

    var markets by remember { mutableStateOf<List<MarketInfo>>(emptyList()) }
    var shops by remember { mutableStateOf<List<MarketShop>>(emptyList()) }
    var catalog by remember { mutableStateOf<List<MarketProduct>>(emptyList()) }
    var rules by remember { mutableStateOf(MarketRules()) }
    var selectedMarket by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("all") }
    var shopFocusId by remember { mutableStateOf<String?>(null) }
    var marketMenuExpanded by remember { mutableStateOf(false) }
    var showCart by remember { mutableStateOf(false) }
    var cart by remember(actorId) { mutableStateOf<List<MarketCartLine>>(emptyList()) }
    var restoredCart by remember(actorId) { mutableStateOf(false) }
    var activeTrip by remember { mutableStateOf<ActiveMarketTrip?>(null) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = showCart) { showCart = false }

    LaunchedEffect(actorId, location?.latitude, location?.longitude) {
        loading = true
        runCatching {
            val loadedMarkets = if (auth == null) api.loadMarketsPublic() else api.loadMarkets(auth)
            val loadedShops = if (auth == null) api.loadShopsPublic() else api.loadShops(auth)
            val loadedCatalog = if (auth == null) api.loadCatalogPublic() else api.loadCatalog(auth)
            val loadedRules = runCatching { api.loadRules(auth) }.getOrDefault(MarketRules())
            val sortedMarkets = sortMarketsForLocation(loadedMarkets, location)

            markets = sortedMarkets
            shops = loadedShops
            catalog = loadedCatalog
            rules = loadedRules

            var restored = cart
            if (!restoredCart) {
                fun restore(saved: List<MarketCartStore.Saved>): List<MarketCartLine> =
                    saved.mapNotNull { line ->
                        loadedCatalog.find { it.id == line.productId && it.availablePacks > 0 }?.let { fresh ->
                            MarketCartLine(
                                fresh,
                                line.quantity.coerceAtMost(fresh.availablePacks).coerceAtLeast(1)
                            )
                        }
                    }.let { rows ->
                        if (rows.map { it.product.marketId }.toSet().size <= 1) rows else emptyList()
                    }

                val accountCart = restore(cartStore.load())
                val guestStore = if (auth == null) null else MarketCartStore(context, "guest")
                val guestCart = guestStore?.let { restore(it.load()) }.orEmpty()
                restored = when {
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
                cart = restored
                restoredCart = true
            }

            val active = if (auth == null) null else api.activeTrip(auth)
            activeTrip = active
            val preferred = active?.marketId
                ?: restored.firstOrNull()?.product?.marketId
                ?: selectedMarket
                ?: sortedMarkets.firstOrNull()?.id

            selectedMarket = if (
                preferred != null && sortedMarkets.any { it.id == preferred }
            ) preferred else sortedMarkets.firstOrNull()?.id
        }.onFailure {
            message = it.message ?: "โหลดตลาดสดไม่สำเร็จ"
        }
        loading = false
    }

    LaunchedEffect(cart, restoredCart) {
        if (restoredCart) cartStore.save(cart)
    }

    val market = markets.find { it.id == selectedMarket }
    val marketShops = shops.filter { it.marketId == selectedMarket }
    val marketCatalog = catalog.filter { it.marketId == selectedMarket }
    val visibleProducts = marketCatalog.filter { product ->
        product.availablePacks > 0 &&
            (selectedCategory == "all" || marketCategoryKey(product.category, product.name) == selectedCategory) &&
            (shopFocusId == null || product.shopId == shopFocusId)
    }
    val cartCount = cart.sumOf { it.quantity }
    val cartTotal = cart.sumOf { it.product.price * it.quantity }

    fun addProduct(product: MarketProduct) {
        val active = activeTrip
        if (active?.locked == true) {
            message = "Rider ออกจากตลาดแล้ว ไม่สามารถเพิ่มร้านได้"
            return
        }
        val targetMarket = active?.marketId
            ?: cart.firstOrNull()?.product?.marketId
            ?: product.marketId
        if (product.marketId != targetMarket) {
            message = "ตะกร้าตลาดสดซื้อข้ามตลาดไม่ได้"
            return
        }
        if (active?.shopIds?.contains(product.shopId) == true) {
            message = "ร้านนี้อยู่ใน Market Trip แล้ว"
            return
        }
        val usedShops = buildSet {
            addAll(cart.map { it.product.shopId })
            addAll(active?.shopIds.orEmpty())
        }
        if (
            usedShops.isNotEmpty() &&
            product.shopId !in usedShops &&
            !rules.multiShopEnabled
        ) {
            message = "ตลาดหลายร้านถูกปิดชั่วคราว"
            return
        }

        val existing = cart.find { it.product.id == product.id }
        cart = when {
            existing == null -> cart + MarketCartLine(product, 1)
            existing.quantity < product.availablePacks ->
                cart.map {
                    if (it.product.id == product.id) it.copy(quantity = it.quantity + 1) else it
                }
            else -> {
                message = "จำนวนเกินสต็อกที่พร้อมขาย"
                cart
            }
        }
        if (existing == null || existing.quantity < product.availablePacks) {
            message = "เพิ่มลงตะกร้าตลาดสดแล้ว"
        }
    }

    if (showCart) {
        MarketCartView(
            cart = cart,
            marketName = cart.firstOrNull()?.product?.marketName ?: market?.name ?: "ตลาดสด",
            activeTrip = activeTrip,
            rules = rules,
            location = location,
            address = address,
            note = note,
            busy = busy,
            message = message,
            requireLogin = auth == null,
            shops = shops,
            onBack = { showCart = false },
            onChangeQuantity = { productId, delta ->
                cart = cart.mapNotNull { line ->
                    if (line.product.id != productId) line
                    else {
                        val next = line.quantity + delta
                        when {
                            next <= 0 -> null
                            next <= line.product.availablePacks.coerceAtMost(99) -> line.copy(quantity = next)
                            else -> line
                        }
                    }
                }
                if (cart.isEmpty()) showCart = false
            },
            onNote = { note = it.take(500) },
            onGps = onGps,
            onAddress = onAddress,
            onChooseAddress = onChooseAddress,
            onSubmit = {
                val loc = location?.copy(address = address.trim())
                when {
                    loc == null || address.isBlank() ->
                        message = "กรุณาเลือกที่อยู่จัดส่งก่อน"
                    activeTrip?.locked == true ->
                        message = "Rider ออกจากตลาดแล้ว ไม่สามารถเพิ่มร้านได้"
                    activeTrip != null &&
                        activeTrip?.marketId != cart.firstOrNull()?.product?.marketId ->
                        message = "มี Market Trip อีกตลาดที่ยังไม่จบ"
                    auth == null -> onRequireLogin()
                    else -> {
                        busy = true
                        scope.launch {
                            runCatching { api.place(auth, cart, loc, note) }
                                .onSuccess {
                                    cart = emptyList()
                                    showCart = false
                                    message = if (activeTrip == null) {
                                        "สร้าง Market Trip สำเร็จ"
                                    } else {
                                        "เพิ่มร้านเข้า Market Trip แล้ว"
                                    }
                                    onDone()
                                }
                                .onFailure {
                                    message = it.message ?: "ยืนยันตลาดสดไม่สำเร็จ"
                                }
                            busy = false
                        }
                    }
                }
            }
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = if (cartCount > 0) 88.dp else 30.dp)
        ) {
            Row(
                Modifier.padding(top = 7.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    QgIcon("back", Modifier.size(20.dp), QgInk)
                }
                Spacer(Modifier.width(8.dp))
                Text("ตลาดสด", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            CustomerServiceBanner(
                banner = effectiveMarketBanner,
                fallbackDrawable = null,
                fallbackTitle = "ตลาดสด",
                fallbackSubtitle = "ตลาดสดใกล้คุณ สดใหม่ทุกวัน",
                onLink = onBannerLink
            )

            if (activeTrip != null) {
                Spacer(Modifier.height(12.dp))
                QgCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("Market Trip ที่กำลังใช้งาน", fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            (if (activeTrip?.locked == true) {
                                "Rider ออกจากตลาดแล้ว ระบบล็อกการเพิ่มร้าน"
                            } else {
                                "ยังเพิ่มร้านใหม่ได้ตราบใดที่ Rider ยังไม่ออกจากตลาด"
                            }) + " · " + (activeTrip?.status ?: ""),
                            color = QgMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("เลือกตลาด", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp))
                    Box(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .border(1.dp, QgLine, RoundedCornerShape(16.dp))
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .clickable(enabled = markets.isNotEmpty()) {
                                    marketMenuExpanded = true
                                }
                                .padding(horizontal = 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                market?.let { marketOptionLabel(it, location) }
                                    ?: "ยังไม่มีตลาดที่เปิดใช้งาน",
                                modifier = Modifier.weight(1f),
                                color = if (market == null) QgMuted else QgInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("⌄", color = QgMuted, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(
                            expanded = marketMenuExpanded,
                            onDismissRequest = { marketMenuExpanded = false },
                            modifier = Modifier.widthIn(min = 280.dp)
                        ) {
                            markets.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(marketOptionLabel(item, location)) },
                                    onClick = {
                                        selectedMarket = item.id
                                        selectedCategory = "all"
                                        shopFocusId = null
                                        marketMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    val distance = marketDistanceKm(location, market)
                    if (distance != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "เลือกตลาดใกล้ตำแหน่งคุณก่อน · " +
                                String.format(java.util.Locale("th", "TH"), "%.1f กม.", distance),
                            color = QgMuted,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }

            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(message.orEmpty(), color = QgRed, fontSize = 11.sp)
            }

            Spacer(Modifier.height(16.dp))
            Text("เลือกประเภทสินค้าในตลาด", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "เนื้อสัตว์ ปลา ผักสด ผลไม้ และของตลาดประเภทอื่น ๆ",
                color = QgMuted,
                fontSize = 10.5.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("all").plus(MARKET_CATEGORY_ORDER).forEach { key ->
                    val active = selectedCategory == key
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (active) QgRed else Color.White)
                            .border(
                                1.dp,
                                if (active) QgRed else Color(0xFFDDDDDD),
                                RoundedCornerShape(99.dp)
                            )
                            .clickable {
                                selectedCategory = key
                                shopFocusId = null
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            if (key == "all") "ทั้งหมด" else MARKET_CATEGORY_LABELS[key].orEmpty(),
                            color = if (active) Color.White else Color(0xFF333333),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ร้านค้าในตลาด", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(marketShops.size.toString() + " ร้าน", color = QgMuted, fontSize = 10.5.sp)
            }
            Spacer(Modifier.height(8.dp))

            when {
                loading -> CircularProgressIndicator()
                marketShops.isEmpty() -> Text(
                    "ไม่พบร้านที่ตรงกับคำค้นหา",
                    color = QgMuted,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                )
                else -> marketShops.forEach { shop ->
                    val count = marketCatalog.count {
                        it.shopId == shop.id && it.availablePacks > 0
                    }
                    MarketShopRow(
                        shop = shop,
                        count = count,
                        onShowProducts = {
                            selectedCategory = "all"
                            shopFocusId = shop.id
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("สินค้าจากร้านในตลาด", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(visibleProducts.size.toString() + " รายการ", color = QgMuted, fontSize = 10.5.sp)
            }
            Spacer(Modifier.height(4.dp))

            if (loading) {
                CircularProgressIndicator()
            } else if (visibleProducts.isEmpty()) {
                Text(
                    "ยังไม่มีสินค้าพร้อมขายในเงื่อนไขนี้",
                    color = QgMuted,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                )
            } else {
                visibleProducts.forEach { product ->
                    MarketProductRow(product = product, onAdd = { addProduct(product) })
                    HorizontalDivider(color = QgLine)
                }
            }
        }

        if (cartCount > 0) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(QgRed)
                    .clickable { showCart = true }
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ตะกร้าตลาดสด · " + cartCount + " รายการ",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "฿" + "%.0f".format(cartTotal),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun MarketShopRow(
    shop: MarketShop,
    count: Int,
    onShowProducts: () -> Unit
) {
    val status = when {
        shop.deliveryEnabled && count > 0 -> "เปิดรับออเดอร์ · " + count + " รายการ"
        shop.deliveryEnabled -> "เปิดร้านแล้ว · ยังไม่มีสินค้าพร้อมขาย"
        else -> "ร้านอยู่ในตลาด · ยังไม่เปิดรับ Delivery"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, QgLine, RoundedCornerShape(16.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QgRemoteImage(
            source = shop.logo ?: shop.cover,
            modifier = Modifier.size(72.dp),
            fallback = shop.name,
            cornerRadius = 13.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(shop.name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(status, color = QgMuted, fontSize = 10.5.sp)
            if (!shop.description.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    shop.description.orEmpty(),
                    color = QgMuted,
                    fontSize = 9.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (count > 0) {
            Text(
                "ดูสินค้า",
                color = QgRed,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable(onClick = onShowProducts)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun MarketProductRow(
    product: MarketProduct,
    onAdd: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QgRemoteImage(
            source = product.image,
            modifier = Modifier.size(width = 72.dp, height = 66.dp),
            fallback = product.name,
            cornerRadius = 13.dp
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(product.name, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(3.dp))
            Text(
                product.shopName + " · " +
                    MARKET_CATEGORY_LABELS[marketCategoryKey(product.category, product.name)].orEmpty(),
                color = QgMuted,
                fontSize = 10.sp,
                maxLines = 2
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "฿" + "%.0f".format(product.price) +
                    (product.unit?.let { " / " + it } ?: ""),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "พร้อมขาย " + product.availablePacks + " หน่วย",
                color = QgMuted,
                fontSize = 9.5.sp
            )
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(QgRed)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            QgIcon("plus", Modifier.size(19.dp), Color.White)
        }
    }
}

@Composable
private fun MarketCartView(
    cart: List<MarketCartLine>,
    marketName: String,
    activeTrip: ActiveMarketTrip?,
    rules: MarketRules,
    location: CustomerLocation?,
    address: String,
    note: String,
    busy: Boolean,
    message: String?,
    requireLogin: Boolean,
    shops: List<MarketShop>,
    onBack: () -> Unit,
    onChangeQuantity: (String, Int) -> Unit,
    onNote: (String) -> Unit,
    onGps: () -> Unit,
    onAddress: (String) -> Unit,
    onChooseAddress: () -> Unit,
    onSubmit: () -> Unit
) {
    val groups = cart.groupBy { it.product.shopId }
    val subtotal = cart.sumOf { it.product.price * it.quantity }
    val marketId = cart.firstOrNull()?.product?.marketId
    val addMode = activeTrip != null &&
        activeTrip.marketId == marketId &&
        !activeTrip.locked
    val combinedShops = buildSet {
        addAll(activeTrip?.shopIds.orEmpty())
        addAll(cart.map { it.product.shopId })
    }
    val serviceFee = if (addMode) null else marketMultiShopFee(combinedShops.size, rules)
    val firstShop = shops.find { it.id == cart.firstOrNull()?.product?.shopId }
    val distance = marketShopDistanceKm(location, firstShop)
    val deliveryEstimate = distance?.let { marketDeliveryEstimate(it, rules) }
    val blocked = activeTrip != null &&
        (activeTrip.locked || activeTrip.marketId != marketId)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                QgIcon("back", Modifier.size(20.dp), QgInk)
            }
            Spacer(Modifier.width(8.dp))
            Text("ตะกร้าตลาดสด", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))

        QgCard(Modifier.fillMaxWidth()) {
            Column {
                Text(marketName, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(5.dp))
                Text(
                    if (addMode) "เพิ่มร้านใหม่เข้า Market Trip เดิม"
                    else "1 Market Trip สำหรับลูกค้าคนเดียว · ทุกร้านต้องอยู่ตลาดเดียวกัน",
                    color = QgMuted,
                    fontSize = 10.5.sp
                )
            }
        }

        groups.forEach { (_, items) ->
            Spacer(Modifier.height(10.dp))
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(items.firstOrNull()?.product?.shopName ?: "ร้านค้า", fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    items.forEach { line ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            QgRemoteImage(
                                source = line.product.image,
                                modifier = Modifier.size(width = 62.dp, height = 56.dp),
                                fallback = line.product.name,
                                cornerRadius = 12.dp
                            )
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(line.product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "฿" + "%.0f".format(line.product.price) +
                                        (line.product.unit?.let { " / " + it } ?: ""),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                                Row(
                                    Modifier.padding(top = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MarketQtyButton("−") { onChangeQuantity(line.product.id, -1) }
                                    Text(
                                        line.quantity.toString(),
                                        Modifier.padding(horizontal = 9.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                    MarketQtyButton("+") { onChangeQuantity(line.product.id, 1) }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, QgLine, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            MarketSummary("ค่าสินค้า", "฿" + "%.0f".format(subtotal))
            if (addMode) {
                MarketSummary("ค่ารับหลายร้าน", "คำนวณจากราคาที่ล็อกไว้ใน Trip เดิม")
            } else {
                MarketSummary("ค่ารับหลายร้าน", "฿" + "%.0f".format(serviceFee ?: 0.0))
                MarketSummary(
                    "ค่ารอบ/จัดส่งโดยประมาณ",
                    deliveryEstimate?.let { "฿" + "%.0f".format(it) } ?: "เลือกที่อยู่จัดส่ง"
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("หมายเหตุ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = note,
            onValueChange = onNote,
            placeholder = { Text("หมายเหตุถึงร้าน") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 4
        )

        Spacer(Modifier.height(10.dp))
        if (location != null) {
            QgCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("ส่งไปที่", fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        address.ifBlank { location.address.ifBlank { "ตำแหน่งที่เลือก" } },
                        color = QgMuted,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "เปลี่ยนที่อยู่",
                        color = QgRed,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onChooseAddress)
                    )
                }
            }
        } else {
            Button(
                onClick = onChooseAddress,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(15.dp)
            ) {
                Text("เลือกที่อยู่จัดส่ง", fontWeight = FontWeight.ExtraBold)
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onGps, modifier = Modifier.fillMaxWidth()) {
            Text("ใช้ตำแหน่ง GPS ปัจจุบัน")
        }
        if (location != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = address,
                onValueChange = onAddress,
                label = { Text("ที่อยู่ / จุดสังเกต") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message, color = QgRed, fontSize = 11.sp)
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onSubmit,
            enabled = !busy && !blocked && cart.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(15.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    Modifier.size(21.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Text(
                    when {
                        requireLogin -> "เข้าสู่ระบบเพื่อสั่งซื้อ"
                        addMode -> "เพิ่มร้านเข้า Market Trip"
                        else -> "ยืนยัน Market Trip"
                    },
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        if (blocked) {
            Spacer(Modifier.height(8.dp))
            Text(
                "มี Market Trip ที่ยังไม่จบ หรือ Rider ออกจากตลาดแล้ว จึงเริ่ม/เพิ่มร้านไม่ได้",
                color = QgMuted,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun MarketQtyButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .border(1.dp, Color(0xFFDDDDDD), CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 16.sp)
    }
}

@Composable
private fun MarketSummary(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun marketCategoryKey(raw: String?, name: String?): String {
    val text = ((raw ?: "") + " " + (name ?: "")).lowercase()
    return when {
        Regex("seafood|fish|ปลา|กุ้ง|ปู|หมึก|หอย").containsMatchIn(text) -> "seafood"
        Regex("meat|pork|chicken|beef|หมู|ไก่|เป็ด|เนื้อ|เครื่องใน").containsMatchIn(text) -> "meat"
        Regex("vegetable|veg|ผัก|เห็ด|หน่อไม้").containsMatchIn(text) -> "vegetable"
        Regex("fruit|ผลไม้").containsMatchIn(text) -> "fruit"
        Regex("egg|tofu|ไข่|เต้าหู้").containsMatchIn(text) -> "egg_tofu_fresh"
        Regex("season|herb|spice|เครื่องปรุง|สมุนไพร|กระเทียม|พริก|หอม|ข่า|ตะไคร้|น้ำปลา|เครื่องแกง").containsMatchIn(text) -> "seasoning_herb"
        Regex("rice|dry|grain|ข้าว|ของแห้ง|ธัญพืช|ถั่ว|เส้น").containsMatchIn(text) -> "rice_dry"
        Regex("processed|ready.?to.?cook|ลูกชิ้น|ไส้กรอก|หมัก|แช่แข็ง|พร้อมปรุง").containsMatchIn(text) -> "processed_ready_to_cook"
        Regex("prepared|ready.?to.?eat|กับข้าว|น้ำพริก|ของทอด|พร้อมทาน").containsMatchIn(text) -> "prepared_food"
        Regex("dessert|bakery|ขนม|ของหวาน").containsMatchIn(text) -> "dessert"
        Regex("local|community|พื้นบ้าน|ชุมชน").containsMatchIn(text) -> "local"
        else -> "other_market"
    }
}

private fun sortMarketsForLocation(
    markets: List<MarketInfo>,
    location: CustomerLocation?
): List<MarketInfo> {
    if (location == null) return markets
    return markets.sortedWith(
        compareBy<MarketInfo> {
            marketDistanceKm(location, it) ?: Double.POSITIVE_INFINITY
        }.thenBy { it.name }
    )
}

private fun marketOptionLabel(
    market: MarketInfo,
    location: CustomerLocation?
): String {
    val distance = marketDistanceKm(location, market)
    return if (distance == null) market.name
    else market.name + " · " +
        String.format(java.util.Locale("th", "TH"), "%.1f กม.", distance)
}

private fun marketDistanceKm(
    location: CustomerLocation?,
    market: MarketInfo?
): Double? {
    val lat = market?.latitude ?: return null
    val lng = market.longitude ?: return null
    val from = location ?: return null
    return haversineKm(from.latitude, from.longitude, lat, lng)
}

private fun marketShopDistanceKm(
    location: CustomerLocation?,
    shop: MarketShop?
): Double? {
    val lat = shop?.latitude ?: return null
    val lng = shop.longitude ?: return null
    val from = location ?: return null
    return haversineKm(from.latitude, from.longitude, lat, lng)
}

private fun haversineKm(
    aLat: Double,
    aLng: Double,
    bLat: Double,
    bLng: Double
): Double {
    val r = 6371.0
    val dLat = Math.toRadians(bLat - aLat)
    val dLng = Math.toRadians(bLng - aLng)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) *
        sin(dLng / 2) * sin(dLng / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun marketMultiShopFee(
    shopCount: Int,
    rules: MarketRules
): Double {
    if (shopCount <= 1) return 0.0
    return rules.secondShopFee +
        (shopCount - 2).coerceAtLeast(0) * rules.additionalShopFee
}

private fun marketDeliveryEstimate(
    distanceKm: Double,
    rules: MarketRules
): Double =
    rules.baseDeliveryFee +
        ceil((distanceKm - 5.0).coerceAtLeast(0.0)) * rules.distanceStepFee
