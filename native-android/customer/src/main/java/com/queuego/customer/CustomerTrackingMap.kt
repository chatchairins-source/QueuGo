package com.queuego.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.QgCard
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
    QgCard(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (searching) "พื้นที่ค้นหา Rider" else "ติดตามการจัดส่ง", fontWeight = FontWeight.Bold)
                Text(if (searching) "ค้นหาอัตโนมัติ" else "อัปเดตตำแหน่ง", color = QgRed, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(7.dp))
            if (points.isNotEmpty()) {
                key(order.id, attempt) {
                    QgLongdoTrackingMap(points, Modifier.fillMaxWidth().height(mapHeight).clip(RoundedCornerShape(15.dp))) {
                        ready = it
                        if (it) timedOut = false
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            val updated = runCatching { Instant.parse(rider?.updatedAt).toEpochMilli() }.getOrNull()
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
                color = QgMuted,
                style = MaterialTheme.typography.bodySmall
            )
            if (timedOut) TextButton(onClick = { ready = false; timedOut = false; attempt++ }) { Text("ลองใหม่") }
        }
    }
    Spacer(Modifier.height(10.dp))
}
