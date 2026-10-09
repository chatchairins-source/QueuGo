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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.queuego.shared.QgRemoteImage

@Composable
internal fun CustomerHomeBanner(
    banners: List<HomeBanner>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onOpen: (HomeBanner) -> Unit
) {
    val banner = banners.getOrNull(selected)
    val openModifier = if (banner?.link.isNullOrBlank()) Modifier else Modifier.clickable { banner?.let(onOpen) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(662f / 386f)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
                ambientColor = Color(0x1214181E),
                spotColor = Color(0x1214181E)
            )
            .clip(RoundedCornerShape(20.dp))
            .then(openModifier)
    ) {
        // Byte-identical Production fallback, also visible behind a failed remote image.
        Image(
            painterResource(R.drawable.qg_home_banner),
            banner?.alt ?: "QueueGo delivery service",
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        banner?.let {
            QgRemoteImage(it.image, Modifier.fillMaxSize(), cornerRadius = 20.dp, showFallback = false)
        }
        if (banners.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                banners.indices.forEach { index ->
                    Box(
                        Modifier
                            .size(width = if (index == selected) 20.dp else 7.dp, height = 7.dp)
                            .clip(CircleShape)
                            .background(if (index == selected) Color.White else Color.White.copy(alpha = .62f))
                            .clickable { onSelect(index) }
                    )
                }
            }
        }
    }
}

