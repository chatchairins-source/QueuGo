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
            OutlinedButton(onClick = onNavigate, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                Text(if (job.isDelivering) "นำทางไปจุดหมาย" else "นำทางไปจุดรับผู้โดยสาร")
            }
            OutlinedButton(onClick = onChat, enabled = !busy && networkAvailable) { Text("ติดต่อผู้โดยสาร") }
            val action = passengerStageAction(job)
            action?.let { (key,label) ->
                Button(onClick = { onAction(key) }, enabled = !busy && networkAvailable && (key != "complete" || job.customerCash != null), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "กำลังตรวจผล…" else label) }
            }
        }
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

@Composable
internal fun RiderPassengerEligibility(auth: QueueGoAuth) {
    var ready by androidx.compose.runtime.remember(auth.user.id) { androidx.compose.runtime.mutableStateOf<Boolean?>(null) }
    var failed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var retry by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(auth.user.id,auth.session.accessToken,retry) {
        try {
            val state = com.queuego.shared.QueueGoNativeApi().rpc("queuego_ride_state",auth.session.accessToken) as org.json.JSONObject
            ready = state.getBoolean("public_license_valid");failed = false
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("สิทธิ์รับส่งผู้โดยสาร",fontWeight = FontWeight.Bold)
            Text(when { failed -> "ยังตรวจสิทธิ์ไม่ได้";ready == true -> "ใบขับขี่สาธารณะผ่านการตรวจและยังไม่หมดอายุ";ready == false -> "ต้องมีใบขับขี่สาธารณะที่ Admin ตรวจอนุมัติก่อนรับเที่ยว";else -> "กำลังตรวจสิทธิ์…" })
            TextButton(onClick = { retry++ }) { Text("ตรวจสิทธิ์ล่าสุด") }
        }
    }
}
