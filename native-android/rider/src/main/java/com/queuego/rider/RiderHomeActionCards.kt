package com.queuego.rider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

internal enum class RiderHomeActionTarget {
    EARNINGS,
    PROFILE
}

internal data class RiderHomeActionCardModel(
    val key: String,
    val title: String,
    val detail: String,
    val target: RiderHomeActionTarget,
    val urgent: Boolean = false
)

internal fun riderHomeActionCards(
    missions: List<RiderMission>,
    diagnostics: RiderDiagnosticsSnapshot,
    documents: RiderDocumentReadiness?,
    documentsLoaded: Boolean
): List<RiderHomeActionCardModel> {
    val cards = mutableListOf<RiderHomeActionCardModel>()

    val activeMission = missions.firstOrNull { it.state == "active" && !it.completed }
        ?: missions.firstOrNull { it.state == "upcoming" && !it.completed }
    activeMission?.let { mission ->
        cards += RiderHomeActionCardModel(
            key = "mission:" + mission.id,
            title = if (mission.state == "upcoming") "ภารกิจกำลังจะเริ่ม" else "ภารกิจของคุณ",
            detail = mission.title + " · " + mission.progressJobs + "/" + mission.targetJobs + " เที่ยว",
            target = RiderHomeActionTarget.EARNINGS
        )
    }

    if (documentsLoaded && documents?.blocking == true) {
        val expired = documents.items.firstOrNull { it.status == "expired" }
        cards += RiderHomeActionCardModel(
            key = "documents:blocked",
            title = "เอกสารต้องแก้ไข",
            detail = expired?.let { item ->
                item.label + (item.expiresOn?.let { date -> " · $date" } ?: "")
            } ?: "มีเอกสารหมดอายุ กรุณาตรวจสอบในโปรไฟล์",
            target = RiderHomeActionTarget.PROFILE,
            urgent = true
        )
    } else if (documentsLoaded) {
        documents?.items?.firstOrNull { it.status == "expiring" }?.let { item ->
            cards += RiderHomeActionCardModel(
                key = "documents:expiring:" + item.key,
                title = "เอกสารใกล้หมดอายุ",
                detail = item.label + (item.daysRemaining?.let { " · เหลือ ${it.coerceAtLeast(0)} วัน" } ?: ""),
                target = RiderHomeActionTarget.PROFILE
            )
        }
    }

    if (!diagnostics.backgroundReliabilityReady) {
        val detail = when {
            diagnostics.backgroundRestricted && !diagnostics.batteryOptimizationExempt ->
                "Android จำกัดเบื้องหลังและ Battery Optimization ยังทำงานอยู่"
            diagnostics.backgroundRestricted ->
                "Android กำลังจำกัดการทำงานเบื้องหลัง"
            else ->
                "แนะนำให้ยกเว้น QueueGo Rider จาก Battery Optimization"
        }
        cards += RiderHomeActionCardModel(
            key = "background-health",
            title = "ปรับการทำงานเบื้องหลัง",
            detail = detail,
            target = RiderHomeActionTarget.PROFILE
        )
    }

    return cards
        .distinctBy { it.key }
        .sortedWith(
            compareByDescending<RiderHomeActionCardModel> { it.urgent }
                .thenBy { if (it.key.startsWith("mission:")) 0 else 1 }
        )
        .take(2)
}

@Composable
internal fun RiderHomeActionCards(
    cards: List<RiderHomeActionCardModel>,
    onAction: (RiderHomeActionTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    if (cards.isEmpty()) return

    Column(
        modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        cards.forEach { item ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable { onAction(item.target) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (item.urgent) Color(0xFFFFF2F3) else Color(0xFFFAF7F8)
                ),
                border = BorderStroke(
                    1.dp,
                    if (item.urgent) Color(0xFFFFCDD2) else Color(0xFFEEE5E8)
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (item.urgent) "!" else "•",
                        color = if (item.urgent) QgRed else Color(0xFF9A6B00),
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontSize = 10.5.sp, fontWeight = FontWeight.Black)
                        Text(
                            item.detail,
                            color = QgMuted,
                            fontSize = 8.5.sp,
                            lineHeight = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Text("›", color = QgRed, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            }
        }
    }
}
