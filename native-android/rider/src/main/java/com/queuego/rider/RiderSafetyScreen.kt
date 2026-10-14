package com.queuego.rider

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed

internal const val RIDER_EMERGENCY_POLICE = "191"
internal const val RIDER_EMERGENCY_MEDICAL = "1669"
internal const val RIDER_TOURIST_POLICE = "1155"

internal data class RiderSafetyContact(
    val title: String,
    val detail: String,
    val number: String,
    val urgent: Boolean
)

internal fun riderSafetyContacts(): List<RiderSafetyContact> = listOf(
    RiderSafetyContact(
        title = "เหตุด่วนเหตุร้าย",
        detail = "ตำรวจ 191",
        number = RIDER_EMERGENCY_POLICE,
        urgent = true
    ),
    RiderSafetyContact(
        title = "เจ็บป่วย / อุบัติเหตุฉุกเฉิน",
        detail = "การแพทย์ฉุกเฉิน 1669",
        number = RIDER_EMERGENCY_MEDICAL,
        urgent = true
    ),
    RiderSafetyContact(
        title = "ตำรวจท่องเที่ยว",
        detail = "สายด่วน 1155",
        number = RIDER_TOURIST_POLICE,
        urgent = false
    )
)

@Composable
internal fun RiderSafetyScreen(
    activeJob: RiderJob?,
    onSupport: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun dial(number: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
            )
        }
    }

    Column(
        modifier
            .heightIn(max = 720.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("ศูนย์ความปลอดภัย", fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("QueueGo Rider", color = QgMuted, fontSize = 10.sp)
            }
            TextButton(onClick = onClose) {
                Text("ปิด", color = QgRed, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(10.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4F4)),
            border = BorderStroke(1.dp, Color(0xFFFFD8DB))
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("กรณีฉุกเฉิน", color = QgRed, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(
                    "ปุ่มด้านล่างจะเปิดหน้าจอโทรศัพท์ก่อนทุกครั้ง เพื่อป้องกันการโทรโดยไม่ตั้งใจ",
                    color = QgMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                activeJob?.let { job ->
                    Spacer(Modifier.height(8.dp))
                    Text("ออเดอร์ปัจจุบัน", color = QgMuted, fontSize = 9.sp)
                    Text(job.numberLabel, fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        riderSafetyContacts().forEach { contact ->
            RiderSafetyContactCard(contact) { dial(contact.number) }
        }

        Spacer(Modifier.height(5.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFEEE6E8))
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("QueueGo Support", fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text(
                    "ปัญหาเกี่ยวกับออเดอร์ ลูกค้า ร้านค้า การรับงาน หรือบัญชี Rider",
                    color = QgMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSupport,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("ติดต่อฝ่ายดูแล QueueGo", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RiderSafetyContactCard(
    contact: RiderSafetyContact,
    onDial: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(
            1.dp,
            if (contact.urgent) Color(0xFFFFD8DB) else Color(0xFFEEE6E8)
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    contact.title,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = if (contact.urgent) QgRed else Color(0xFF17191D)
                )
                Text(contact.detail, color = QgMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Button(
                onClick = onDial,
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (contact.urgent) QgRed else Color(0xFF17191D)
                )
            ) {
                Text("โทร " + contact.number, fontWeight = FontWeight.Bold)
            }
        }
    }
}
