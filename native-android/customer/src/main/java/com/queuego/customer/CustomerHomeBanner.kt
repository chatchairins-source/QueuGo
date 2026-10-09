package com.queuego.customer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.queuego.shared.QgRemoteImage

@Composable
internal fun CustomerHomeBanner(banners: List<HomeBanner>, selected: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(662f / 386f).clip(RoundedCornerShape(20.dp))) {
        // Byte-identical Production fallback, also visible behind a failed remote image.
        Image(painterResource(R.drawable.qg_home_banner), "QueueGo delivery service", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        banners.getOrNull(selected)?.let { banner ->
            QgRemoteImage(banner.image, Modifier.fillMaxSize(), cornerRadius = 20.dp, showFallback = false)
        }
        if (banners.size > 1) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            banners.indices.forEach { index ->
                Box(Modifier.size(width = if (index == selected) 20.dp else 7.dp, height = 7.dp).clip(CircleShape)
                    .background(if (index == selected) Color.White else Color.White.copy(alpha = .62f)).clickable { onSelect(index) })
            }
        }
    }
}
