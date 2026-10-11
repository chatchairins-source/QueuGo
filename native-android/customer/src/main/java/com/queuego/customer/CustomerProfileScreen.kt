package com.queuego.customer

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgAccountDeletionSection
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

internal data class CustomerProfileDetails(
    val name: String,
    val phone: String?,
    val email: String?,
    val photo: String?
)

internal data class CustomerFavoriteRef(
    val id: String,
    val shopId: String?,
    val productId: String?
)

internal data class CustomerFavoriteProduct(
    val id: String,
    val shopId: String,
    val name: String,
    val price: Double,
    val image: String?
)

internal class CustomerProfileApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun details(auth: NativeAuth): CustomerProfileDetails {
        val rows = http.array(
            http.get(
                "users?select=name,phone,email,metadata&id=eq." + http.enc(auth.user.id) + "&limit=1",
                auth.session.accessToken
            )
        )
        val row = rows.optJSONObject(0)
        val metadata = row?.optJSONObject("metadata")
        return CustomerProfileDetails(
            name = row?.optString("name")?.takeIf { it.isNotBlank() } ?: auth.user.name,
            phone = row?.optString("phone")?.takeIf { it.isNotBlank() && it != "null" },
            email = row?.optString("email")?.takeIf { it.isNotBlank() && it != "null" },
            photo = listOf(
                metadata?.optString("profileImage"),
                metadata?.optString("profile_image"),
                metadata?.optString("avatarUrl")
            ).firstOrNull { !it.isNullOrBlank() && it != "null" }
        )
    }

    suspend fun favorites(auth: NativeAuth): List<CustomerFavoriteRef> {
        val rows = http.array(
            http.get(
                "qg_customer_favorites?select=id,shop_id,product_id&user_id=eq." +
                    http.enc(auth.user.id) + "&order=created_at.desc",
                auth.session.accessToken
            )
        )
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                add(
                    CustomerFavoriteRef(
                        id = row.optString("id"),
                        shopId = row.optString("shop_id").takeIf { it.isNotBlank() && it != "null" },
                        productId = row.optString("product_id").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
    }

    suspend fun favoriteProducts(
        auth: NativeAuth,
        productIds: List<String>
    ): Map<String, CustomerFavoriteProduct> {
        if (productIds.isEmpty()) return emptyMap()
        val filter = productIds.distinct().joinToString(",") { http.enc(it) }
        val rows = http.array(
            http.get(
                "products?select=id,shop_id,name,price,delivery_price,image&available=eq.true&id=in.(" +
                    filter + ")",
                auth.session.accessToken
            )
        )
        return buildMap {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val id = row.optString("id")
                val shopId = row.optString("shop_id")
                if (id.isBlank() || shopId.isBlank()) continue
                put(
                    id,
                    CustomerFavoriteProduct(
                        id = id,
                        shopId = shopId,
                        name = row.optString("name").ifBlank { "สินค้า" },
                        price = if (!row.isNull("delivery_price")) row.optDouble("delivery_price")
                            else row.optDouble("price"),
                        image = row.optString("image").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
    }

    suspend fun removeFavorite(auth: NativeAuth, favoriteId: String) {
        http.delete(
            "qg_customer_favorites?id=eq." + http.enc(favoriteId) +
                "&user_id=eq." + http.enc(auth.user.id),
            auth.session.accessToken
        )
    }
}

@Composable
internal fun CustomerProfileScreen(
    auth: NativeAuth,
    unreadCount: Int,
    pushBusy: Boolean,
    onLocation: () -> Unit,
    onOrders: () -> Unit,
    onNotifications: () -> Unit,
    onFavorites: () -> Unit,
    onPromotion: () -> Unit,
    onSupport: () -> Unit,
    onEnableBackgroundNotifications: () -> Unit,
    onTestNotification: () -> Unit,
    logout: () -> Unit
) {
    val api = remember { CustomerProfileApi() }
    val context = LocalContext.current
    var profile by remember(auth.user.id) {
        mutableStateOf(CustomerProfileDetails(auth.user.name, null, null, null))
    }
    var loading by remember(auth.user.id) { mutableStateOf(true) }

    LaunchedEffect(auth.user.id, auth.session.accessToken) {
        loading = true
        try {
            profile = api.details(auth)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            profile = CustomerProfileDetails(auth.user.name, null, null, null)
        } finally {
            loading = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QgRemoteImage(
                source = profile.photo,
                modifier = Modifier.size(60.dp),
                fallback = profile.name,
                cornerRadius = 20.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name.ifBlank { "ลูกค้า" },
                    color = Color(0xFF202329),
                    fontSize = 20.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                val contact = profile.phone ?: profile.email
                if (!contact.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(contact, color = Color(0xFF8B9098), fontSize = 11.sp, maxLines = 1)
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .background(Color(0xFFE9FAF2), RoundedCornerShape(99.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        "บัญชีลูกค้า",
                        color = Color(0xFF0A9660),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 6.dp))

        CustomerProfileSectionTitle("บัญชีและการใช้งาน")
        CustomerProfileCard {
            CustomerProfileRow("ที่อยู่จัดส่ง", onClick = onLocation)
            HorizontalDivider(color = Color(0xFFECEEF1))
            CustomerProfileRow("ออเดอร์ของฉัน", onClick = onOrders)
            HorizontalDivider(color = Color(0xFFECEEF1))
            CustomerProfileRow(
                title = "การแจ้งเตือน",
                trailing = if (unreadCount > 0) unreadCount.coerceAtMost(99).toString() else null,
                onClick = onNotifications
            )
            HorizontalDivider(color = Color(0xFFECEEF1))
            CustomerProfileRow("รายการโปรด", onClick = onFavorites)
            HorizontalDivider(color = Color(0xFFECEEF1))
            CustomerProfileRow("โปรโมชั่นจากร้าน", onClick = onPromotion)
            HorizontalDivider(color = Color(0xFFECEEF1))
            CustomerProfileRow("ติดต่อฝ่ายช่วยเหลือ", onClick = onSupport)
        }

        Spacer(Modifier.height(12.dp))
        CustomerProfileSectionTitle("การแจ้งเตือน")
        CustomerProfileCard {
            CustomerProfileRow(
                title = "การแจ้งเตือนเบื้องหลัง",
                subtitle = "รับสถานะออเดอร์แม้ไม่ได้เปิดหน้านี้",
                enabled = !pushBusy,
                onClick = onEnableBackgroundNotifications
            )
            HorizontalDivider(color = Color(0xFFECEEF1))
            CustomerProfileRow(
                title = "ทดสอบการแจ้งเตือน",
                subtitle = "ตรวจว่าแจ้งเตือนเข้าเมื่อแอปอยู่เบื้องหลัง",
                enabled = !pushBusy,
                onClick = onTestNotification
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 7.dp)
                .background(Color(0xFFF7F7F8), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("การชำระเงิน", color = Color(0xFF5D6269), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            Text("ชำระเงินสดเมื่อรับสินค้า", color = Color(0xFF737980), fontSize = 9.5.sp)
        }

        Spacer(Modifier.height(12.dp))
        CustomerProfileSectionTitle("ความเป็นส่วนตัวและบัญชี")
        CustomerProfileCard {
            CustomerProfileRow("นโยบายความเป็นส่วนตัว") {
                runCatching {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://chatchairins-source.github.io/QueuGo/docs/privacy.html")
                        )
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        QgAccountDeletionSection(
            accessToken = auth.session.accessToken,
            onDeleted = logout,
            pendingChatUserId = auth.user.id
        )
        Spacer(Modifier.height(8.dp))
        CustomerProfileCard {
            CustomerProfileRow("ออกจากระบบ", onClick = logout)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun CustomerProfileSectionTitle(text: String) {
    Text(
        text,
        color = Color(0xFF777D85),
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.2.sp,
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp)
    )
}

@Composable
private fun CustomerProfileCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
    ) {
        content()
    }
}

@Composable
private fun CustomerProfileRow(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) Color(0xFF202226) else Color(0xFF9EA2A8),
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = Color(0xFF8A8F96), fontSize = 9.5.sp, lineHeight = 13.sp)
            }
        }
        if (!trailing.isNullOrBlank()) {
            Box(
                Modifier
                    .background(QgRed, RoundedCornerShape(99.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(trailing, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(7.dp))
        }
        Text("›", color = Color(0xFFAFB3B9), fontSize = 22.sp)
    }
}

@Composable
internal fun CustomerFavoritesScreen(
    auth: NativeAuth,
    shops: List<CustomerShop>,
    onBack: () -> Unit,
    onOpenShop: (String) -> Unit
) {
    val api = remember { CustomerProfileApi() }
    val scope = rememberCoroutineScope()
    var favorites by remember(auth.user.id) { mutableStateOf<List<CustomerFavoriteRef>>(emptyList()) }
    var products by remember(auth.user.id) { mutableStateOf<Map<String, CustomerFavoriteProduct>>(emptyMap()) }
    var loading by remember(auth.user.id) { mutableStateOf(true) }
    var error by remember(auth.user.id) { mutableStateOf<String?>(null) }
    var removing by remember(auth.user.id) { mutableStateOf<Set<String>>(emptySet()) }
    var reload by remember(auth.user.id) { mutableStateOf(0) }

    LaunchedEffect(auth.session.accessToken, reload) {
        loading = true
        error = null
        try {
            val rows = api.favorites(auth)
            val loadedProducts = api.favoriteProducts(auth, rows.mapNotNull { it.productId })
            favorites = rows
            products = loadedProducts
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            error = "โหลดรายการโปรดไม่สำเร็จ"
        } finally {
            loading = false
        }
    }

    fun remove(row: CustomerFavoriteRef) {
        if (row.id in removing) return
        removing = removing + row.id
        scope.launch {
            try {
                api.removeFavorite(auth, row.id)
                favorites = favorites.filterNot { it.id == row.id }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                error = "บันทึกรายการโปรดไม่สำเร็จ"
            } finally {
                removing = removing - row.id
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = Color(0xFF202226), fontSize = 26.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text("รายการโปรด", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(12.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(
                "$it · แตะเพื่อลองอีกครั้ง",
                color = QgRed,
                modifier = Modifier.fillMaxWidth().clickable { reload++ }.padding(vertical = 12.dp)
            )
        }
        if (!loading && favorites.isEmpty() && error == null) {
            CustomerProfileCard {
                Text("ยังไม่มีรายการโปรด", color = QgMuted, modifier = Modifier.padding(14.dp))
            }
        }
        favorites.forEach { row ->
            val shop = row.shopId?.let { id -> shops.find { it.id == id } }
            val product = row.productId?.let(products::get)
            val targetShopId = shop?.id ?: product?.shopId
            val label = shop?.name ?: product?.name ?: "รายการนี้ยังไม่พร้อมให้บริการ"
            val image = shop?.logo ?: shop?.cover ?: product?.image
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .clickable(enabled = targetShopId != null) { targetShopId?.let(onOpenShop) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QgRemoteImage(image, Modifier.size(48.dp), label, cornerRadius = 12.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    product?.let {
                        Spacer(Modifier.height(3.dp))
                        Text("฿" + "%.0f".format(it.price), color = QgMuted, fontSize = 11.sp)
                    }
                }
                Text(
                    if (row.id in removing) "…" else "♥",
                    color = QgRed,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clickable(enabled = row.id !in removing) { remove(row) }
                        .padding(8.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
