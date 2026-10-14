package com.queuego.rider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import kotlinx.coroutines.CancellationException

internal fun riderDocumentStatusText(item: RiderDocumentStatus): String = when (item.status) {
    "expired" -> "หมดอายุ" + (item.expiresOn?.let { " · $it" } ?: "")
    "expiring" -> {
        val days = item.daysRemaining?.coerceAtLeast(0)
        "ใกล้หมดอายุ" + (days?.let { " · เหลือ $it วัน" } ?: "") +
            (item.expiresOn?.let { " · $it" } ?: "")
    }
    "valid" -> "ใช้ได้" + (item.expiresOn?.let { " · หมดอายุ $it" } ?: "")
    else -> "ยังไม่ระบุวันหมดอายุ"
}

internal fun riderDocumentStatusColor(status: String): Color = when (status) {
    "expired" -> QgRed
    "expiring" -> Color(0xFF9A6B00)
    "valid" -> Color(0xFF12845E)
    else -> QgMuted
}

@Composable
internal fun RiderDocumentReadinessCard(
    auth: QueueGoAuth,
    api: QueueGoApi,
    modifier: Modifier = Modifier
) {
    var readiness by remember(auth.user.id) { mutableStateOf<RiderDocumentReadiness?>(null) }
    var loaded by remember(auth.user.id) { mutableStateOf(false) }

    LaunchedEffect(auth.user.id, auth.session.accessToken) {
        try {
            readiness = api.documentReadiness(auth)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            readiness = null
        } finally {
            loaded = true
        }
    }

    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFFEEE6E8)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("เอกสารและวันหมดอายุ", fontWeight = FontWeight.Black, fontSize = 14.sp)
            Text(
                "วันหมดอายุมาจากเอกสารจริงที่ Admin ตรวจแล้ว · ข้อมูลที่ยังไม่ระบุจะไม่ปิดรับงาน",
                color = QgMuted,
                fontSize = 8.5.sp,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            if (!loaded) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 9.dp))
            } else if (readiness == null) {
                Text(
                    "โหลดสถานะเอกสารไม่สำเร็จ",
                    color = QgMuted,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 9.dp)
                )
            } else {
                Spacer(Modifier.height(7.dp))
                readiness!!.items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = Color(0xFFF0EAEC))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.label + (item.vehiclePlate?.let { " · $it" } ?: ""),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                riderDocumentStatusText(item),
                                color = riderDocumentStatusColor(item.status),
                                fontSize = 8.5.sp,
                                fontWeight = if (item.status in setOf("expired","expiring")) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }
                if (readiness!!.blocking) {
                    Text(
                        "มีเอกสารหมดอายุ · กรุณาติดต่อ Admin ก่อนเปิดรับงาน",
                        color = QgRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}
