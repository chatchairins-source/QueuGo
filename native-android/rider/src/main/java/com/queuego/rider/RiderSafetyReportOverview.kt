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
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.CancellationException

internal data class RiderSafetyReportSummary(
    val id: String,
    val status: String,
    val details: String,
    val createdAt: String?,
    val adminNote: String?
)

internal fun riderSafetyReportStatusLabel(status: String): String = when (status.uppercase()) {
    "OPEN" -> "รับเรื่องแล้ว"
    "IN_REVIEW" -> "กำลังตรวจสอบ"
    "WAITING_USER" -> "รอข้อมูลเพิ่มเติม"
    "RESOLVED" -> "แก้ไขแล้ว"
    "REJECTED" -> "ปิดเคส"
    "CLOSED" -> "ปิดเคส"
    else -> status.ifBlank { "ไม่ทราบสถานะ" }
}

internal fun riderSafetyReportReasonLabel(details: String): String {
    val key = Regex("""^\[SAFETY_INCIDENT:([^\]]+)]""")
        .find(details.trim())
        ?.groupValues
        ?.getOrNull(1)
        ?: return "เหตุการณ์ความปลอดภัย"
    return riderSafetyIncidentReasons.firstOrNull { it.key == key }?.label
        ?: "เหตุการณ์ความปลอดภัย"
}

internal fun riderSafetyReportOrderLabel(details: String): String? {
    val match = Regex("""^\[SAFETY_INCIDENT:[^\]]+]\s+([^·]+)""")
        .find(details.trim())
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?: return null
    return match.takeIf { it.startsWith("QT-") }
}

private class RiderSafetyReportOverviewApi(
    private val http: QueueGoNativeApi = QueueGoNativeApi()
) {
    suspend fun reports(auth: QueueGoAuth): List<RiderSafetyReportSummary> {
        val rows = http.array(
            http.get(
                "qg_support_tickets?select=id,status,details,created_at,admin_note" +
                    "&category=eq.rider&order=created_at.desc&limit=50",
                auth.session.accessToken
            )
        )
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val details = row.optString("details")
                if (!details.startsWith("[SAFETY_INCIDENT:")) continue
                add(
                    RiderSafetyReportSummary(
                        id = row.optString("id"),
                        status = row.optString("status"),
                        details = details,
                        createdAt = row.optString("created_at").takeIf { it.isNotBlank() },
                        adminNote = row.optString("admin_note").takeIf { it.isNotBlank() }
                    )
                )
                if (size >= 3) break
            }
        }
    }
}

@Composable
internal fun RiderSafetyReportOverviewCard(
    auth: QueueGoAuth,
    refreshKey: Int,
    onOpenSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val api = remember { RiderSafetyReportOverviewApi() }
    var reports by remember(auth.user.id) { mutableStateOf<List<RiderSafetyReportSummary>>(emptyList()) }
    var loaded by remember(auth.user.id) { mutableStateOf(false) }
    var loadError by remember(auth.user.id) { mutableStateOf(false) }

    LaunchedEffect(auth.user.id, auth.session.accessToken, refreshKey) {
        try {
            reports = api.reports(auth)
            loadError = false
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            loadError = true
        } finally {
            loaded = true
        }
    }

    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEEE6E8))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("รายงานความปลอดภัยของฉัน", fontWeight = FontWeight.Black, fontSize = 14.sp)
            Text(
                "สถานะจาก QueueGo Support โดยตรง · แสดง 3 รายงานล่าสุด",
                color = QgMuted,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 3.dp)
            )

            when {
                !loaded -> Unit
                loadError -> {
                    Text(
                        "โหลดสถานะรายงานไม่สำเร็จ กรุณาลองเปิดศูนย์ความปลอดภัยใหม่",
                        color = QgRed,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 9.dp)
                    )
                }
                reports.isEmpty() -> {
                    Text(
                        "ยังไม่มีรายงานเหตุการณ์ความปลอดภัย",
                        color = QgMuted,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 9.dp)
                    )
                }
                else -> {
                    Spacer(Modifier.height(7.dp))
                    reports.forEachIndexed { index, report ->
                        if (index > 0) HorizontalDivider(color = Color(0xFFF0EAEC))
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    riderSafetyReportReasonLabel(report.details),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    riderSafetyReportStatusLabel(report.status),
                                    color = if (report.status.uppercase() in setOf("OPEN", "IN_REVIEW", "WAITING_USER")) {
                                        QgRed
                                    } else {
                                        Color(0xFF15845E)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            }
                            riderSafetyReportOrderLabel(report.details)?.let {
                                Text(it, color = QgMuted, fontSize = 8.5.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                            report.adminNote?.let {
                                Text(
                                    "ฝ่ายดูแล: " + it,
                                    color = QgMuted,
                                    fontSize = 8.5.sp,
                                    lineHeight = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenSupport,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("ดูเรื่องที่แจ้งทั้งหมด", fontWeight = FontWeight.Bold)
            }
        }
    }
}
