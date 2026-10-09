package com.queuego.customer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgIcon
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRemoteImage

internal data class WebServiceCategory(val heading: String, val bannerTitle: String, val bannerSubtitle: String,
    val sectionTitle: String, val description: String, val empty: String, val drawable: Int)

internal fun webServiceCategory(category: String): WebServiceCategory? = when (category) {
    "food" -> WebServiceCategory("ร้านอาหาร", "อาหาร", "ร้านอาหารใกล้คุณ", "ร้านอาหารใกล้คุณ",
        "เลือกร้าน ดูเมนู และสั่งอาหารได้ทันที", "ยังไม่มีร้านอาหารที่เปิดให้บริการ", R.drawable.qg_food_banner)
    "cafe" -> WebServiceCategory("เครื่องดื่ม", "เครื่องดื่ม", "ร้านเครื่องดื่มใกล้คุณ", "ร้านเครื่องดื่มใกล้คุณ",
        "เลือกร้าน ดูเมนู และสั่งเครื่องดื่มได้ทันที", "ยังไม่มีร้านเครื่องดื่มที่เปิดให้บริการ", R.drawable.qg_cafe_banner)
    "grocery" -> WebServiceCategory("ร้านขายของชำ", "ร้านขายของชำ", "ซื้อของใกล้บ้าน เงินหมุนเวียนในชุมชน", "ร้านขายของชำใกล้คุณ",
        "เลือกร้านแล้วดูสินค้าและสั่งซื้อได้ทันที", "ยังไม่มีร้านขายของชำที่เปิดให้บริการ", R.drawable.qg_grocery_banner)
    else -> null
}

internal fun webBannerImage(banner: ServiceBanner?): String? = banner?.image?.trim()?.takeIf {
    it.startsWith("data:image/") || it.startsWith("https://", true) || it.startsWith("http://", true)
}

@Composable
internal fun ServiceCategoryScreen(loading: Boolean, category: String, serviceBanner: ServiceBanner?,
    shops: List<CustomerShop>, onBack: () -> Unit, onShop: (CustomerShop) -> Unit,
    onBannerLink: (String) -> Unit = {}) {
    val spec = webServiceCategory(category)
    if (spec == null) {
        ShoppingCategoryScreen(loading, category, serviceBanner, shops, onBack, onShop)
        return
    }
    val visible = shops.filter { when(category) {
        "food" -> it.category in setOf("food", "restaurant")
        "cafe" -> it.category in setOf("cafe", "drink", "beverage")
        else -> it.category in setOf("grocery", "convenience")
    } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Row(Modifier.padding(top = 7.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).clickable(onClick = onBack),
                contentAlignment = Alignment.Center) { QgIcon("back", Modifier.size(20.dp), Color(0xFF17191D)) }
            Spacer(Modifier.width(8.dp))
            Text(spec.heading, fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
        }
        CustomerServiceBanner(
            banner = serviceBanner,
            fallbackDrawable = spec.drawable,
            fallbackTitle = spec.bannerTitle,
            fallbackSubtitle = spec.bannerSubtitle,
            onLink = onBannerLink
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(spec.sectionTitle, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(spec.description, color = QgMuted, fontSize = 10.5.sp, lineHeight = 16.sp)
            }
            Text("${visible.size} ร้าน", color = QgMuted, fontSize = 10.5.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (loading || visible.isEmpty()) {
            Text(if (loading) "กำลังโหลดข้อมูล…" else spec.empty, color = QgMuted, fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 35.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        } else visible.forEach { shop -> WebCategoryShopCard(shop, spec.bannerTitle) { onShop(shop) }; Spacer(Modifier.height(7.dp)) }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
internal fun WebCategoryShopCard(shop: CustomerShop, categoryLabel: String, onClick: () -> Unit) {
    val imageSize = if (LocalConfiguration.current.screenWidthDp <= 420) 72.dp else 78.dp
    val radius = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().shadow(4.dp, radius, ambientColor = Color(0x0914181E), spotColor = Color(0x0914181E))
        .clip(radius).background(Color.White).border(1.dp, Color(0xFFECEEF1), radius)
        .clickable(onClick = onClick).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
        QgRemoteImage(shop.logo ?: shop.cover, Modifier.size(imageSize), shop.name, cornerRadius = 13.dp)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(shop.name, fontSize = 15.sp, lineHeight = 18.75.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(categoryLabel + (shop.openTime?.takeIf { it.isNotBlank() }?.let { "  $it–${shop.closeTime ?: ""}" } ?: ""),
                color = QgMuted, fontSize = 10.5.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(5.dp))
            Text(if (shop.open) "เปิดอยู่" else "ปิดอยู่", fontSize = 9.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold,
                color = if(shop.open) Color(0xFF0A9660) else Color(0xFF777777),
                modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if(shop.open) Color(0xFFEAFAF3) else Color(0xFFF1F1F2))
                    .padding(horizontal = 6.dp, vertical = 3.dp))
        }
    }
}


@Composable
internal fun CustomerServiceBanner(
    banner: ServiceBanner?,
    fallbackDrawable: Int?,
    fallbackTitle: String,
    fallbackSubtitle: String,
    onLink: (String) -> Unit = {}
) {
    if (banner?.active == false) return
    val remoteImage = webBannerImage(banner)
    val heading = banner?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle
    val subtitle = banner?.subtitle?.takeIf { it.isNotBlank() } ?: fallbackSubtitle
    val link = banner?.link?.takeIf { it.isNotBlank() }
    val shape = RoundedCornerShape(20.dp)
    val baseModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(720f / 343f)
        .shadow(8.dp, shape, ambientColor = Color(0x1214181E), spotColor = Color(0x1214181E))
        .clip(shape)
        .background(Brush.linearGradient(listOf(Color(0xFFEF0B32), Color(0xFF8F071F))))
    Box(if (link != null) baseModifier.clickable { onLink(link) } else baseModifier) {
        when {
            remoteImage != null -> QgRemoteImage(
                remoteImage,
                Modifier.fillMaxSize(),
                heading.ifBlank { "QueueGo" },
                contentScale = ContentScale.Crop,
                cornerRadius = 20.dp,
                showFallback = false
            )
            fallbackDrawable != null -> Image(
                painterResource(fallbackDrawable),
                heading.ifBlank { "QueueGo" },
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAD000000))))
                .padding(start = 18.dp, end = 18.dp, top = 30.dp, bottom = 16.dp)
        ) {
            if (heading.isNotBlank()) {
                Text(
                    heading,
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
