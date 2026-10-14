package com.queuego.rider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed

internal enum class RiderAvailabilityCheckState {
    READY,
    BLOCKED,
    CHECKING
}

internal data class RiderAvailabilityCheck(
    val key: String,
    val title: String,
    val detail: String,
    val state: RiderAvailabilityCheckState
)

internal data class RiderAvailabilityReadiness(
    val checks: List<RiderAvailabilityCheck>
) {
    val totalCount: Int get() = checks.size
    val readyCount: Int get() = checks.count { it.state == RiderAvailabilityCheckState.READY }
    val blocked: List<RiderAvailabilityCheck>
        get() = checks.filter { it.state == RiderAvailabilityCheckState.BLOCKED }
    val checking: List<RiderAvailabilityCheck>
        get() = checks.filter { it.state == RiderAvailabilityCheckState.CHECKING }
    val fullyReady: Boolean get() = blocked.isEmpty() && checking.isEmpty()
}

internal fun riderAvailabilityReadiness(
    diagnostics: RiderDiagnosticsSnapshot,
    firebaseConfigured: Boolean,
    reverification: RiderReverificationStatus?,
    reverificationLoaded: Boolean
): RiderAvailabilityReadiness {
    fun state(ready: Boolean) =
        if (ready) RiderAvailabilityCheckState.READY else RiderAvailabilityCheckState.BLOCKED

    val identityCheck = when {
        !reverificationLoaded -> RiderAvailabilityCheck(
            key = "identity",
            title = "สิทธิ์บัญชี",
            detail = "กำลังตรวจสถานะยืนยันตัวตนจาก QueueGo",
            state = RiderAvailabilityCheckState.CHECKING
        )
        reverification != null && riderReverificationBlocksOnline(reverification) -> RiderAvailabilityCheck(
            key = "identity",
            title = "ยืนยันตัวตน",
            detail = when (reverification.status) {
                "pending" -> "ส่งรูปแล้วและกำลังรอ Admin ตรวจสอบ"
                "rejected" -> reverification.reviewNote
                    ?.takeIf { it.isNotBlank() }
                    ?: "ต้องถ่ายรูปยืนยันตัวตนใหม่"
                else -> reverification.reason
                    ?.takeIf { it.isNotBlank() }
                    ?: "ต้องยืนยันตัวตน Rider ก่อนเปิดรับงาน"
            },
            state = RiderAvailabilityCheckState.BLOCKED
        )
        else -> RiderAvailabilityCheck(
            key = "identity",
            title = "สิทธิ์บัญชี",
            detail = "ไม่มีคำขอยืนยันตัวตนที่บล็อกการเปิดรับงาน",
            state = RiderAvailabilityCheckState.READY
        )
    }

    return RiderAvailabilityReadiness(
        listOf(
            RiderAvailabilityCheck(
                "network",
                "อินเทอร์เน็ต",
                "ต้องเชื่อมต่ออินเทอร์เน็ตก่อนเปิดรับงาน",
                state(diagnostics.networkReady)
            ),
            RiderAvailabilityCheck(
                "firebase",
                "ระบบแจ้งเตือนงาน",
                "Firebase ของ QueueGo Rider ยังไม่พร้อมบนเครื่องนี้",
                state(firebaseConfigured)
            ),
            RiderAvailabilityCheck(
                "notifications",
                "การแจ้งเตือน",
                "ต้องเปิดการแจ้งเตือนเพื่อไม่พลาดข้อเสนองาน",
                state(diagnostics.notifications)
            ),
            RiderAvailabilityCheck(
                "location_permission",
                "สิทธิ์ตำแหน่ง",
                "ต้องอนุญาตตำแหน่งให้ QueueGo Rider",
                state(diagnostics.locationPermission)
            ),
            RiderAvailabilityCheck(
                "location_service",
                "GPS / Location",
                "ต้องเปิด Location ของโทรศัพท์ก่อนรับงาน",
                state(diagnostics.locationServices)
            ),
            RiderAvailabilityCheck(
                "google_maps",
                "Google Maps",
                "ต้องติดตั้ง Google Maps สำหรับการนำทาง",
                state(diagnostics.googleMaps)
            ),
            RiderAvailabilityCheck(
                "overlay",
                "ปุ่มกลับ QueueGo",
                "ต้องเปิดสิทธิ์แสดงทับแอปอื่นเพื่อกลับจาก Google Maps ได้เร็ว",
                state(diagnostics.overlayReturn)
            ),
            identityCheck
        )
    )
}

@Composable
internal fun RiderAvailabilityReadinessCard(
    readiness: RiderAvailabilityReadiness,
    modifier: Modifier = Modifier
) {
    val ready = readiness.fullyReady
    val background = if (ready) Color(0xFFEAF8F2) else Color(0xFFFFFAF1)
    val border = if (ready) Color(0xFFCBECDD) else Color(0xFFFFE2B8)
    val headline = if (ready) "พร้อมเปิดรับงาน" else "ตรวจความพร้อมก่อนเปิดรับงาน"
    val blocked = readiness.blocked.take(3)
    val checking = readiness.checking.take(1)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        border = BorderStroke(1.dp, border)
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .size(24.dp)
                        .background(
                            if (ready) Color(0xFFD8F1E6) else Color(0xFFFFECD0),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (ready) "✓" else "!",
                        color = if (ready) Color(0xFF12845E) else Color(0xFF9A6511),
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(headline, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text(
                        "พร้อม ${readiness.readyCount}/${readiness.totalCount} รายการ",
                        color = QgMuted,
                        fontSize = 8.5.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }

            blocked.forEach { item ->
                Text(
                    "• ${item.title}: ${item.detail}",
                    color = Color(0xFF7A5A21),
                    fontSize = 8.5.sp,
                    lineHeight = 12.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
            checking.forEach { item ->
                Text(
                    "• ${item.detail}",
                    color = QgMuted,
                    fontSize = 8.5.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
            if (readiness.blocked.size > blocked.size) {
                Text(
                    "และอีก ${readiness.blocked.size - blocked.size} รายการ · แตะเปิดรับงานเพื่อไปตั้งค่ารายการแรก",
                    color = QgRed,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 5.dp)
                )
            } else if (!ready && blocked.isNotEmpty()) {
                Text(
                    "แตะเปิดรับงาน ระบบจะพาไปแก้รายการแรกที่ยังไม่พร้อม",
                    color = QgMuted,
                    fontSize = 8.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
    }
}
