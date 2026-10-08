package com.queuetech.queuego.customer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import com.queuetech.queuego.core.model.QueueGoCustomerCart
import com.queuetech.queuego.core.model.QueueGoProduct
import com.queuetech.queuego.core.model.QueueGoShop
import com.queuetech.queuego.core.model.QueueGoUser
import com.queuetech.queuego.core.network.QueueGoApi
import java.text.DecimalFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class NativeCategory(
    val key: String,
    val label: String,
    val productFlow: Boolean = true,
)

private val nativeCategories = listOf(
    NativeCategory("food", "อาหาร"),
    NativeCategory("cafe", "เครื่องดื่ม"),
    NativeCategory("grocery", "ร้านขายของชำ"),
    NativeCategory("shopping", "ช้อปปิ้ง"),
    NativeCategory("laundry", "ฝากซัก", productFlow = false),
    NativeCategory("market", "ตลาดสด", productFlow = false),
)

private sealed interface CustomerScreen {
    data object Home : CustomerScreen
    data class Shops(val category: NativeCategory) : CustomerScreen
    data class Shop(val shop: QueueGoShop) : CustomerScreen
    data object Cart : CustomerScreen
}

@Composable
fun CustomerNativeApp(
    user: QueueGoUser,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val api = remember { QueueGoApi() }
    val cartStore = remember(user.id) { CustomerCartStore(context, user.id) }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf<CustomerScreen>(CustomerScreen.Home) }
    var shops by remember { mutableStateOf<List<QueueGoShop>>(emptyList()) }
    var products by remember { mutableStateOf<List<QueueGoProduct>>(emptyList()) }
    var cart by remember { mutableStateOf(cartStore.read()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refreshShops() {
        loading = true
        error = null
        runCatching { withContext(Dispatchers.IO) { api.loadActiveShops() } }
            .onSuccess { shops = it }
            .onFailure { error = it.message ?: "โหลดร้านค้าไม่สำเร็จ" }
        loading = false
    }

    fun openShop(shop: QueueGoShop) {
        screen = CustomerScreen.Shop(shop)
        loading = true
        error = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { api.loadProductsForShop(shop.id) } }
                .onSuccess { products = it }
                .onFailure {
                    products = emptyList()
                    error = it.message ?: "โหลดสินค้าไม่สำเร็จ"
                }
            loading = false
        }
    }

    fun back() {
        screen = when (val current = screen) {
            CustomerScreen.Home -> CustomerScreen.Home
            is CustomerScreen.Shops -> CustomerScreen.Home
            is CustomerScreen.Shop -> nativeCategories
                .firstOrNull { it.key == current.shop.category }
                ?.let(CustomerScreen::Shops)
                ?: CustomerScreen.Home
            CustomerScreen.Cart -> CustomerScreen.Home
        }
        error = null
    }

    LaunchedEffect(user.id) { refreshShops() }
    BackHandler(enabled = screen != CustomerScreen.Home) { back() }

    Scaffold(
        topBar = {
            NativeCustomerTopBar(
                title = when (val current = screen) {
                    CustomerScreen.Home -> "QueueGo"
                    is CustomerScreen.Shops -> current.category.label
                    is CustomerScreen.Shop -> current.shop.name
                    CustomerScreen.Cart -> "ตะกร้าสินค้า"
                },
                canBack = screen != CustomerScreen.Home,
                cartCount = cart.count,
                onBack = ::back,
                onCart = { screen = CustomerScreen.Cart },
                onLogout = onLogout,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            error?.let {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            when (val current = screen) {
                CustomerScreen.Home -> CustomerHome(
                    user = user,
                    shops = shops,
                    loading = loading,
                    onCategory = { category ->
                        if (category.productFlow) {
                            screen = CustomerScreen.Shops(category)
                            error = null
                        } else {
                            error = "หมวด${category.label}กำลังเชื่อม Flow Native เฉพาะบริการ ยังไม่ใช้ Product Flow ทั่วไป"
                        }
                    },
                    onShop = ::openShop,
                    onRefresh = { scope.launch { refreshShops() } },
                )
                is CustomerScreen.Shops -> CustomerShopList(
                    category = current.category,
                    shops = shops.filter {
                        it.category == current.category.key && it.deliveryEnabled
                    },
                    loading = loading,
                    onShop = ::openShop,
                )
                is CustomerScreen.Shop -> CustomerProductList(
                    shop = current.shop,
                    products = products,
                    loading = loading,
                    cart = cart,
                    onAdd = { product ->
                        runCatching { cartStore.add(cart, product) }
                            .onSuccess { cart = it; error = null }
                            .onFailure { error = it.message ?: "เพิ่มสินค้าไม่สำเร็จ" }
                    },
                )
                CustomerScreen.Cart -> CustomerCartScreen(
                    cart = cart,
                    onChange = { productId, delta ->
                        cart = cartStore.changeQuantity(cart, productId, delta)
                    },
                    onClear = { cart = cartStore.clear() },
                )
            }
        }
    }
}

@Composable
private fun NativeCustomerTopBar(
    title: String,
    canBack: Boolean,
    cartCount: Int,
    onBack: () -> Unit,
    onCart: () -> Unit,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canBack) {
            TextButton(onClick = onBack) { Text("กลับ") }
        } else {
            Text(
                text = "Q",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Black, fontSize = 20.sp)
        TextButton(onClick = onCart) {
            Text(if (cartCount > 0) "ตะกร้า $cartCount" else "ตะกร้า")
        }
        if (!canBack) {
            TextButton(onClick = onLogout) { Text("ออก") }
        }
    }
}

@Composable
private fun CustomerHome(
    user: QueueGoUser,
    shops: List<QueueGoShop>,
    loading: Boolean,
    onCategory: (NativeCategory) -> Unit,
    onShop: (QueueGoShop) -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "สวัสดี ${user.name.ifBlank { "ลูกค้า QueueGo" }}",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
            Text("บริการใกล้คุณจากข้อมูล Production", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                nativeCategories.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { category ->
                            FilterChip(
                                selected = false,
                                onClick = { onCategory(category) },
                                label = { Text(category.label) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ร้านค้า", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                TextButton(onClick = onRefresh) { Text("รีเฟรช") }
            }
        }
        if (loading) {
            item { CircularProgressIndicator() }
        } else {
            val productShops = shops.filter {
                it.deliveryEnabled && it.category in setOf("food", "cafe", "grocery", "shopping")
            }
            if (productShops.isEmpty()) {
                item { Text("ยังไม่มีร้านค้าที่เปิดให้บริการ") }
            } else {
                items(productShops, key = { it.id }) { shop ->
                    ShopCard(shop = shop, onClick = { onShop(shop) })
                }
            }
        }
    }
}

@Composable
private fun CustomerShopList(
    category: NativeCategory,
    shops: List<QueueGoShop>,
    loading: Boolean,
    onShop: (QueueGoShop) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text(category.label, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        if (loading) item { CircularProgressIndicator() }
        else if (shops.isEmpty()) item { Text("ยังไม่มีร้านในหมวดนี้ที่เปิดให้บริการ") }
        else items(shops, key = { it.id }) { shop ->
            ShopCard(shop = shop, onClick = { onShop(shop) })
        }
    }
}

@Composable
private fun ShopCard(shop: QueueGoShop, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(shop.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            if (shop.description.isNotBlank()) Text(shop.description, style = MaterialTheme.typography.bodySmall)
            if (shop.address.isNotBlank()) Text(shop.address, style = MaterialTheme.typography.bodySmall)
            Text(
                if (shop.isOpen) "เปิดอยู่" else "ปิดอยู่",
                color = if (shop.isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CustomerProductList(
    shop: QueueGoShop,
    products: List<QueueGoProduct>,
    loading: Boolean,
    cart: QueueGoCustomerCart,
    onAdd: (QueueGoProduct) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(shop.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (shop.address.isNotBlank()) Text(shop.address)
            Text(if (shop.isOpen) "ร้านเปิดอยู่" else "ร้านปิดอยู่")
        }
        if (loading) item { CircularProgressIndicator() }
        else if (products.isEmpty()) item { Text("ยังไม่มีสินค้าสำหรับเดลิเวอรี่") }
        else items(products, key = { it.id }) { product ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(product.name, fontWeight = FontWeight.Bold)
                        if (product.description.isNotBlank()) Text(product.description, style = MaterialTheme.typography.bodySmall)
                        Text("${formatBaht(product.effectivePrice)} บาท", color = MaterialTheme.colorScheme.primary)
                    }
                    Button(
                        onClick = { onAdd(product) },
                        enabled = product.available && product.deliveryAvailable && shop.isOpen,
                    ) {
                        val qty = cart.items.firstOrNull { it.productId == product.id }?.quantity
                        Text(if (qty == null) "เพิ่ม" else "เพิ่ม · $qty")
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerCartScreen(
    cart: QueueGoCustomerCart,
    onChange: (String, Int) -> Unit,
    onClear: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("ตะกร้าสินค้า", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        if (cart.items.isEmpty()) {
            item { Text("ตะกร้าว่าง") }
        } else {
            items(cart.items, key = { it.productId }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Text("${formatBaht(item.price)} บาท")
                        }
                        OutlinedButton(onClick = { onChange(item.productId, -1) }) { Text("−") }
                        Text(
                            item.quantity.toString(),
                            modifier = Modifier.padding(horizontal = 10.dp),
                            fontWeight = FontWeight.Bold,
                        )
                        OutlinedButton(onClick = { onChange(item.productId, 1) }) { Text("+") }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("รวม ${cart.count} รายการ")
                        Text("${formatBaht(cart.total)} บาท", fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Checkout Native จะเปิดหลังล็อก quote/ที่อยู่/idempotency กับ RPC Production เดิม",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onClear) { Text("ล้างตะกร้า") }
                    }
                }
            }
        }
    }
}

private fun formatBaht(value: Double): String = DecimalFormat("#,##0.##").format(value)
