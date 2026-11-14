package com.queuego.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRedSoft

@Composable
internal fun RiderExpressCard(
    job: RiderJob,
    busy: Boolean,
    networkAvailable: Boolean,
    onNavigate: () -> Unit,
    onChat: () -> Unit,
    onArrivePickup: () -> Unit,
    onPickupProof: () -> Unit,
    onStartTransit: () -> Unit,
    onArriveDropoff: () -> Unit,
    onDeliveryProof: () -> Unit
) {
    val actionLabel: String
    val action: () -> Unit
    when {
        job.status == "rider_assigned" && job.arrivedShopAt == null -> {
            actionLabel = "ถึงจุดรับแล้ว"
            action = onArrivePickup
        }
        job.status == "rider_assigned" -> {
            actionLabel = "ถ่ายรูปและรับพัสดุ"
            action = onPickupProof
        }
        job.status == "picked_up" -> {
            actionLabel = "เริ่มนำส่ง"
            action = onStartTransit
        }
        job.status == "in_progress" && job.arrivedCustomerAt == null -> {
            actionLabel = "ถึงจุดส่งแล้ว"
            action = onArriveDropoff
        }
        else -> {
            actionLabel = "ถ่ายรูปส่งและจบงาน"
            action = onDeliveryProof
        }
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE5E8))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Express ส่งด่วน", color = QgRed, fontWeight = FontWeight.ExtraBold)
                    Text(job.numberLabel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                }
                Text(riderMoneyBaht(job.deliveryFee), color = QgRed, fontWeight = FontWeight.Black)
            }
            Box(Modifier.fillMaxWidth().background(QgRedSoft, RoundedCornerShape(14.dp)).padding(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("พัสดุ", color = QgMuted, style = MaterialTheme.typography.labelSmall)
                    Text(
                        (job.expressItemType ?: "พัสดุ") + " · " +
                            riderMoneyNumber(job.expressWeightKg) + " กก. · " +
                            (job.expressSize ?: "ไม่ระบุขนาด"),
                        fontWeight = FontWeight.Bold
                    )
                    Text("รถ: " + when (job.expressVehicleType) {
                        "motorcycle" -> "มอเตอร์ไซค์"
                        "car" -> "รถยนต์"
                        "saleng" -> "ซาเล้ง"
                        else -> "ตามงาน"
                    }, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("รับพัสดุ · " + (job.pickupAddress ?: "จุดรับพัสดุ"), fontWeight = FontWeight.Bold)
            Text("ผู้ส่ง: " + (job.expressSenderName ?: "ผู้ส่ง"), color = QgMuted)
            Text("ส่งพัสดุ · " + (job.deliveryAddress ?: "จุดส่งพัสดุ"), fontWeight = FontWeight.Bold)
            Text("ผู้รับ: " + (job.expressReceiverName ?: "ผู้รับ"), color = QgMuted)
            if (!job.expressInstructions.isNullOrBlank()) {
                Text("หมายเหตุ: " + job.expressInstructions, color = QgMuted)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onNavigate, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("นำทาง")
                }
                OutlinedButton(onClick = onChat, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("แชต")
                }
            }
            Button(
                onClick = action,
                enabled = !busy && networkAvailable,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(17.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                else Text(actionLabel, fontWeight = FontWeight.Black)
            }
        }
    }
}
