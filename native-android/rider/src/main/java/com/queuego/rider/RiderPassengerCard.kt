package com.queuego.rider

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Passenger cash is per QT trip; no merchant/pickup photo/payment-confirmation flow. */
@Composable
internal fun RiderPassengerCard(job: RiderJob, busy: Boolean, networkAvailable: Boolean,
    onNavigate: () -> Unit, onChat: () -> Unit, onAction: (String) -> Unit) {
    var showCashConfirm by remember(job.id) { mutableStateOf(false) }
    val cashConfirmReady = riderPassengerCashConfirmReady(job.customerCash)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("รับส่งผู้โดยสาร · " + job.numberLabel, fontWeight = FontWeight.Bold)
            Text("จุดรับ: " + (job.pickupAddress ?: "กำลังอ่านจุดรับ"))
            Text("จุดหมาย: " + (job.deliveryAddress ?: "กำลังอ่านจุดหมาย"))
            Text("รายรับเที่ยวนี้ " + riderMoneyBaht(job.deliveryFee ?: 0.0))
            if (job.customerArrived) {
                Text("เก็บเงินสดค่าโดยสาร", fontWeight = FontWeight.Bold)
                Text(job.customerCash?.let(::riderMoneyBaht) ?: "กำลังตรวจยอดเงิน", style = MaterialTheme.typography.headlineMedium)
            }
            if (!networkAvailable) Text("ออฟไลน์ · สถานะล่าสุดที่บันทึกไว้ รอเชื่อมต่อก่อนทำขั้นตอนถัดไป")
            passengerNavigationLabel(job)?.let { label ->
                OutlinedButton(onClick = onNavigate, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Text(label)
                }
            }
            OutlinedButton(onClick = onChat, enabled = !busy && networkAvailable) { Text("ติดต่อผู้โดยสาร") }
            val action = passengerStageAction(job)
            action?.let { (key,label) ->
                if (key == "complete") {
                    Button(onClick = { showCashConfirm = true }, enabled = !busy && networkAvailable && cashConfirmReady, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "กำลังตรวจผล…" else "จบเที่ยว · เก็บเงินสด ${riderMoneyBaht(job.customerCash)}")
                    }
                    if (showCashConfirm) AlertDialog(
                        onDismissRequest = { if (!busy) showCashConfirm = false },
                        title = { Text("เก็บเงินจากผู้โดยสาร") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("ยอดที่ต้องเก็บ", style = MaterialTheme.typography.bodyMedium)
                                Text(riderMoneyBaht(job.customerCash), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                                Text("ตรวจสอบว่าได้รับเงินสดครบตามยอดแล้ว จากนั้นสไลด์เพื่อยืนยันและจบเที่ยว")
                                RiderSwipeCompleteAction(
                                    enabled = cashConfirmReady && networkAvailable,
                                    busy = busy,
                                    label = "สไลด์เพื่อยืนยันว่าเก็บเงินแล้ว",
                                    onComplete = { onAction(key) }
                                )
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showCashConfirm = false }, enabled = !busy) { Text("กลับ") }
                        }
                    )
                } else {
                    Button(onClick = { onAction(key) }, enabled = !busy && networkAvailable, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "กำลังตรวจผล…" else label)
                    }
                }
            }
        }
    }
}

internal fun riderPassengerCashConfirmReady(amount: Double?): Boolean = amount?.let { it.isFinite() && it >= 0.0 } == true

internal fun passengerNavigationLabel(job: RiderJob): String? {
    if (!job.isPassenger) return null
    return when {
        job.status == "rider_assigned" && !job.shopArrived -> "นำทางไปจุดรับผู้โดยสาร"
        job.status == "in_progress" && !job.customerArrived -> "นำทางไปจุดหมาย"
        else -> null
    }
}

internal fun passengerStageAction(job: RiderJob): Pair<String,String>? {
    if (!job.isPassenger) return null
    return when {
        job.status == "rider_assigned" && !job.shopArrived -> "arrive_pickup" to "ถึงจุดรับแล้ว"
        job.status == "rider_assigned" -> "start" to "ผู้โดยสารขึ้นรถแล้ว · เริ่มเดินทาง"
        job.status == "in_progress" && !job.customerArrived -> "arrive_dropoff" to "ถึงจุดหมายแล้ว"
        job.status == "in_progress" -> "complete" to "จบเที่ยว"
        else -> null
    }
}
