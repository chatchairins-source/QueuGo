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
    val urgent: Boolean = false,
    val priority: Int = 50
)

internal fun riderHomeActionCards(
    missions: List<RiderMission>,
    diagnostics: RiderDiagnosticsSnapshot,
    documents: RiderDocumentReadiness?,
    documentsLoaded: Boolean,
    reverification: RiderReverificationStatus? = null,
    reverificationLoaded: Boolean = false,
    vehicleInspection: RiderVehicleInspectionStatus? = null,
    vehicleInspectionLoaded: Boolean = false,
    selectedVehicle: RiderVehicle? = null,
    vehiclesLoaded: Boolean = false
): List<RiderHomeActionCardModel> {
    val cards = mutableListOf<RiderHomeActionCardModel>()

    if (reverificationLoaded && reverification != null && riderReverificationBlocksOnline(reverification)) {
        cards += RiderHomeActionCardModel(
            key = "identity:" + (reverification.status ?: "required"),
            title = when (reverification.status) {
                "pending" -> "ยืนยันตัวตน · รอตรวจสอบ"
                "rejected" -> "ต้องถ่ายรูปยืนยันตัวตนใหม่"
                else -> "ต้องยืนยันตัวตน Rider"
            },
            detail = when (reverification.status) {
                "pending" -> "QueueGo ได้รับรูปแล้ว ฝ่ายดูแลกำลังตรวจสอบ"
                "rejected" -> reverification.reviewNote?.takeIf { it.isNotBlank() }
                    ?: "รูปก่อนหน้ายังไม่ผ่าน กรุณาถ่ายรูปใหม่ในโปรไฟล์"
                else -> reverification.reason?.takeIf { it.isNotBlank() }
                    ?: "กรุณาถ่ายรูปหน้าตรงใหม่ในโปรไฟล์"
            },
            target = RiderHomeActionTarget.PROFILE,
            urgent = true,
            priority = 0
        )
    }

    if (vehicleInspectionLoaded && vehicleInspection != null &&
        riderVehicleInspectionBlocksOnline(vehicleInspection)
    ) {
        cards += RiderHomeActionCardModel(
            key = "vehicle-inspection:" + (vehicleInspection.status ?: "required"),
            title = when (vehicleInspection.status) {
                "pending" -> "ตรวจสภาพรถ · รอตรวจสอบ"
                "rejected" -> "ต้องถ่ายรูปรถใหม่"
                else -> "ต้องตรวจสภาพรถ"
            },
            detail = when (vehicleInspection.status) {
                "pending" -> "QueueGo ได้รับรูปรถแล้ว ฝ่ายดูแลกำลังตรวจสอบ"
                "rejected" -> vehicleInspection.reviewNote?.takeIf { it.isNotBlank() }
                    ?: "รูปสภาพรถยังไม่ผ่าน กรุณาถ่ายและส่งใหม่"
                else -> vehicleInspection.reason?.takeIf { it.isNotBlank() }
                    ?: "กรุณาส่งรูปตรวจสภาพรถในโปรไฟล์"
            },
            target = RiderHomeActionTarget.PROFILE,
            urgent = true,
            priority = 1
        )
    }

    if (vehiclesLoaded && riderVehicleBlocksOnline(selectedVehicle)) {
        cards += RiderHomeActionCardModel(
            key = "selected-vehicle",
            title = "รถที่เลือกยังไม่พร้อมรับงาน",
            detail = selectedVehicle?.let {
                "รถ " + it.plate + " ยังไม่ผ่านการอนุมัติ กรุณาเลือกรถที่อนุมัติแล้ว"
            } ?: "กรุณาเลือกรถที่ผ่านการอนุมัติ",
            target = RiderHomeActionTarget.PROFILE,
            urgent = true,
            priority = 2
        )
    }

    val activeMission = missions.firstOrNull { it.state == "active" && !it.completed }
        ?: missions.firstOrNull { it.state == "upcoming" && !it.completed }
    activeMission?.let { mission ->
        cards += RiderHomeActionCardModel(
            key = "mission:" + mission.id,
            title = if (mission.state == "upcoming") "ภารกิจกำลังจะเริ่ม" else "ภารกิจของคุณ",
            detail = mission.title + " · " + mission.progressJobs + "/" + mission.targetJobs + " เที่ยว",
            target = RiderHomeActionTarget.EARNINGS,
            priority = 5
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
            urgent = true,
            priority = 3
        )
    } else if (documentsLoaded) {
        documents?.items?.firstOrNull { it.status == "expiring" }?.let { item ->
            cards += RiderHomeActionCardModel(
                key = "documents:expiring:" + item.key,
                title = "เอกสารใกล้หมดอายุ",
                detail = item.label + (item.daysRemaining?.let { " · เหลือ ${it.coerceAtLeast(0)} วัน" } ?: ""),
                target = RiderHomeActionTarget.PROFILE,
                priority = 4
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
            target = RiderHomeActionTarget.PROFILE,
            priority = 6
        )
    }

    return cards
        .distinctBy { it.key }
        .sortedBy { it.priority }
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
