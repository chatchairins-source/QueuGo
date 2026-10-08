package com.queuego.shared

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

val QgRed = Color(0xFFEF3340)
val QgRedDark = Color(0xFFD91F2D)
val QgInk = Color(0xFF17191D)
val QgMuted = Color(0xFF73777F)
val QgBg = Color(0xFFF7F7F8)
val QgLine = Color(0xFFECEEF1)
val QgGreen = Color(0xFF18B77A)

private val QueueGoColors = lightColorScheme(
    primary = QgRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFECEF),
    onPrimaryContainer = QgRedDark,
    secondary = QgInk,
    background = QgBg,
    surface = Color.White,
    onSurface = QgInk,
    outline = QgLine,
    error = Color(0xFFD92F3E)
)

@Composable
fun QueueGoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = QueueGoColors, content = content)
}

@Composable
fun QueueGoBrand(modifier: Modifier = Modifier, suffix: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Queue", color = QgInk, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        Text("Go", color = QgRed, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        if (!suffix.isNullOrBlank()) {
            Text("  $suffix", color = QgMuted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun QgSectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        if (!subtitle.isNullOrBlank()) Text(subtitle, color = QgMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun QgCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) { Box(Modifier.padding(14.dp)) { content() } }
}

@Composable
fun QgStatusPill(text: String, active: Boolean = true) {
    Box(
        Modifier.clip(CircleShape)
            .background(if (active) Color(0xFFEAF9F3) else Color(0xFFF1F1F2))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text,
            color = if (active) Color(0xFF0A9660) else QgMuted,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

data class QgNavItem(val key: String, val label: String, val glyph: String)

@Composable
fun QgBottomNav(selected: String, items: List<QgNavItem>, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = Color.White, tonalElevation = 2.dp) {
        items.forEach { item ->
            NavigationBarItem(
                selected = selected == item.key,
                onClick = { onSelect(item.key) },
                icon = { Text(item.glyph, fontWeight = FontWeight.Black) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = QgRed,
                    selectedTextColor = QgRed,
                    indicatorColor = Color(0xFFFFECEF),
                    unselectedIconColor = QgMuted,
                    unselectedTextColor = QgMuted
                )
            )
        }
    }
}

@Composable
fun QgRemoteImage(
    source: String?,
    modifier: Modifier,
    fallback: String = "Q",
    contentScale: ContentScale = ContentScale.Crop
) {
    var image by remember(source) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(source) {
        image = if (source.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val bytes = if (source.startsWith("data:image/")) {
                    Base64.decode(source.substringAfter(","), Base64.DEFAULT)
                } else {
                    val c = URL(source).openConnection() as HttpURLConnection
                    c.connectTimeout = 10000
                    c.readTimeout = 10000
                    try { c.inputStream.use { it.readBytes() } } finally { c.disconnect() }
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    if (image != null) {
        Image(image!!, null, modifier.clip(RoundedCornerShape(14.dp)), contentScale = contentScale)
    } else {
        Box(
            modifier.clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFF0F1F2))
                .border(1.dp, QgLine, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(fallback.take(1).uppercase(), color = QgMuted, fontWeight = FontWeight.Black)
        }
    }
}
