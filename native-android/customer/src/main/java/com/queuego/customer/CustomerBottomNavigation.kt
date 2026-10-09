package com.queuego.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgRed

@Composable
internal fun CustomerBottomNavigation(screen: String, cartCount: Int, onSelect: (String) -> Unit) {
    val home = screen == "home"
    val selected = when (screen) {
        "home", "category", "shopping", "shop", "market", "laundry", "location" -> "home"
        "search", "cart", "orders" -> screen
        "checkout" -> "cart"
        else -> ""
    }
    Column(Modifier.fillMaxWidth().background(Color(0xF2FFFFFF)).windowInsetsPadding(WindowInsets.navigationBars)) {
        HorizontalDivider(thickness = 1.dp, color = Color(0xFFE9EAEC))
        Row(Modifier.fillMaxWidth().height(if (home) 58.dp else 48.dp).padding(horizontal = 8.dp, vertical = if (home) 6.dp else 4.dp)) {
            listOf(Triple("home", "หน้าหลัก", R.drawable.qg_nav_home), Triple("search", "ค้นหา", R.drawable.qg_nav_search),
                Triple("cart", "ตะกร้า", R.drawable.qg_nav_bag), Triple("orders", "ออเดอร์", R.drawable.qg_nav_orders)).forEach { (key, label, resource) ->
                val tint = if (selected == key) QgRed else Color(0xFF8A8D93)
                Column(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(key) }.padding(vertical = if (home) 3.dp else 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(if (home) 24.dp else 22.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(resource), label, Modifier.size(20.dp), tint = tint)
                        if (key == "cart" && cartCount > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-5).dp).size(17.dp).clip(CircleShape).background(QgRed), contentAlignment = Alignment.Center) {
                            Text(cartCount.toString(), fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Text(label, fontSize = if (home) 10.sp else 9.sp, lineHeight = if (home) 16.sp else 14.sp, fontWeight = FontWeight.Bold, color = tint)
                }
            }
        }
    }
}
