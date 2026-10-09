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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgLine
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage

private val customerSearchExcludedMarketCategories =
    setOf("market", "fresh", "fresh_market", "meat", "fish", "vegetable", "fruit")

internal fun customerSearchCategory(category: String): String = when (category.lowercase()) {
    "food", "restaurant" -> "อาหาร"
    "cafe", "drink", "beverage" -> "เครื่องดื่ม"
    "grocery", "convenience" -> "ร้านขายของชำ"
    "market", "fresh", "fresh_market" -> "ตลาดสด"
    "laundry" -> "ฝากซัก"
    "shopping" -> "ช้อปปิ้ง"
    else -> "อื่นๆ"
}

internal fun customerGlobalSearchRows(
    shops: List<CustomerShop>,
    location: CustomerLocation?,
    query: String
): List<CustomerShop> {
    val sorted = shops
        .filter { it.category.lowercase() !in customerSearchExcludedMarketCategories }
        .let { values ->
            if (location == null) values
            else values.sortedBy { customerDistanceKm(location, it) }
        }
    val term = query.trim().lowercase()
    if (term.isBlank()) return sorted
    return sorted.filter { shop ->
        listOf(
            shop.name,
            customerSearchCategory(shop.category),
            shop.description.orEmpty(),
            shop.address.orEmpty()
        ).joinToString(" ").lowercase().contains(term)
    }
}

@Composable
fun CustomerSearchScreen(
    shops: List<CustomerShop>,
    location: CustomerLocation?,
    onBack: () -> Unit,
    onOpenShop: (CustomerShop) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val rows = customerGlobalSearchRows(shops, location, query)
    val compact = LocalConfiguration.current.screenWidthDp <= 420
    val horizontalPadding = if (compact) 10.dp else 12.dp

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding)
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
                Icon(
                    painter = painterResource(R.drawable.qg_shop_back),
                    contentDescription = "ย้อนกลับ",
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF17191D)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "ค้นหา",
                color = Color(0xFF17191D),
                fontSize = 20.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(14.dp),
                    clip = false,
                    ambientColor = Color(0x0914181E),
                    spotColor = Color(0x0914181E)
                )
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, QgLine, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.qg_nav_search),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color(0xFF3F434A)
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        "ค้นหาร้านจากทุกหมวด",
                        color = Color(0xFF9A9EA5),
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    cursorBrush = SolidColor(QgRed),
                    textStyle = TextStyle(
                        color = Color(0xFF17191D),
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "ร้านค้าทั้งหมด",
                    color = Color(0xFF17191D),
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "ค้นหาอาหาร เครื่องดื่ม ร้านขายของชำ และร้านค้าทั่วไปในที่เดียว",
                    color = QgMuted,
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp
                )
            }
            Text(
                "${rows.size} ร้าน",
                color = QgMuted,
                fontSize = 10.5.sp,
                lineHeight = 16.sp
            )
        }
        Spacer(Modifier.height(8.dp))

        if (rows.isEmpty()) {
            Text(
                "ไม่พบร้านที่ค้นหา",
                color = QgMuted,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 35.dp),
                fontSize = 16.sp
            )
        } else {
            rows.forEach { shop ->
                CustomerSearchShopCard(shop = shop, compact = compact) { onOpenShop(shop) }
                Spacer(Modifier.height(7.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CustomerSearchShopCard(
    shop: CustomerShop,
    compact: Boolean,
    onClick: () -> Unit
) {
    val radius = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = radius,
                clip = false,
                ambientColor = Color(0x0914181E),
                spotColor = Color(0x0914181E)
            )
            .clip(radius)
            .background(Color.White)
            .border(1.dp, QgLine, radius)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QgRemoteImage(
            source = shop.logo ?: shop.cover,
            modifier = Modifier.size(if (compact) 72.dp else 78.dp),
            fallback = shop.name,
            cornerRadius = 13.dp
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                shop.name,
                color = Color(0xFF17191D),
                fontSize = 15.sp,
                lineHeight = 18.75.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    customerSearchCategory(shop.category),
                    color = QgMuted,
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 1
                )
                if (!shop.openTime.isNullOrBlank()) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        shop.openTime + "–" + (shop.closeTime ?: ""),
                        color = QgMuted,
                        fontSize = 10.5.sp,
                        lineHeight = 16.sp,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (shop.open) Color(0xFFEAF9F3) else Color(0xFFF1F1F2))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    if (shop.open) "เปิดอยู่" else "ปิดอยู่",
                    color = if (shop.open) Color(0xFF0A9660) else Color(0xFF777777),
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
