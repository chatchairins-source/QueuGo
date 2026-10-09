package com.queuego.shared

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
val QgRedSoft = Color(0xFFFFF0F1)
val QgRiderBg = Color(0xFFFFFAFA)

private val QueueGoColors = lightColorScheme(
    primary = QgRed,
    onPrimary = Color.White,
    primaryContainer = QgRedSoft,
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
        Text("Queue", color = QgInk, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        Text("Go", color = QgRed, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        if (!suffix.isNullOrBlank()) {
            Text("  " + suffix, color = QgMuted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun QgSectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = QgInk, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, color = QgMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun QgCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, QgLine)
    ) {
        Box(Modifier.padding(10.dp)) { content() }
    }
}

@Composable
fun QgStatusPill(text: String, active: Boolean = true) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (active) Color(0xFFEAF9F3) else Color(0xFFF1F1F2))
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            color = if (active) Color(0xFF0A9660) else Color(0xFF777777),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun QgIconButton(
    icon: String,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    onClick: () -> Unit
) {
    Box(modifier.size(40.dp)) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFFF3F4F6))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            QgIcon(icon, Modifier.size(20.dp), QgInk)
        }
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(QgRed),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (badge > 99) "99" else badge.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

data class QgNavItem(val key: String, val label: String, val glyph: String)

@Composable
fun QgBottomNav(selected: String, items: List<QgNavItem>, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Color(0xFAFFFFFF))
            .border(1.dp, Color(0xFFE9EAEC))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        items.forEach { item ->
            val active = selected == item.key
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(item.key) }
                    .padding(vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                QgIcon(item.glyph, Modifier.size(22.dp), if (active) QgRed else Color(0xFF8A8D93))
                Text(
                    item.label,
                    color = if (active) QgRed else Color(0xFF8A8D93),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun QgIcon(name: String, modifier: Modifier = Modifier, color: Color = QgRed) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = (w * 0.085f).coerceAtLeast(1.5f)
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(w * x1, h * y1), Offset(w * x2, h * y2), sw, StrokeCap.Round)

        when (name) {
            "brand_q" -> {
                drawCircle(color, w * .30f, Offset(w * .47f, h * .46f), style = stroke)
                line(.57f,.58f,.84f,.85f)
            }
            "home", "Q" -> {
                line(.16f,.48f,.5f,.18f); line(.5f,.18f,.84f,.48f)
                line(.24f,.43f,.24f,.82f); line(.76f,.43f,.76f,.82f)
                line(.24f,.82f,.76f,.82f)
            }
            "search", "⌕" -> {
                drawCircle(color, w*.28f, Offset(w*.43f,h*.42f), style=stroke)
                line(.63f,.62f,.86f,.85f)
            }
            "back" -> {
                line(.63f,.18f,.32f,.50f)
                line(.32f,.50f,.63f,.82f)
            }
            "location" -> {
                drawCircle(color, w*.28f, Offset(w*.5f,h*.5f), style=stroke)
                drawCircle(color, w*.08f, Offset(w*.5f,h*.5f), style=stroke)
                line(.5f,.08f,.5f,.19f); line(.5f,.81f,.5f,.92f)
                line(.08f,.5f,.19f,.5f); line(.81f,.5f,.92f,.5f)
            }
            "pin" -> {
                drawCircle(color, w*.29f, Offset(w*.5f,h*.41f), style=stroke)
                line(.27f,.57f,.5f,.88f); line(.73f,.57f,.5f,.88f)
                drawCircle(color, w*.10f, Offset(w*.5f,h*.41f), style=stroke)
            }
            "bag" -> {
                drawRoundRect(color, Offset(w*.2f,h*.34f), Size(w*.6f,h*.5f), CornerRadius(w*.08f), style=stroke)
                drawArc(color, 200f, 140f, false, Offset(w*.34f,h*.12f), Size(w*.32f,h*.34f), style=stroke)
            }
            "orders", "▤" -> {
                drawRoundRect(color, Offset(w*.22f,h*.15f), Size(w*.56f,h*.7f), CornerRadius(w*.06f), style=stroke)
                line(.34f,.34f,.67f,.34f); line(.34f,.5f,.67f,.5f); line(.34f,.66f,.58f,.66f)
            }
            "bell" -> {
                drawArc(color, 195f, 150f, false, Offset(w*.25f,h*.2f), Size(w*.5f,h*.52f), style=stroke)
                line(.25f,.58f,.2f,.72f); line(.2f,.72f,.8f,.72f); line(.8f,.72f,.75f,.58f)
                drawCircle(color, w*.04f, Offset(w*.5f,h*.84f))
            }
            "user", "store" -> {
                drawCircle(color, w*.17f, Offset(w*.5f,h*.32f), style=stroke)
                drawArc(color, 200f, 140f, false, Offset(w*.22f,h*.5f), Size(w*.56f,h*.42f), style=stroke)
            }
            "food", "utensils" -> {
                line(.29f,.13f,.29f,.46f); line(.17f,.13f,.17f,.33f); line(.42f,.13f,.42f,.33f)
                drawArc(color, 0f, 180f, false, Offset(w*.17f,h*.25f), Size(w*.25f,h*.17f), style=stroke)
                line(.29f,.46f,.29f,.88f)
                line(.71f,.13f,.71f,.88f)
                drawArc(color, 90f, 180f, false, Offset(w*.58f,h*.13f), Size(w*.25f,h*.30f), style=stroke)
            }
            "drink", "cafe" -> {
                line(.29f,.13f,.71f,.13f)
                line(.29f,.13f,.33f,.88f); line(.71f,.13f,.67f,.88f)
                line(.33f,.88f,.67f,.88f); line(.31f,.29f,.69f,.29f)
            }
            "grocery", "bottle" -> {
                line(.38f,.13f,.63f,.13f); line(.38f,.13f,.38f,.29f); line(.63f,.13f,.63f,.29f)
                line(.38f,.29f,.29f,.42f); line(.63f,.29f,.71f,.42f)
                line(.29f,.42f,.29f,.83f); line(.71f,.42f,.71f,.83f)
                line(.29f,.83f,.71f,.83f); line(.29f,.50f,.71f,.50f)
            }
            "market", "basket" -> {
                line(.17f,.42f,.83f,.42f); line(.17f,.42f,.25f,.83f); line(.83f,.42f,.75f,.83f)
                line(.25f,.83f,.75f,.83f); line(.29f,.42f,.42f,.17f); line(.71f,.42f,.58f,.17f)
                line(.33f,.58f,.67f,.58f); line(.38f,.71f,.62f,.71f)
            }
            "laundry", "grid" -> {
                drawRoundRect(color, Offset(w*.17f,h*.17f), Size(w*.25f,h*.25f), CornerRadius(w*.04f), style=stroke)
                drawRoundRect(color, Offset(w*.58f,h*.17f), Size(w*.25f,h*.25f), CornerRadius(w*.04f), style=stroke)
                drawRoundRect(color, Offset(w*.17f,h*.58f), Size(w*.25f,h*.25f), CornerRadius(w*.04f), style=stroke)
                drawRoundRect(color, Offset(w*.58f,h*.58f), Size(w*.25f,h*.25f), CornerRadius(w*.04f), style=stroke)
            }
            "shopping" -> {
                drawRoundRect(color, Offset(w*.17f,h*.33f), Size(w*.66f,h*.54f), CornerRadius(w*.04f), style=stroke)
                drawArc(color, 200f, 140f, false, Offset(w*.36f,h*.13f), Size(w*.28f,h*.29f), style=stroke)
            }
            "box" -> {
                drawRoundRect(color, Offset(w*.2f,h*.26f), Size(w*.6f,h*.56f), CornerRadius(w*.05f), style=stroke)
                line(.2f,.4f,.8f,.4f); line(.5f,.26f,.5f,.82f)
            }
            "phone" -> {
                drawRoundRect(color, Offset(w*.29f,h*.10f), Size(w*.42f,h*.80f), CornerRadius(w*.08f), style=stroke)
                line(.42f,.21f,.58f,.21f); line(.46f,.78f,.54f,.78f)
            }
            "monitor" -> {
                drawRoundRect(color, Offset(w*.13f,h*.17f), Size(w*.74f,h*.50f), CornerRadius(w*.07f), style=stroke)
                line(.50f,.67f,.50f,.83f); line(.33f,.83f,.67f,.83f)
            }
            "gear" -> {
                drawCircle(color, w*.14f, Offset(w*.50f,h*.50f), style=stroke)
                drawCircle(color, w*.31f, Offset(w*.50f,h*.50f), style=stroke)
                line(.50f,.07f,.50f,.19f); line(.50f,.81f,.50f,.93f)
                line(.07f,.50f,.19f,.50f); line(.81f,.50f,.93f,.50f)
                line(.20f,.20f,.29f,.29f); line(.71f,.71f,.80f,.80f)
                line(.80f,.20f,.71f,.29f); line(.29f,.71f,.20f,.80f)
            }
            "shirt" -> {
                line(.33f,.17f,.21f,.25f); line(.21f,.25f,.10f,.40f); line(.10f,.40f,.25f,.50f)
                line(.25f,.50f,.25f,.86f); line(.25f,.86f,.75f,.86f); line(.75f,.86f,.75f,.50f)
                line(.75f,.50f,.90f,.40f); line(.90f,.40f,.79f,.25f); line(.79f,.25f,.67f,.17f)
                drawArc(color, 0f, 180f, false, Offset(w*.33f,h*.11f), Size(w*.34f,h*.23f), style=stroke)
            }
            "toy" -> {
                drawCircle(color, w*.08f, Offset(w*.33f,h*.28f), style=stroke)
                drawCircle(color, w*.08f, Offset(w*.67f,h*.28f), style=stroke)
                drawCircle(color, w*.25f, Offset(w*.50f,h*.54f), style=stroke)
                drawCircle(color, w*.025f, Offset(w*.41f,h*.51f))
                drawCircle(color, w*.025f, Offset(w*.59f,h*.51f))
                drawArc(color, 20f, 140f, false, Offset(w*.40f,h*.57f), Size(w*.20f,h*.15f), style=stroke)
            }
            "decor" -> {
                drawRoundRect(color, Offset(w*.20f,h*.26f), Size(w*.60f,h*.34f), CornerRadius(w*.10f), style=stroke)
                drawRoundRect(color, Offset(w*.10f,h*.45f), Size(w*.80f,h*.30f), CornerRadius(w*.08f), style=stroke)
                line(.21f,.75f,.21f,.86f); line(.79f,.75f,.79f,.86f)
            }
            "car" -> {
                line(.17f,.63f,.17f,.38f); line(.17f,.38f,.29f,.20f); line(.29f,.20f,.71f,.20f)
                line(.71f,.20f,.83f,.38f); line(.83f,.38f,.83f,.63f); line(.13f,.46f,.87f,.46f)
                drawCircle(color, w*.04f, Offset(w*.29f,h*.63f), style=stroke)
                drawCircle(color, w*.04f, Offset(w*.71f,h*.63f), style=stroke)
                line(.21f,.63f,.21f,.79f); line(.21f,.79f,.31f,.79f)
                line(.79f,.63f,.79f,.79f); line(.79f,.79f,.69f,.79f)
            }
            "motorcycle" -> {
                drawCircle(color, w*.13f, Offset(w*.25f,h*.67f), style=stroke)
                drawCircle(color, w*.13f, Offset(w*.75f,h*.67f), style=stroke)
                line(.38f,.67f,.55f,.67f); line(.55f,.67f,.63f,.46f); line(.63f,.46f,.46f,.46f)
                line(.46f,.46f,.38f,.33f); line(.38f,.33f,.25f,.33f)
                line(.55f,.67f,.42f,.46f); line(.63f,.46f,.75f,.67f); line(.58f,.29f,.71f,.29f)
            }
            "tag" -> {
                line(.83f,.54f,.54f,.83f); line(.54f,.83f,.17f,.46f)
                line(.17f,.46f,.17f,.17f); line(.17f,.17f,.46f,.17f)
                line(.46f,.17f,.83f,.54f)
                drawCircle(color, w*.04f, Offset(w*.35f,h*.35f), style=stroke)
            }
            "chart" -> {
                line(.17f,.83f,.17f,.42f); line(.42f,.83f,.42f,.17f)
                line(.67f,.83f,.67f,.54f); line(.08f,.83f,.92f,.83f)
            }
            "clock" -> {
                drawCircle(color, w*.37f, Offset(w*.5f,h*.5f), style=stroke)
                line(.50f,.29f,.50f,.50f); line(.50f,.50f,.63f,.58f)
            }
            "close" -> {
                line(.25f,.25f,.75f,.75f); line(.75f,.25f,.25f,.75f)
            }
            "gallery" -> {
                drawRoundRect(color, Offset(w*.13f,h*.17f), Size(w*.74f,h*.66f), CornerRadius(w*.10f), style=stroke)
                drawCircle(color, w*.055f, Offset(w*.33f,h*.37f), style=stroke)
                line(.21f,.71f,.42f,.50f); line(.42f,.50f,.55f,.63f)
                line(.55f,.63f,.63f,.54f); line(.63f,.54f,.79f,.71f)
            }
            "support" -> {
                drawRoundRect(color, Offset(w*.17f,h*.21f), Size(w*.66f,h*.50f), CornerRadius(w*.04f), style=stroke)
                line(.38f,.71f,.27f,.84f); line(.27f,.84f,.27f,.71f)
                line(.33f,.42f,.67f,.42f); line(.33f,.55f,.54f,.55f)
            }
            "megaphone" -> {
                line(.13f,.46f,.71f,.21f); line(.71f,.21f,.71f,.79f)
                line(.71f,.79f,.13f,.54f); line(.13f,.54f,.13f,.46f)
                line(.29f,.60f,.29f,.83f); line(.29f,.83f,.46f,.83f)
                line(.46f,.83f,.42f,.65f); line(.83f,.38f,.83f,.63f)
            }
            "camera" -> {
                drawRoundRect(color, Offset(w*.17f,h*.29f), Size(w*.66f,h*.50f), CornerRadius(w*.04f), style=stroke)
                line(.17f,.29f,.33f,.29f); line(.33f,.29f,.42f,.21f)
                line(.42f,.21f,.58f,.21f); line(.58f,.21f,.67f,.29f)
                drawCircle(color, w*.16f, Offset(w*.50f,h*.54f), style=stroke)
            }
            "plus" -> {
                line(.50f,.21f,.50f,.79f); line(.21f,.50f,.79f,.50f)
            }
            else -> {
                drawCircle(color, w*.3f, Offset(w*.5f,h*.5f), style=stroke)
            }
        }
    }
}

@Composable
fun QgRemoteImage(
    source: String?,
    modifier: Modifier,
    fallback: String = "Q",
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: androidx.compose.ui.unit.Dp = 14.dp,
    showFallback: Boolean = true
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
        Image(image!!, null, modifier.clip(RoundedCornerShape(cornerRadius)), contentScale = contentScale)
    } else if (showFallback) {
        Box(
            modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(Color(0xFFF0F1F2))
                .border(1.dp, QgLine, RoundedCornerShape(cornerRadius)),
            contentAlignment = Alignment.Center
        ) {
            Text(fallback.take(1).uppercase(), color = Color(0xFFC3C6CB), fontWeight = FontWeight.Black)
        }
    }
    else { Box(modifier.clip(RoundedCornerShape(cornerRadius))) }
}
