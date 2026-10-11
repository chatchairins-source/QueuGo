package com.queuego.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgIcon
import com.queuego.shared.QgLine
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage

@Composable
internal fun MerchantCatalogScreen(
    shop: MerchantShop,
    products: List<MerchantProduct>,
    onOpenProduct: (MerchantProduct) -> Unit,
    onAddProduct: (String?) -> Unit,
    onBack: () -> Unit
) {
    val food = merchantIsFoodProductShop(shop)
    val market = merchantIsMarketProductShop(shop)
    val categories = remember(shop.category, shop.shoppingSubcategories) {
        merchantProductCategories(shop)
    }
    val showCategoryGrid = !food && !market
    var category by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    val cleanQuery = query.trim().lowercase()
    val rows = remember(products, category, cleanQuery) {
        products.filter { product ->
            (category.isBlank() || product.category == category) &&
                (cleanQuery.isBlank() ||
                    (product.name + " " + product.category).lowercase().contains(cleanQuery))
        }.sortedBy { it.name }
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
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.height(38.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) { Text("กลับ") }
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    food -> "จัดการเมนู"
                    market -> "จัดการสินค้า"
                    else -> "เพิ่มสินค้าจากคลังสินค้า"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(Color.White, RoundedCornerShape(14.dp))
                .border(1.dp, QgLine, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QgIcon("search", Modifier.size(19.dp), Color(0xFF555B63))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isBlank()) {
                    Text(
                        if (food) "ค้นหาเมนูในร้าน..." else "ค้นหาสินค้าในร้าน...",
                        color = Color(0xFF9A9EA5),
                        fontSize = 12.sp
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    cursorBrush = SolidColor(QgRed),
                    textStyle = TextStyle(fontSize = 12.sp, color = Color(0xFF202329)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (showCategoryGrid) {
            Spacer(Modifier.height(10.dp))
            categories.chunked(3).forEach { rowItems ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    rowItems.forEach { option ->
                        val active = category == option
                        Column(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 82.dp)
                                .background(
                                    if (active) Color(0xFFFFF5F7) else Color.White,
                                    RoundedCornerShape(14.dp)
                                )
                                .border(
                                    if (active) 2.dp else 1.dp,
                                    if (active) QgRed else QgLine,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    category = if (active) "" else option
                                }
                                .padding(horizontal = 5.dp, vertical = 13.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFFFF0F3), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                QgIcon("box", Modifier.size(20.dp), QgRed)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                option,
                                color = if (active) Color(0xFFE5092C) else Color(0xFF20242A),
                                fontSize = 10.sp,
                                lineHeight = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2
                            )
                        }
                    }
                    repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            if (category.isNotBlank()) "สินค้าในหมวด $category"
            else if (food) "เมนูในร้าน" else "สินค้าในร้าน",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(8.dp))

        if (rows.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, QgLine, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    when {
                        query.isNotBlank() && food -> "ไม่พบเมนูที่ค้นหา"
                        query.isNotBlank() -> "ไม่พบสินค้าที่ค้นหา"
                        food -> "ยังไม่มีเมนูในร้าน"
                        else -> "ยังไม่มีสินค้าในร้าน"
                    },
                    color = QgMuted,
                    fontSize = 11.sp
                )
            }
        } else {
            rows.chunked(3).forEach { rowItems ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { product ->
                        Column(
                            Modifier
                                .weight(1f)
                                .background(Color.White, RoundedCornerShape(14.dp))
                                .border(1.dp, QgLine, RoundedCornerShape(14.dp))
                                .padding(7.dp)
                        ) {
                            QgRemoteImage(
                                product.image,
                                Modifier.fillMaxWidth().aspectRatio(1f),
                                product.name,
                                cornerRadius = 10.dp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                product.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "%.0f บาท".format(product.posPrice ?: product.price),
                                color = Color(0xFF7D8289),
                                fontSize = 9.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(QgRed, RoundedCornerShape(99.dp))
                                    .clickable { onOpenProduct(product) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "ดูและแก้ไข",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onAddProduct(category.takeIf { it.isNotBlank() }) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                if (category.isNotBlank()) "เพิ่มสินค้าใหม่ในหมวด $category"
                else if (food) "เพิ่มเมนูใหม่" else "เพิ่มสินค้าใหม่เข้าร้าน",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}
