package com.queuego.rider

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import com.queuego.shared.QgRedSoft

internal data class RiderArrivalGuardState(
    val job: RiderJob,
    val target: String,
    val meters: Int?
)

@Composable
internal fun RiderArrivalGuard(
    state: RiderArrivalGuardState,
    busy: Boolean,
    onProceed: () -> Unit,
    onDismiss: () -> Unit
) {
    val shop = state.target == "shop"
    val place = if (shop) "ร้าน" else "จุดส่งลูกค้า"
    BackHandler(enabled = !busy, onBack = onDismiss)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x88161217))
            .clickable(enabled = !busy, onClick = onDismiss)
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .background(
                    Color.White,
                    RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
                )
                .clickable(enabled = true, onClick = {})
                .padding(horizontal = 17.dp)
                .padding(top = 12.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(width = 40.dp, height = 5.dp)
                    .background(Color(0xFFDED8DA), RoundedCornerShape(99.dp))
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .size(66.dp)
                    .background(QgRedSoft, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("!", color = QgRed, fontSize = 34.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(10.dp))
            Text("ถึง${place}จริงหรือเปล่า?", fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(5.dp))
            Text(
                if (state.meters != null)
                    "GPS แสดงว่าคุณยังห่างจาก${place}เกิน 200 เมตร คำเตือนนี้ไม่ล็อกงาน คุณยังยืนยันต่อได้"
                else
                    "ยังตรวจสอบระยะจาก GPS ไม่ได้ คำเตือนนี้ไม่ล็อกงาน คุณยังยืนยันต่อได้",
                color = QgMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(13.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF3F6), RoundedCornerShape(15.dp))
                    .padding(11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(30.dp).background(QgRedSoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("•", color = QgRed, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.meters != null) "ระยะจากจุดหมายประมาณ" else "GPS ยังไม่พร้อม",
                        color = QgMuted,
                        fontSize = 9.sp
                    )
                }
                state.meters?.let {
                    Text("${it} ม.", color = QgRed, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(13.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onProceed,
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF04455))
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("ยืนยันว่าถึงแล้ว", fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text("ย้อนกลับ", fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
        }
    }
}
