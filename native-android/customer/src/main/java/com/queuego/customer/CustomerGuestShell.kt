package com.queuego.customer

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgBg
import com.queuego.shared.QgRed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Production web allows anonymous Home/category/search/shop/cart/market/map/promotion browsing.
 * Protected checkout/orders/laundry/favorites route to the same native Customer authentication.
 */
@Composable
internal fun GuestCustomerShell(onLogin: (destination: String) -> Unit) {
    val api = remember { CustomerApi() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cartStore = remember { CustomerCartStore(context, "guest") }
    val shopCatalog = remember { CustomerShopCatalog(scope) }
    val catalogState by shopCatalog.state.collectAsState()

    var screen by remember { mutableStateOf("home") }
    var returnScreen by remember { mutableStateOf("home") }
    var category by remember { mutableStateOf("all") }
    var shoppingMode by remember { mutableStateOf("all") }
    var shops by remember { mutableStateOf<List<CustomerShop>>(emptyList()) }
    var banners by remember { mutableStateOf<List<HomeBanner>>(emptyList()) }
    var serviceBanners by remember { mutableStateOf<Map<String, ServiceBanner>>(emptyMap()) }
    var selectedShop by remember { mutableStateOf<CustomerShop?>(null) }
    var cart by remember { mutableStateOf(cartStore.load()) }
    var location by remember { mutableStateOf<CustomerLocation?>(null) }
    var address by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(shopCatalog) { onDispose { shopCatalog.close() } }
    LaunchedEffect(screen) { if (screen != "shop") shopCatalog.close() }
    LaunchedEffect(cart) { cartStore.save(cart) }

    fun refresh() {
        scope.launch {
            loading = true
            try {
                shops = api.loadShopsPublic()
                banners = api.loadHomeBannersPublic()
                serviceBanners = api.loadServiceBannersPublic()
                message = null
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                message = failure.message ?: "โหลดข้อมูลไม่สำเร็จ"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            val point = lastKnownLocation(context)
            if (point != null) {
                location = CustomerLocation(point.first, point.second, address)
                message = "ใช้ตำแหน่งปัจจุบันแล้ว"
            } else {
                message = "ยังอ่านตำแหน่ง GPS ไม่ได้"
            }
        } else {
            message = "กรุณาอนุญาตตำแหน่งเพื่อเลือกพื้นที่จัดส่ง"
        }
    }

    fun gps() {
        if (hasLocation(context)) {
            val point = lastKnownLocation(context)
            if (point != null) {
                location = CustomerLocation(point.first, point.second, address)
                message = "ใช้ตำแหน่งปัจจุบันแล้ว"
            } else {
                message = "ยังอ่านตำแหน่ง GPS ไม่ได้"
            }
        } else {
            permission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun openShop(shop: CustomerShop, from: String) {
        if (shop.category.lowercase() == "laundry") {
            onLogin("home")
            return
        }
        returnScreen = from
        selectedShop = shop
        screen = "shop"
        shopCatalog.open(shop.id) { api.loadProductsPublic(shop.id) }
    }

    fun openBannerLink(raw: String) {
        val value = raw.trim()
        if (value.isBlank()) return
        if (value.startsWith("#")) {
            val route = value.removePrefix("#").trim().trimStart('/')
            when {
                route.isBlank() || route == "home" -> screen = "home"
                route == "search" -> screen = "search"
                route == "cart" -> screen = "cart"
                route == "orders" || route == "laundry" || route == "checkout" -> onLogin(
                    if (route == "checkout") "checkout" else "home"
                )
                route == "map" -> screen = "location"
                route == "market" || route == "market-cart" -> screen = "market"
                route == "promotion" -> screen = "promotion"
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
                    shops.find { it.id == id }?.let { openShop(it, "home") }
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

    BackHandler(screen != "home") {
        screen = when {
            screen == "shop" -> returnScreen
            screen == "shopping" && shoppingMode != "all" -> {
                shoppingMode = "all"
                "shopping"
            }
            else -> "home"
        }
        if (screen != "shop") selectedShop = null
    }

    Scaffold(
        containerColor = QgBg,
        bottomBar = {
            CustomerBottomNavigation(screen, cart.sumOf { it.quantity }) { target ->
                when (target) {
                    "orders" -> onLogin("home")
                    else -> screen = target
                }
            }
        }
    ) { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .background(QgBg)
        ) {
            GuestCustomerTopBar(
                onHome = { screen = "home" },
                onLogin = { onLogin("home") }
            )
            if (!message.isNullOrBlank()) {
                Text(
                    message!!,
                    color = if (message!!.contains("แล้ว")) QgRed else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            when (screen) {
                "home" -> CustomerHome(
                    loading = loading,
                    banners = banners,
                    location = location,
                    deliveryAddress = address,
                    shops = shops,
                    onLocation = { screen = "location" },
                    onSearch = { screen = "search" },
                    onCategory = { selected ->
                        when (selected) {
                            "market" -> screen = "market"
                            "laundry" -> onLogin("home")
                            "shopping" -> {
                                shoppingMode = "all"
                                screen = "shopping"
                            }
                            else -> {
                                category = selected
                                screen = "category"
                            }
                        }
                    },
                    onShop = { openShop(it, "home") },
                    onBannerLink = ::openBannerLink
                )

                "search" -> CustomerSearchScreen(
                    shops = shops,
                    location = location,
                    onBack = { screen = "home" },
                    onOpenShop = { openShop(it, "search") }
                )

                "location" -> CustomerLocationPickerScreen(
                    location = location,
                    address = address,
                    busy = false,
                    onGps = ::gps,
                    resolveAddress = { lat, lng -> api.reverseGeocode(lat, lng) },
                    onSave = {
                        location = it
                        address = it.address
                        message = "บันทึกที่อยู่ในเครื่องแล้ว"
                        screen = "home"
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
                    onShop = { openShop(it, "category") },
                    onBannerLink = ::openBannerLink
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
                    onShop = { openShop(it, "shopping") },
                    onBannerLink = ::openBannerLink
                )

                "shop" -> ShopScreen(
                    auth = null,
                    shop = selectedShop,
                    products = catalogState.products,
                    cart = cart,
                    productsLoading = catalogState.loading,
                    productsError = catalogState.error,
                    onRetryProducts = {
                        selectedShop?.let { shop ->
                            shopCatalog.open(shop.id) { api.loadProductsPublic(shop.id) }
                        }
                    },
                    onBack = { screen = returnScreen },
                    onAdd = { product ->
                        val currentShop = cart.firstOrNull()?.product?.shopId
                        if (currentShop != null && currentShop != product.shopId) {
                            message = "หนึ่งตะกร้าสั่งได้จากร้านเดียว กรุณาสั่งร้านเดิมให้เสร็จก่อน"
                        } else {
                            val existing = cart.find { it.product.id == product.id }
                            cart = if (existing == null) cart + CartLine(product, 1)
                            else cart.map {
                                if (it.product.id == product.id)
                                    it.copy(quantity = (it.quantity + 1).coerceAtMost(99))
                                else it
                            }
                            message = "เพิ่มลงตะกร้าแล้ว"
                        }
                    },
                    onCart = { screen = "cart" },
                    onRequireLogin = { onLogin("home") }
                )

                "cart" -> CustomerCartScreen(
                    cart = cart,
                    busy = false,
                    checkoutPending = false,
                    onBack = { screen = "home" },
                    onMinus = { id ->
                        cart = cart.mapNotNull {
                            if (it.product.id != id) it
                            else if (it.quantity <= 1) null else it.copy(quantity = it.quantity - 1)
                        }
                    },
                    onPlus = { id ->
                        cart = cart.map {
                            if (it.product.id == id)
                                it.copy(quantity = (it.quantity + 1).coerceAtMost(99))
                            else it
                        }
                    },
                    onRemove = { id -> cart = cart.filterNot { it.product.id == id } },
                    onCheckout = { onLogin("checkout") }
                )

                "market" -> MarketNativeScreen(
                    auth = null,
                    location = location,
                    address = address,
                    onAddress = {
                        address = it
                        location = location?.copy(address = it)
                    },
                    onGps = ::gps,
                    onBack = { screen = "home" },
                    onDone = { screen = "home" },
                    onRequireLogin = { onLogin("home") }
                )

                "promotion" -> CustomerPromotionScreen(
                    onBack = { screen = "home" },
                    onOpenShop = { shopId ->
                        shops.find { it.id == shopId }?.let { openShop(it, "promotion") }
                    }
                )

                else -> screen = "home"
            }
        }
    }
}

@Composable
private fun GuestCustomerTopBar(
    onHome: () -> Unit,
    onLogin: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Color(0xF0FFFFFF))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.clickable(onClick = onHome)) {
                Text(
                    "Queue",
                    color = Color(0xFF17191D),
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Go",
                    color = QgRed,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .background(Color(0xFFFFF0F1), RoundedCornerShape(12.dp))
                    .clickable(onClick = onLogin)
                    .padding(horizontal = 13.dp, vertical = 9.dp)
            ) {
                Text("เข้าสู่ระบบ", color = QgRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        HorizontalDivider(thickness = 1.dp, color = Color(0x0D000000))
    }
}
