package com.queuego.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRedSoft

@Composable
internal fun RiderLaundryActiveCard(
    job: RiderLaundryJob,
    busy: Boolean,
    onNavigate: () -> Unit,
    onAction: (String) -> Unit
) {
    val action = when (job.jobStatus) {
        "assigned", "accepted" -> "arrive"
        "arrived" -> "collect"
        "collected" -> "deliver"
        else -> null
    }
    val actionLabel = when (action) {
        "arrive" -> if (job.leg == "pickup") "ถึงจุดรับผ้าแล้ว" else "ถึงร้านแล้ว"
        "collect" -> if (job.leg == "pickup") "รับผ้าจากลูกค้าแล้ว" else "รับผ้าสะอาดจากร้านแล้ว"
        "deliver" -> if (job.leg == "pickup") "ส่งผ้าถึงร้านแล้ว" else "ส่งคืนลูกค้าแล้ว"
        else -> ""
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFDFD)),
        border = BorderStroke(1.dp, Color(0xFFEEE5E8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("งานฝากซัก", color = QgRed, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Text(job.numberLabel, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text(job.routeLabel + " · " + (job.serviceName ?: "บริการฝากซัก"), color = QgMuted, fontSize = 9.sp)
                }
                Text(
                    "฿%.0f".format(job.jobFee),
                    color = QgRed,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.background(QgRedSoft, RoundedCornerShape(13.dp)).padding(horizontal = 9.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            LaundryStop("จาก", job.fromAddress ?: "-", true)
            LaundryStop("ไป", job.toAddress ?: "-", false)
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigate,
                    enabled = !busy && job.navigationTarget != null,
                    modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                    shape = RoundedCornerShape(15.dp)
                ) { Text("นำทาง", fontWeight = FontWeight.Black) }
                if (action != null) {
                    Button(
                        onClick = { onAction(action) },
                        enabled = !busy,
                        modifier = Modifier.weight(1.25f).heightIn(min = 46.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF04455))
                    ) { Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}

@Composable
private fun LaundryStop(label: String, value: String, start: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).background(if (start) Color(0xFFFFF0F3) else Color(0xFFF2F7FF), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (start) "A" else "B", color = if (start) QgRed else Color(0xFF276DD8), fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = QgMuted, fontSize = 8.sp)
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        }
    }
}

@Composable
internal fun RiderLaundryPoolCard(
    jobs: List<RiderLaundryJob>,
    busy: Boolean,
    onClaim: (RiderLaundryJob) -> Unit
) {
    if (jobs.isEmpty()) return
    Card(
        Modifier.fillMaxWidth().padding(top = 9.dp),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEEE6E8))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("งานฝากซักที่รับได้", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("เฉพาะร้านที่คุณยอมรับเป็น Rider แล้ว", color = QgMuted, fontSize = 8.sp)
                }
                Text("${jobs.size} งาน", color = QgMuted, fontSize = 8.sp)
            }
            jobs.take(5).forEach { job ->
                HorizontalDivider(color = Color(0xFFF0EAEC), modifier = Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (job.leg == "pickup") "รับผ้าจากลูกค้า" else "ส่งผ้าคืนลูกค้า") + " · " + job.numberLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text((job.shopName ?: job.hubName ?: "ร้านซัก") + " · " + (job.serviceName ?: "บริการฝากซัก"), color = QgMuted, fontSize = 8.sp)
                        Text((job.fromAddress ?: "-") + " → " + (job.toAddress ?: "-"), color = QgMuted, fontSize = 8.sp, maxLines = 2)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("฿%.0f".format(job.jobFee), color = QgRed, fontWeight = FontWeight.Black)
                        Button(
                            onClick = { onClaim(job) },
                            enabled = !busy,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = QgRedSoft, contentColor = QgRed)
                        ) { Text("รับงาน", fontSize = 10.sp, fontWeight = FontWeight.Black) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RiderLaundryInvites(
    invites: List<RiderLaundryInvite>,
    busy: Boolean,
    onAction: (RiderLaundryInvite, Boolean) -> Unit
) {
    if (invites.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        invites.forEach { invite ->
            Card(
                Modifier.fillMaxWidth().padding(bottom = 7.dp),
                shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFEEE6E8))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("คำเชิญงานฝากซัก", fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text(invite.shopName.ifBlank { invite.hubName }, color = QgMuted, fontSize = 9.sp)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Button(
                            onClick = { onAction(invite, true) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF04455))
                        ) { Text("ยอมรับ", fontSize = 10.sp, fontWeight = FontWeight.Black) }
                        OutlinedButton(
                            onClick = { onAction(invite, false) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("ปฏิเสธ", fontSize = 10.sp, fontWeight = FontWeight.Black) }
                    }
                }
            }
        }
    }
}
