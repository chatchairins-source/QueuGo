package com.queuego.rider

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import com.queuego.shared.QgIcon
import com.queuego.shared.QgRed
import com.queuego.shared.QgRedSoft
import com.queuego.shared.QgRemoteImage
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Production Web rider/index.html is the visual Golden Master for these surfaces.
// Do not add Native-only information or controls here.

private val WebInk = Color(0xFF17171B)
private val WebMuted = Color(0xFF8D8589)
private val WebMutedReady = Color(0xFF77777F)
private val WebCard = Color(0xFFFEFDFD)
private val WebLine = Color(0xFFEEE5E8)
private val WebRowLine = Color(0xFFF1EBED)
private val WebMetric = Color(0xFFFAF7F8)
private val WebSecondaryLine = Color(0xFFF3C7D1)
private val WebPrimaryButton = Color(0xFFF04455)

@Composable
internal fun RiderWebParityReadyCard(
    online: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val compact = LocalConfiguration.current.screenWidthDp <= 360
    val horizontalPadding = if (compact) 14.dp else 20.dp
    val verticalPadding = if (compact) 18.dp else 22.dp
    val gap = if (compact) 10.dp else 14.dp
    val titleSize = if (compact) 17.sp else 20.sp
    val subtitleSize = if (compact) 12.sp else 13.sp
    val thumbOffset by animateDpAsState(
        targetValue = if (online) 23.dp else 3.dp,
        animationSpec = tween(durationMillis = 180),
        label = "rider-web-ready-switch"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        if (online) Color(0xFF12A651) else Color(0xFFA2A2A7),
                        CircleShape
                    )
            )
            Column(Modifier.weight(1f)) {
                Text(
                    if (online) "พร้อมรับงานแล้ว" else "ยังไม่พร้อมรับงาน",
                    fontSize = titleSize,
                    lineHeight = if (compact) 24.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = WebInk
                )
                Text(
                    if (online) "คุณจะได้รับงานในพื้นที่ใกล้เคียง" else "เปิดเพื่อเริ่มรับงาน",
                    fontSize = subtitleSize,
                    lineHeight = 19.sp,
                    color = WebMutedReady,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 34.dp)
                    .alpha(if (enabled) 1f else 0.5f)
                    .clip(CircleShape)
                    .background(if (online) QgRed else Color(0xFFD9D5D7))
                    .clickable(enabled = enabled) { onCheckedChange(!online) }
                    .semantics { contentDescription = "พร้อมรับงาน" }
            ) {
                Card(
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.CenterStart)
                        .offset(x = thumbOffset),
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {}
            }
        }
    }
}

@Composable
internal fun RiderWebParityOfferCard(
    job: RiderJob,
    shop: RiderShopDisplay?,
    riderLocation: Pair<Double, Double>?,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onExpired: () -> Unit
) {
    val pickupKm = webParityDistanceKm(
        riderLocation?.first,
        riderLocation?.second,
        job.pickupLat,
        job.pickupLng
    )
    val deliveryKm = webParityDistanceKm(
        job.pickupLat,
        job.pickupLng,
        job.deliveryLat,
        job.deliveryLng
    )
    var secondsLeft by remember(job.id, job.offerExpiresAt) {
        mutableIntStateOf(riderOfferSecondsLeft(job.offerExpiresAt))
    }

    LaunchedEffect(job.id, job.offerExpiresAt) {
        while (true) {
            secondsLeft = riderOfferSecondsLeft(job.offerExpiresAt)
            if (secondsLeft <= 0) {
                onExpired()
                break
            }
            delay(250)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = WebCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        border = BorderStroke(1.dp, WebLine)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            job.isPassenger -> "เที่ยวโดยสาร"
                            job.isExpress -> "Express ส่งด่วน"
                            else -> "งานใหม่ใกล้คุณ"
                        },
                        color = QgRed,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                    Text(
                        job.numberLabel,
                        color = WebInk,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        letterSpacing = (-0.6).sp,
                        lineHeight = 29.sp
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(QgRedSoft)
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Text(
                        riderMoneyBaht(job.deliveryFee ?: 0.0),
                        color = QgRed,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }

            RiderWebParityOfferPlaceRow(
                logoUrl = if (job.isExpress) null else shop?.shopLogo,
                fallback = (if (job.isExpress) job.pickupAddress else shop?.shopName
                    ?: job.pickupAddress ?: "Q")?.take(1),
                icon = if (job.isExpress) "express" else null,
                label = when {
                    job.isPassenger -> "รับผู้โดยสาร"
                    job.isExpress -> "รับพัสดุ"
                    else -> "รับที่ร้าน"
                },
                value = if (job.isExpress) {
                    job.pickupAddress ?: "จุดรับพัสดุ"
                } else {
                    shop?.shopName ?: job.pickupAddress ?: "ร้านค้า"
                },
                trailing = if (job.isPassenger || job.isExpress) "จุดรับ" else "รับสินค้า",
                divider = true
            )

            RiderWebParityOfferPlaceRow(
                logoUrl = null,
                fallback = null,
                icon = "location",
                label = when {
                    job.isPassenger -> "ไปยังจุดหมาย"
                    job.isExpress -> "ส่งพัสดุ"
                    else -> "ส่งให้ลูกค้า"
                },
                value = job.deliveryAddress ?: if (job.isExpress) "จุดส่งพัสดุ" else "จุดส่งสินค้า",
                trailing = if (job.isPassenger || job.isExpress) "จุดส่ง" else "ส่งสินค้า",
                divider = false
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RiderWebParityOfferMetric(
                    label = "ระยะทาง",
                    value = (pickupKm?.let { String.format(Locale.US, "%.1f", it) } ?: "-") + " กม.",
                    modifier = Modifier.weight(1f)
                )
                val minutes = when {
                    job.isPassenger -> job.rideDurationMinutes
                    job.isExpress -> job.expressDurationMinutes
                    else -> deliveryKm?.let { ceil(it / 25.0 * 60.0).coerceAtLeast(1.0) }
                }
                RiderWebParityOfferMetric(
                    label = "เวลาประมาณ",
                    value = (minutes?.let { ceil(it).toInt().toString() } ?: "-") + " นาที",
                    modifier = Modifier.weight(1f)
                )
                RiderWebParityOfferMetric(
                    label = "รายได้",
                    value = riderMoneyBaht(job.deliveryFee),
                    modifier = Modifier.weight(1f),
                    highlight = true
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    enabled = !busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, WebSecondaryLine),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = QgRed,
                        disabledContentColor = QgRed.copy(alpha = 0.45f)
                    )
                ) {
                    Text("ปฏิเสธ", fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
                Button(
                    onClick = onAccept,
                    enabled = !busy && secondsLeft > 0,
                    modifier = Modifier
                        .weight(1.25f)
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WebPrimaryButton,
                        contentColor = Color.White,
                        disabledContainerColor = WebPrimaryButton.copy(alpha = 0.45f),
                        disabledContentColor = Color.White
                    )
                ) {
                    Text(
                        if (secondsLeft > 0) "รับงาน · $secondsLeft วิ" else "หมดเวลา",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun RiderWebParityOfferPlaceRow(
    logoUrl: String?,
    fallback: String?,
    icon: String?,
    label: String,
    value: String,
    trailing: String,
    divider: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon == null) {
                QgRemoteImage(
                    logoUrl,
                    Modifier.size(42.dp),
                    fallback = fallback ?: "Q",
                    cornerRadius = 14.dp
                )
            } else {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(QgRedSoft, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    QgIcon(icon, Modifier.size(22.dp), QgRed)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = WebMuted, fontSize = 8.sp)
                Text(
                    value,
                    color = WebInk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                trailing,
                color = Color(0xFF8A8387),
                fontSize = 9.sp,
                maxLines = 1
            )
        }
        if (divider) {
            HorizontalDivider(thickness = 1.dp, color = WebRowLine)
        }
    }
}

@Composable
private fun RiderWebParityOfferMetric(
    label: String,
    value: String,
    modifier: Modifier,
    highlight: Boolean = false
) {
    Column(
        modifier
            .background(WebMetric, RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = WebMuted, fontSize = 8.sp)
        Text(
            value,
            color = if (highlight) QgRed else WebInk,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

private fun webParityDistanceKm(
    fromLat: Double?,
    fromLng: Double?,
    toLat: Double?,
    toLng: Double?
): Double? {
    if (fromLat == null || fromLng == null || toLat == null || toLng == null) return null
    if (
        !fromLat.isFinite() || !fromLng.isFinite() || !toLat.isFinite() || !toLng.isFinite() ||
        fromLat !in -90.0..90.0 || toLat !in -90.0..90.0 ||
        fromLng !in -180.0..180.0 || toLng !in -180.0..180.0
    ) return null
    val dLat = Math.toRadians(toLat - fromLat)
    val dLng = Math.toRadians(toLng - fromLng)
    val a = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) *
        sin(dLng / 2).let { it * it }
    return 6371.0 * 2.0 * atan2(
        sqrt(a.coerceIn(0.0, 1.0)),
        sqrt((1.0 - a).coerceIn(0.0, 1.0))
    )
}
