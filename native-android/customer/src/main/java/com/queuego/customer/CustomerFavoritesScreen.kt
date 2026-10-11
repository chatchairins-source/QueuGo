package com.queuego.customer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgCard
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.launch

private data class CustomerFavoriteRow(
    val id: String,
    val shopId: String?,
    val productId: String?
)

private data class CustomerFavoriteView(
    val favoriteId: String,
    val shopId: String,
    val title: String,
    val subtitle: String?,
    val image: String?,
    val price: Double? = null
)

private class CustomerFavoritesApi(
    private val http: QueueGoNativeApi = QueueGoNativeApi()
) {
    suspend fun load(auth: NativeAuth): List<CustomerFavoriteView> {
        val favoriteRows = http.array(
            http.get(
                "qg_customer_favorites?select=id,shop_id,product_id" +
                    "&user_id=eq." + http.enc(auth.user.id) +
                    "&order=created_at.desc",
                auth.session.accessToken
            )
        )
        val raw = buildList {
            for (i in 0 until favoriteRows.length()) {
                val r = favoriteRows.optJSONObject(i) ?: continue
                add(
                    CustomerFavoriteRow(
                        id = r.optString("id"),
                        shopId = r.optString("shop_id").takeIf { it.isNotBlank() && it != "null" },
                        productId = r.optString("product_id").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
        if (raw.isEmpty()) return emptyList()

        val shops = CustomerApi().loadShops(auth).associateBy { it.id }
        val productIds = raw.mapNotNull { it.productId }.distinct()
        val products = if (productIds.isEmpty()) {
            emptyMap()
        } else {
            val ids = productIds.joinToString(",")
            val rows = http.array(
                http.get(
                    "products?select=id,shop_id,name,delivery_price,price,image,available,delivery_available" +
                        "&id=in.(" + ids + ")",
                    auth.session.accessToken
                )
            )
            buildMap<String, CustomerProduct> {
                for (i in 0 until rows.length()) {
                    val r = rows.optJSONObject(i) ?: continue
                    val id = r.optString("id")
                    if (id.isBlank()) continue
                    put(
                        id,
                        CustomerProduct(
                            id = id,
                            shopId = r.optString("shop_id"),
                            name = r.optString("name").ifBlank { "สินค้า" },
                            description = null,
                            price = r.optDouble("price", 0.0),
                            deliveryPrice = r.optDouble("delivery_price", r.optDouble("price", 0.0)),
                            image = r.optString("image").takeIf { it.isNotBlank() && it != "null" },
                            available = r.optBoolean("available", true) && r.optBoolean("delivery_available", true)
                        )
                    )
                }
            }
        }

        return raw.mapNotNull { favorite ->
            favorite.shopId?.let { shopId ->
                val shop = shops[shopId] ?: return@let null
                CustomerFavoriteView(
                    favoriteId = favorite.id,
                    shopId = shop.id,
                    title = shop.name,
                    subtitle = categoryLabelForFavorite(shop.category),
                    image = shop.logo ?: shop.cover
                )
            } ?: favorite.productId?.let { productId ->
                val product = products[productId] ?: return@let null
                CustomerFavoriteView(
                    favoriteId = favorite.id,
                    shopId = product.shopId,
                    title = product.name,
                    subtitle = shops[product.shopId]?.name ?: "สินค้า",
                    image = product.image,
                    price = product.deliveryPrice
                )
            }
        }
    }

    suspend fun remove(auth: NativeAuth, favoriteId: String) {
        http.delete(
            "qg_customer_favorites?id=eq." + http.enc(favoriteId) +
                "&user_id=eq." + http.enc(auth.user.id),
            auth.session.accessToken
        )
    }
}

@Composable
internal fun CustomerFavoritesScreen(
    auth: NativeAuth,
    onBack: () -> Unit,
    onOpenShop: (String) -> Unit
) {
    val api = remember { CustomerFavoritesApi() }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<CustomerFavoriteView>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            runCatching { api.load(auth) }
                .onSuccess { rows = it; error = null }
                .onFailure { error = it.message ?: "โหลดรายการโปรดไม่สำเร็จ" }
            loading = false
        }
    }

    LaunchedEffect(auth.user.id) { refresh() }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("ย้อนกลับ") }
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text("รายการโปรด", fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))

        when {
            loading -> CircularProgressIndicator()
            error != null -> Text(error!!, color = QgRed)
            rows.isEmpty() -> QgCard(Modifier.fillMaxWidth()) {
                Text("ยังไม่มีรายการโปรด", color = QgMuted)
            }
            else -> rows.forEach { row ->
                QgCard(
                    Modifier.fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onOpenShop(row.shopId) }
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QgRemoteImage(
                                source = row.image,
                                modifier = Modifier.size(58.dp),
                                fallback = row.title,
                                cornerRadius = 13.dp
                            )
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(row.title, fontWeight = FontWeight.ExtraBold)
                                row.subtitle?.let { Text(it, color = QgMuted) }
                                row.price?.let { Text("฿" + "%.0f".format(it), fontWeight = FontWeight.Bold) }
                            }
                            OutlinedButton(
                                enabled = busyId != row.favoriteId,
                                onClick = {
                                    busyId = row.favoriteId
                                    scope.launch {
                                        runCatching { api.remove(auth, row.favoriteId) }
                                            .onSuccess { rows = rows.filterNot { it.favoriteId == row.favoriteId } }
                                            .onFailure { error = it.message ?: "นำออกจากรายการโปรดไม่สำเร็จ" }
                                        busyId = null
                                    }
                                }
                            ) { Text("นำออก") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

private fun categoryLabelForFavorite(category: String): String = when (category.lowercase()) {
    "food" -> "อาหาร"
    "cafe", "drink", "beverage" -> "เครื่องดื่ม"
    "grocery" -> "ร้านขายของชำ"
    "market", "fresh", "fresh_market" -> "ตลาดสด"
    "laundry" -> "ฝากซัก"
    "shopping" -> "ช้อปปิ้ง"
    else -> "ร้านค้า"
}
