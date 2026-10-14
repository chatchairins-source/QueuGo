package com.queuego.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed

internal fun riderDeliveryReturnReasonLabel(reason: String): String = when (reason) {
    "customer_unreachable" -> "ติดต่อลูกค้าไม่ได้"
    "recipient_refuses_delivery" -> "ลูกค้าปฏิเสธรับสินค้า"
    "recipient_refuses_cash" -> "ลูกค้าปฏิเสธชำระเงิน"
    "wrong_dropoff" -> "ที่อยู่หรือจุดส่งมีปัญหา"
    "unsafe_dropoff" -> "จุดส่งไม่ปลอดภัย"
    else -> "ปัญหาการจัดส่ง"
}

@Composable
internal fun RiderDeliveryReturnCard(
    state: RiderDeliveryReturn,
    photoReady: Boolean,
    busy: Boolean,
    onStartReturn: () -> Unit,
    onNavigate: () -> Unit,
    onCaptureProof: () -> Unit,
    onCompleteReturn: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFDFD)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                if (state.waitingForSupport) "กำลังแก้ปัญหาการจัดส่ง" else "คืนสินค้าให้ร้าน",
                color = QgRed,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp
            )
            Text(state.numberLabel, fontWeight = FontWeight.Black, fontSize = 23.sp)
            Text(
                riderDeliveryReturnReasonLabel(state.reason),
                color = Color(0xFF4F484C),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp)
            )

            if (state.waitingForSupport) {
                Column(
                    Modifier.fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(Color(0xFFFFF6E7), RoundedCornerShape(14.dp))
                        .padding(11.dp)
                ) {
                    Text("ส่งเรื่องให้ฝ่ายดูแลแล้ว", fontWeight = FontWeight.Black, fontSize = 11.sp)
                    Text(
                        "ยังไม่ต้องคืนสินค้าและอย่ากดจบงาน ระบบจะอัปเดตทันทีเมื่อมีคำตัดสิน",
                        color = QgMuted,
                        fontSize = 9.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                state.adminNote?.let {
                    Text(it, color = QgMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp))
                }
                return@Column
            }

            Column(
                Modifier.fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(Color(0xFFFFF3F5), RoundedCornerShape(14.dp))
                    .padding(11.dp)
            ) {
                Text("ร้านที่ต้องนำสินค้าคืน", color = QgMuted, fontSize = 8.sp)
                Text(state.shopName ?: "ร้านค้า", fontWeight = FontWeight.Black, fontSize = 13.sp)
                state.shopAddress?.let {
                    Text(it, color = Color(0xFF5E575B), fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("เงินสดที่ต้องได้รับคืน", color = QgMuted, fontSize = 8.sp)
                    Text(
                        "฿" + riderMoneyNumber(state.expectedRefundAmount),
                        color = QgRed,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("สถานะ", color = QgMuted, fontSize = 8.sp)
                    Text(
                        if (state.status == "returning") "กำลังคืนร้าน" else "พร้อมคืนร้าน",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
            }

            state.adminNote?.let {
                Text(
                    it,
                    color = Color(0xFF5E575B),
                    fontSize = 9.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
            if (state.status == "return_required") {
                Button(
                    onClick = onStartReturn,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Text("นำทางกลับร้าน", fontWeight = FontWeight.Black)
                }
            } else {
                OutlinedButton(
                    onClick = onNavigate,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(45.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("นำทางกลับร้าน", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(7.dp))
                Button(
                    onClick = if (photoReady) onCompleteReturn else onCaptureProof,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Text(
                        if (photoReady) "คืนสินค้าเรียบร้อย" else "ถ่ายรูปหลักฐานการคืน",
                        fontWeight = FontWeight.Black
                    )
                }
                if (photoReady) {
                    Text(
                        "✓ มีรูปหลักฐานแล้ว · เมื่อจบงาน ระบบจะบันทึกเงินคืนจากร้านเข้าบัญชีเงินสดของ Rider",
                        color = QgMuted,
                        fontSize = 8.5.sp,
                        lineHeight = 12.sp,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }
            }
        }
    }
}
