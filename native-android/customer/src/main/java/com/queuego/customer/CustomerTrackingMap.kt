package com.queuego.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.queuego.shared.QgLongdoTrackingMap
import com.queuego.shared.QgMapPoint
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun CustomerTrackingMap(order: CustomerOrder, rider: CustomerTrackingRider?) {
    if (order.trackingClosed()) return
    val searching = order.status == "searching_rider"
    val points = customerTrackingPoints(order, rider)
    val hasRiderLocation = rider?.latitude?.let { lat -> rider.longitude?.let { lon -> QgMapPoint(lat, lon, 0).valid } } == true
    var ready by remember(order.id) { mutableStateOf(false) }
    var timedOut by remember(order.id) { mutableStateOf(false) }
    var attempt by remember(order.id) { mutableStateOf(0) }
    LaunchedEffect(order.id, attempt, points.isEmpty(), ready) {
        if (!ready && points.isNotEmpty()) {
            delay(15_000)
            if (!ready) timedOut = true
        }
    }
    val config = LocalConfiguration.current
    val mapHeight = when {
        config.screenHeightDp < 760 -> 145.dp
        config.screenWidthDp <= 420 -> 160.dp
        else -> 178.dp
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFECE7E9), RoundedCornerShape(18.dp))
            .padding(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (searching) "พื้นที่ค้นหา Rider" else "ติดตามการจัดส่ง",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (searching) QgRed else Color(0xFF20B46D))
                )
                Text(
                    if (searching) "ค้นหาอัตโนมัติ" else "อัปเดตตำแหน่ง",
                    color = Color(0xFF6F686C),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        if (points.isNotEmpty()) {
            key(order.id, attempt) {
                QgLongdoTrackingMap(
                    points,
                    Modifier.fillMaxWidth().height(mapHeight).clip(RoundedCornerShape(15.dp))
                ) {
                    ready = it
                    if (it) timedOut = false
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(mapHeight)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFFF1F2F3)),
                contentAlignment = Alignment.Center
            ) {
                Text("ยังไม่มีพิกัดจริง", color = QgMuted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(7.dp))
        val updated = runCatching { Instant.parse(rider?.updatedAt).toEpochMilli() }.getOrNull()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(QgRed))
            Text(
                when {
                    points.isEmpty() -> "ยังไม่มีพิกัดจริงของออร์เดอร์นี้"
                    timedOut -> "ยังโหลดแผนที่ไม่ได้ กรุณาลองเปิดออเดอร์ใหม่"
                    !ready -> "กำลังโหลดตำแหน่งล่าสุด"
                    searching -> "กำลังค้นหา Rider ที่พร้อมรับงานใกล้ร้าน · ระบบจะส่งต่ออัตโนมัติ"
                    !hasRiderLocation -> "รอข้อมูลตำแหน่งจริงจาก Rider"
                    else -> "ตำแหน่งล่าสุด · ${rider?.updatedAt.orEmpty()}" +
                        if (updated == null || System.currentTimeMillis() - updated > 120_000) " · ยังไม่ได้อัปเดตใหม่" else ""
                },
                color = Color(0xFF81797D),
                fontSize = 9.sp,
                lineHeight = 13.sp
            )
        }
        if (timedOut) TextButton(onClick = { ready = false; timedOut = false; attempt++ }) { Text("ลองใหม่") }
    }
    Spacer(Modifier.height(8.dp))
}
