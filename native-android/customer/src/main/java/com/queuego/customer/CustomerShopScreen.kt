package com.queuego.customer

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class ShopFavorite(val id: String, val shop: String, val product: String)
private data class ShopReview(val name: String, val rating: Int, val comment: String, val date: String)
private data class ShopReviews(val count: Int, val average: Double, val items: List<ShopReview>)

// These are the same table and RPC used by Production customer-features.js.
private class ShopExtrasApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun favorites(auth: NativeAuth): List<ShopFavorite> {
        val rows = http.array(http.get("qg_customer_favorites?select=id,shop_id,product_id&user_id=eq." +
            http.enc(auth.user.id) + "&order=created_at.desc", auth.session.accessToken))
        return (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
            ShopFavorite(it.getString("id"), it.optString("shop_id"), it.optString("product_id"))
        } }
    }
    suspend fun toggle(auth: NativeAuth, kind: String, id: String) {
        require(kind == "shop" || kind == "product")
        val owner = "user_id=eq." + http.enc(auth.user.id)
        val rows = http.array(http.get("qg_customer_favorites?select=id&$owner&${kind}_id=eq." + http.enc(id), auth.session.accessToken))
        if (rows.length() > 0) http.delete("qg_customer_favorites?id=eq." + http.enc(rows.getJSONObject(0).getString("id")) + "&$owner", auth.session.accessToken)
        else http.post("qg_customer_favorites", auth.session.accessToken, JSONObject().put("user_id", auth.user.id).put("${kind}_id", id))
    }
    suspend fun reviews(auth: NativeAuth?, shop: String): ShopReviews {
        val data = http.obj(http.rpc("qg_public_shop_reviews", auth?.session?.accessToken, JSONObject().put("p_shop_id", shop)))
        val rows = data.optJSONArray("items")
        val items = (0 until (rows?.length() ?: 0)).map { i -> rows!!.getJSONObject(i).let {
            ShopReview(it.optString("customer_name").ifBlank { "ลูกค้า" }, it.optInt("rating"), it.optString("comment"), it.optString("updated_at"))
        } }
        return ShopReviews(data.optInt("count", items.size), data.optDouble("average", 0.0), items)
    }
}

@Composable
internal fun ShopScreen(auth: NativeAuth?, shop: CustomerShop?, products: List<CustomerProduct>, cart: List<CartLine>,
    productsLoading: Boolean, productsError: String?, onRetryProducts: () -> Unit,
    onBack: () -> Unit, onAdd: (CustomerProduct) -> Unit, onCart: () -> Unit,
    onRequireLogin: () -> Unit = {}) {
    if (shop == null) { Text("ไม่พบร้านค้า", Modifier.padding(35.dp), color = QgMuted); return }
    // Guest and signed-in shop views use the same Production-derived renderer.
    key(auth?.user?.id ?: "guest", shop.id) {
        ShopBody(auth, shop, products, cart, productsLoading, productsError, onRetryProducts,
            onBack, onAdd, onCart, onRequireLogin)
    }
}

@Composable
private fun ShopBody(auth: NativeAuth?, shop: CustomerShop, products: List<CustomerProduct>, cart: List<CartLine>,
    productsLoading: Boolean, productsError: String?, onRetryProducts: () -> Unit,
    onBack: () -> Unit, onAdd: (CustomerProduct) -> Unit, onCart: () -> Unit,
    onRequireLogin: () -> Unit) {
    val api = remember { ShopExtrasApi() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val compact = configuration.screenHeightDp < 760
    val narrow = configuration.screenWidthDp <= 420
    val inset = if (narrow) 10.dp else 12.dp
    var favorites by remember { mutableStateOf<List<ShopFavorite>>(emptyList()) }
    var reviews by remember { mutableStateOf<ShopReviews?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var busyFavorites by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expanded by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload, auth?.session?.accessToken, shop.id) {
        loadError = false
        try {
            favorites = if (auth == null) emptyList() else api.favorites(auth)
            reviews = api.reviews(auth, shop.id)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { loadError = true }
    }
    fun favorite(kind: String, id: String) {
        val liveAuth = auth
        if (liveAuth == null) {
            onRequireLogin()
            return
        }
        val identity = "$kind:$id"
        if (identity in busyFavorites) return
        busyFavorites = busyFavorites + identity
        scope.launch {
            try { api.toggle(liveAuth, kind, id); favorites = api.favorites(liveAuth) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { actionError = "บันทึกรายการโปรดไม่สำเร็จ" }
            finally { busyFavorites = busyFavorites - identity }
        }
    }
    @Composable fun Favorite(kind: String, id: String) {
        val selected = favorites.any { if (kind == "shop") it.shop == id else it.product == id }
        ShopIcon(if (selected) R.drawable.qg_shop_heart_filled else R.drawable.qg_shop_heart,
            "บันทึกรายการโปรด", Modifier.size(32.dp), QgRed, "$kind:$id" !in busyFavorites) { favorite(kind, id) }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(if (compact) 164.dp else if (narrow) 178.dp else 195.dp).background(Color(0xFFDDDDDD))) {
                // The web uses public_cover only; it does not substitute the store logo.
                QgRemoteImage(shop.cover, Modifier.fillMaxSize(), shop.name, cornerRadius = 0.dp, showFallback = false)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .38f), Color.Transparent, Color.Black.copy(alpha = .1f)))))
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    ShopIcon(R.drawable.qg_shop_back, "กลับ", Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3F4F6)), Color(0xFF24272D), onClick = onBack)
                    ShopIcon(R.drawable.qg_shop_share, "แชร์ร้าน", Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3F4F6)), Color(0xFF24272D)) {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "ร้านค้าใน QueueGo")
                            putExtra(Intent.EXTRA_TEXT, "https://chatchairins-source.github.io/QueuGo/#shop/" + shop.id)
                        }
                        context.startActivity(Intent.createChooser(share, "แชร์ร้าน"))
                    }
                }
            }
            // Overlap by the web's 18px without leaving a layout gap below the cover.
            Column(Modifier.offset(y = (-18).dp).fillMaxWidth().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Color.White)
                .padding(start = if (compact) 10.dp else 12.dp, end = if (compact) 10.dp else 12.dp, top = if (compact) 11.dp else 14.dp, bottom = if (compact) 7.dp else 9.dp)) {
                Text(shop.name, fontSize = 21.sp, lineHeight = 24.15.sp, fontWeight = FontWeight.ExtraBold)
                Favorite("shop", shop.id)
                Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(shopCategory(shop.category), fontSize = 11.sp, color = QgMuted)
                    shop.address?.let { Text(it, fontSize = 11.sp, color = QgMuted) }
                }
                Text(if (shop.open) "เปิดอยู่" else "ปิดอยู่", fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
                    color = if (shop.open) Color(0xFF0A9660) else Color(0xFF777777),
                    modifier = Modifier.padding(top = 5.dp).clip(CircleShape).background(if (shop.open) Color(0xFFE7F8F0) else Color(0xFFF1F1F2)).padding(horizontal = 6.dp, vertical = 3.dp))
            }
            Column(Modifier.offset(y = (-18).dp)) {
                // Production loadMenu currently returns no category field, hence the same All control.
                Text("ทั้งหมด", color = QgRed, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = inset + 6.dp, vertical = 12.dp))
                Spacer(Modifier.height(6.dp))
                when {
                    productsLoading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = inset, vertical = 18.dp))
                    productsError != null -> Text("$productsError · ลองอีกครั้ง", Modifier.fillMaxWidth().clickable(onClick = onRetryProducts).padding(35.dp), color = QgRed)
                    products.isEmpty() -> Text("ยังไม่มีเมนู", Modifier.fillMaxWidth().padding(35.dp), color = QgMuted)
                }
                products.forEach { product ->
                    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = inset, vertical = if (compact) 7.dp else 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        QgRemoteImage(product.image, Modifier.size(if (compact) 64.dp else 72.dp, if (compact) 58.dp else 66.dp), product.name, cornerRadius = 13.dp)
                        Column(Modifier.weight(1f)) {
                            Text(product.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            if (!product.description.isNullOrBlank()) Text(product.description, fontSize = 10.sp, color = QgMuted, maxLines = 2, modifier = Modifier.padding(vertical = 3.dp))
                            Text("฿" + "%.0f".format(product.deliveryPrice), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Favorite("product", product.id)
                        if (!product.available) Text("หมด", color = QgMuted, fontSize = 11.sp)
                        else ShopIcon(R.drawable.qg_shop_plus, "เพิ่ม", Modifier.size(36.dp).clip(CircleShape).background(if (shop.open) QgRed else Color(0xFFC9CCD1)), Color.White, shop.open) { onAdd(product) }
                    }
                    HorizontalDivider(color = QgLine)
                }
                Column(Modifier.padding(horizontal = inset).padding(top = 10.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 13.dp, vertical = 12.dp)) {
                    Text("รีวิวจากลูกค้า", fontSize = 18.sp, lineHeight = 21.6.sp, fontWeight = FontWeight.Bold)
                    if (loadError) Text("โหลดข้อมูลไม่สำเร็จ · ลองอีกครั้ง", fontSize = 11.sp, color = QgRed, modifier = Modifier.padding(top = 9.dp).clickable { reload++ })
                    else if (reviews == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 9.dp))
                    reviews?.let { data ->
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("★ " + String.format(Locale.US, "%.1f", data.average), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${data.count} รีวิว", fontSize = 10.sp, color = QgMuted)
                        }
                        if (data.items.isEmpty()) Text("ยังไม่มีรีวิวจากลูกค้า", fontSize = 10.5.sp, color = QgMuted, modifier = Modifier.padding(top = 9.dp))
                        else {
                            ReviewItem(data.items.first(), Modifier.padding(top = 9.dp))
                            if (data.items.size > 1) {
                                HorizontalDivider(Modifier.padding(top = 8.dp), color = QgLine)
                                Text("ดูรีวิวทั้งหมด", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = QgRed,
                                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(top = 9.dp, bottom = if (expanded) 7.dp else 1.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                if (expanded) Column(Modifier.heightIn(max = 310.dp).verticalScroll(rememberScrollState())) { data.items.drop(1).forEach { ReviewItem(it) } }
                            }
                        }
                    }
                    actionError?.let { Text(it, fontSize = 11.sp, color = QgRed) }
                }
                Spacer(Modifier.height(if (cart.any { it.product.shopId == shop.id }) 80.dp else 30.dp))
            }
        }
        val shopCart = cart.filter { it.product.shopId == shop.id }
        if (shopCart.isNotEmpty()) Row(Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(QgRed).clickable(onClick = onCart).padding(horizontal = 13.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("ดูตะกร้า · ${shopCart.sumOf { it.quantity }} รายการ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("฿" + "%.0f".format(shopCart.sumOf { it.product.deliveryPrice * it.quantity }), fontSize = 12.sp, color = Color.White)
        }
    }
}

@Composable
private fun ShopIcon(resource: Int, description: String, modifier: Modifier, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(resource), description, Modifier.size(20.dp), tint = color)
    }
}

@Composable
private fun ReviewItem(review: ShopReview, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = Color(0xFFF0EDEF))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(review.name, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            Text("${review.rating} ★", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (review.comment.isNotBlank()) Text(review.comment, fontSize = 11.sp, lineHeight = 15.4.sp, maxLines = 2, color = Color(0xFF4F5359), modifier = Modifier.padding(top = 4.dp))
        val date = remember(review.date) { runCatching {
            OffsetDateTime.parse(review.date).atZoneSameInstant(ZoneId.of("Asia/Bangkok")).format(DateTimeFormatter.ofPattern("d/M/yyyy HH:mm:ss", Locale.forLanguageTag("th-TH")).withChronology(java.time.chrono.ThaiBuddhistChronology.INSTANCE))
        }.getOrDefault(review.date) }
        Text(date, fontSize = 8.5.sp, color = Color(0xFFA0A4AA), modifier = Modifier.padding(top = 4.dp))
    }
}

private fun shopCategory(category: String) = when (category) {
    "food" -> "อาหาร"; "cafe", "drink" -> "เครื่องดื่ม"; "grocery" -> "ร้านขายของชำ"
    "market" -> "ตลาดสด"; "laundry" -> "ร้านฝากซัก"; "shopping" -> "ช้อปปิ้ง"; else -> "อื่นๆ"
}
