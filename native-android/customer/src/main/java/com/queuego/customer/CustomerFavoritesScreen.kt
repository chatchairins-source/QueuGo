package com.queuego.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgIcon
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.launch

private data class CustomerFavoriteRow(
    val id: String,
    val shopId: String?,
    val productId: String?
)

private data class CustomerFavoriteView(
    val favoriteId: String,
    val targetShopId: String?,
    val title: String,
    val price: Double?
)

private data class FavoriteProduct(
    val id: String,
    val shopId: String?,
    val name: String,
    val price: Double
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
                val row = favoriteRows.optJSONObject(i) ?: continue
                val id = row.optString("id")
                if (id.isBlank()) continue
                add(
                    CustomerFavoriteRow(
                        id = id,
                        shopId = row.optString("shop_id")
                            .takeIf { it.isNotBlank() && it != "null" },
                        productId = row.optString("product_id")
                            .takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
        if (raw.isEmpty()) return emptyList()

        // Production Web resolves shop favorites only against the active public shop list.
        val shops = CustomerApi().loadShops(auth).associateBy { it.id }
        val productIds = raw.mapNotNull { it.productId }.distinct()
        val products = if (productIds.isEmpty()) {
            emptyMap()
        } else {
            val ids = productIds.joinToString(",")
            val rows = http.array(
                http.get(
                    "products?select=id,shop_id,name,price,delivery_price,image" +
                        "&available=eq.true&id=in.(" + ids + ")",
                    auth.session.accessToken
                )
            )
            buildMap<String, FavoriteProduct> {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val id = row.optString("id")
                    if (id.isBlank()) continue
                    put(
                        id,
                        FavoriteProduct(
                            id = id,
                            shopId = row.optString("shop_id")
                                .takeIf { it.isNotBlank() && it != "null" },
                            name = row.optString("name").ifBlank { "สินค้า" },
                            price = row.optDouble(
                                "delivery_price",
                                row.optDouble("price", 0.0)
                            )
                        )
                    )
                }
            }
        }

        // Preserve stale favorites exactly like Production: they stay visible and become disabled
        // instead of silently disappearing from the user's saved list.
        return raw.map { favorite ->
            val shop = favorite.shopId?.let(shops::get)
            val product = favorite.productId?.let(products::get)
            CustomerFavoriteView(
                favoriteId = favorite.id,
                targetShopId = shop?.id ?: product?.shopId,
                title = shop?.name
                    ?: product?.name
                    ?: "รายการนี้ยังไม่พร้อมให้บริการ",
                price = product?.price
            )
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

    LaunchedEffect(auth.user.id, auth.session.accessToken) {
        loading = true
        runCatching { api.load(auth) }
            .onSuccess {
                rows = it
                error = null
            }
            .onFailure { error = it.message ?: "โหลดรายการโปรดไม่สำเร็จ" }
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier.padding(top = 7.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                QgIcon("back", Modifier.size(20.dp), Color(0xFF17191D))
            }
            Spacer(Modifier.size(8.dp))
            Text(
                "รายการโปรด",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF17191D)
            )
        }

        when {
            loading -> Box(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = QgRed)
            }

            error != null -> Text(
                error.orEmpty(),
                color = QgRed,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            rows.isEmpty() -> Text(
                "ยังไม่มีรายการโปรด",
                color = QgMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 35.dp)
            )

            else -> rows.forEach { row ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFECEEF1), RoundedCornerShape(16.dp))
                        .padding(horizontal = 13.dp, vertical = 12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            row.title,
                            color = if (row.targetShopId == null) QgMuted else QgRed,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = row.targetShopId != null) {
                                    row.targetShopId?.let(onOpenShop)
                                }
                                .padding(vertical = 6.dp)
                        )
                        Text(
                            "♥",
                            color = QgRed,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .clickable(enabled = busyId != row.favoriteId) {
                                    busyId = row.favoriteId
                                    error = null
                                    scope.launch {
                                        runCatching { api.remove(auth, row.favoriteId) }
                                            .onSuccess {
                                                rows = rows.filterNot {
                                                    it.favoriteId == row.favoriteId
                                                }
                                            }
                                            .onFailure {
                                                error = it.message
                                                    ?: "บันทึกรายการโปรดไม่สำเร็จ"
                                            }
                                        busyId = null
                                    }
                                }
                                .padding(8.dp)
                        )
                    }
                    row.price?.let { price ->
                        Text(
                            "฿" + "%.0f".format(price),
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF17191D)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}
