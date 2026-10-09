package com.queuego.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgIcon
import com.queuego.shared.QgLine
import com.queuego.shared.QgMuted

internal data class ShoppingScreenSpec(
    val title: String,
    val bannerKey: String,
    val subtitle: String
)

internal fun shoppingScreenSpec(mode: String): ShoppingScreenSpec = when (mode) {
    "mobile_accessories" -> ShoppingScreenSpec(
        "มือถือและอุปกรณ์",
        "mobile_accessories",
        "มือถือ เคส ฟิล์ม สายชาร์จ และอุปกรณ์ใกล้คุณ"
    )
    "computer_it" -> ShoppingScreenSpec(
        "คอมพิวเตอร์และไอที",
        "computer_it",
        "อุปกรณ์ไอทีจากร้านใกล้คุณ"
    )
    "fashion_accessories" -> ShoppingScreenSpec(
        "เสื้อผ้าและเครื่องประดับ",
        "fashion_accessories",
        "แฟชั่น เสื้อผ้า กระเป๋า รองเท้า และเครื่องประดับใกล้คุณ"
    )
    "toys" -> ShoppingScreenSpec("ของเล่น", "toys", "ของเล่นและสินค้าเด็กจากร้านใกล้คุณ")
    "home_decor" -> ShoppingScreenSpec(
        "ตกแต่งบ้าน",
        "home_decor",
        "ของแต่งบ้าน ของใช้ และไอเดียแต่งบ้านใกล้คุณ"
    )
    "automotive_car" -> ShoppingScreenSpec(
        "อะไหล่รถยนต์",
        "automotive",
        "อะไหล่รถยนต์และมอเตอร์ไซค์จากร้านใกล้คุณ"
    )
    "automotive_motorcycle" -> ShoppingScreenSpec(
        "อะไหล่มอเตอร์ไซค์",
        "automotive",
        "อะไหล่รถยนต์และมอเตอร์ไซค์จากร้านใกล้คุณ"
    )
    "automotive" -> ShoppingScreenSpec(
        "อะไหล่ยานยนต์",
        "automotive",
        "อะไหล่รถยนต์และมอเตอร์ไซค์จากร้านใกล้คุณ"
    )
    else -> ShoppingScreenSpec("ช้อปปิ้ง", "shopping", "สินค้าจากร้านใกล้คุณ ส่งได้ทันที")
}

internal fun shoppingShopMatches(shop: CustomerShop, mode: String): Boolean {
    if (shop.category.lowercase() != "shopping") return false
    return when (mode) {
        "mobile_accessories", "computer_it", "fashion_accessories", "toys", "home_decor",
        "automotive_car", "automotive_motorcycle" -> mode in shop.subcategories
        "automotive" -> "automotive_car" in shop.subcategories || "automotive_motorcycle" in shop.subcategories
        else -> true
    }
}

private data class ShoppingTile(val mode: String, val label: String, val icon: String)

private val shoppingTiles = listOf(
    ShoppingTile("mobile_accessories", "มือถือและอุปกรณ์", "phone"),
    ShoppingTile("computer_it", "คอมพิวเตอร์และไอที", "monitor"),
    ShoppingTile("automotive", "อะไหล่ยานยนต์", "gear"),
    ShoppingTile("fashion_accessories", "เสื้อผ้าและเครื่องประดับ", "shirt"),
    ShoppingTile("toys", "ของเล่น", "toy"),
    ShoppingTile("home_decor", "ตกแต่งบ้าน", "decor")
)

@Composable
internal fun CustomerShoppingScreen(
    loading: Boolean,
    mode: String,
    serviceBanners: Map<String, ServiceBanner>,
    shops: List<CustomerShop>,
    location: CustomerLocation?,
    onBack: () -> Unit,
    onMode: (String) -> Unit,
    onShop: (CustomerShop) -> Unit,
    onBannerLink: (String) -> Unit
) {
    val selected = mode.ifBlank { "all" }
    val spec = shoppingScreenSpec(selected)
    val rows = shops
        .filter { shoppingShopMatches(it, selected) }
        .let { values ->
            if (location == null) values
            else values.sortedBy { customerDistanceKm(location, it) }
        }
    val compact = LocalConfiguration.current.screenWidthDp <= 380

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
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, QgLine, RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                QgIcon("back", Modifier.size(20.dp), Color(0xFF17191D))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                spec.title,
                fontSize = 20.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }

        CustomerServiceBanner(
            banner = serviceBanners[spec.bannerKey],
            fallbackDrawable = null,
            fallbackTitle = spec.title,
            fallbackSubtitle = spec.subtitle,
            onLink = onBannerLink
        )

        if (selected == "all") {
            Spacer(Modifier.height(12.dp))
            val gap = if (compact) 7.dp else 10.dp
            shoppingTiles.chunked(3).forEachIndexed { rowIndex, tiles ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    tiles.forEach { tile ->
                        Column(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 94.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(1.dp, QgLine, RoundedCornerShape(16.dp))
                                .clickable { onMode(tile.mode) }
                                .padding(
                                    horizontal = if (compact) 5.dp else 8.dp,
                                    vertical = if (compact) 11.dp else 14.dp
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFFFF1F3)),
                                contentAlignment = Alignment.Center
                            ) {
                                QgIcon(tile.icon, Modifier.size(26.dp), Color(0xFFE7092B))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                tile.label,
                                color = Color(0xFF25282D),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (compact) 10.sp else 11.sp,
                                lineHeight = 14.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                }
                if (rowIndex < 1) Spacer(Modifier.height(gap))
            }
            Spacer(Modifier.height(4.dp))
        }

        if (selected.startsWith("automotive")) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 2.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShoppingFilterChip("ทั้งหมด", selected == "automotive") { onMode("automotive") }
                ShoppingFilterChip("รถยนต์", selected == "automotive_car", "car") {
                    onMode("automotive_car")
                }
                ShoppingFilterChip(
                    "มอเตอร์ไซค์",
                    selected == "automotive_motorcycle",
                    "motorcycle"
                ) { onMode("automotive_motorcycle") }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "ร้านใกล้คุณ",
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "เลือกสินค้า ราคา และสต๊อกจากร้านในพื้นที่",
                    color = QgMuted,
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp
                )
            }
            Text("${rows.size} ร้าน", color = QgMuted, fontSize = 10.5.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.height(8.dp))

        when {
            loading -> Text(
                "กำลังโหลดข้อมูล…",
                color = QgMuted,
                fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 35.dp),
                textAlign = TextAlign.Center
            )
            rows.isEmpty() -> Text(
                "ยังไม่มีร้านในหมวดนี้ที่เปิดให้บริการ",
                color = QgMuted,
                fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 35.dp),
                textAlign = TextAlign.Center
            )
            else -> rows.forEach { shop ->
                WebCategoryShopCard(shop, "ช้อปปิ้ง") { onShop(shop) }
                Spacer(Modifier.height(7.dp))
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ShoppingFilterChip(
    label: String,
    active: Boolean,
    icon: String? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (active) Color(0xFF111111) else Color.White)
            .border(1.dp, if (active) Color(0xFF111111) else Color(0xFFE1E4E8), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            QgIcon(icon, Modifier.size(16.dp), if (active) Color.White else Color(0xFF25282D))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            label,
            color = if (active) Color.White else Color(0xFF25282D),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            lineHeight = 14.sp
        )
    }
}
