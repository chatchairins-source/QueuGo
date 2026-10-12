package com.queuego.rider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
internal fun RiderRouteBundlePanel(
    state: RiderRouteBundleState,
    busy: Boolean,
    onClaim: (RiderBundleCandidate) -> Unit
) {
    val active = state.activeOrders
    val offers = state.candidates
    if (active.size <= 1 && offers.isEmpty()) return

    if (active.size > 1) {
        val live = active.filter { it.job.status != "cancelled" }
        val totalIncome = live.sumOf { it.job.deliveryFee ?: 0.0 }
        val totalSavings = live.sumOf { it.customerSavings }
        Card(
            Modifier.fillMaxWidth().padding(top = 9.dp),
            shape = RoundedCornerShape(17.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFEEE6E8))
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("งานพ่วง ${active.size} ออเดอร์", fontSize = 13.sp, fontWeight = FontWeight.Black)
                        Text(
                            "รายได้รวม ฿%.0f".format(totalIncome) +
                                if (totalSavings > 0.0) " · ลูกค้าประหยัดรวม ฿%.0f".format(totalSavings) else "",
                            color = QgMuted,
                            fontSize = 8.sp
                        )
                    }
                    Text("เส้นทางเดียวกัน", color = QgRed, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                }
                active.forEach { order ->
                    HorizontalDivider(color = Color(0xFFF0EAEC), modifier = Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                order.job.numberLabel + " · " + order.shopName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "รับร้าน ${order.bundleSequence} · ส่งลูกค้าลำดับ ${order.dropRank} · ค่าส่ง ฿" +
                                    "%.0f".format(order.job.deliveryFee ?: 0.0) +
                                    if (order.customerSavings > 0.0) " · ลูกค้าประหยัด ฿%.0f".format(order.customerSavings) else "",
                                color = QgMuted,
                                fontSize = 8.sp
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        BundleStatus(order.job.status, order.dropRank)
                    }
                }
            }
        }
    }

    if (offers.isNotEmpty()) {
        Card(
            Modifier.fillMaxWidth().padding(top = 9.dp),
            shape = RoundedCornerShape(17.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFEEE6E8))
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("มีงานพ่วงในเส้นทาง", fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text("เลือกเองได้ ไม่บังคับรับ", color = QgMuted, fontSize = 8.sp)
                offers.forEach { offer ->
                    HorizontalDivider(color = Color(0xFFF0EAEC), modifier = Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(offer.shopName + " · " + offer.numberLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "เพิ่ม %.1f กม. · ประมาณ %d นาที · รายรับเพิ่ม ฿%.0f · ลูกค้าประหยัด ฿%.0f".format(
                                    offer.detourKm,
                                    offer.addedMinutes,
                                    offer.riderExtraFee,
                                    offer.customerSavings
                                ),
                                color = QgMuted,
                                fontSize = 8.sp,
                                lineHeight = 11.sp
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onClaim(offer) },
                            enabled = !busy,
                            contentPadding = PaddingValues(horizontal = 11.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = QgRedSoft, contentColor = QgRed)
                        ) {
                            Text("รับงานพ่วง", fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BundleStatus(status: String, dropRank: Int) {
    val (label, active) = when (status) {
        "ready" -> "พร้อมรับสินค้า" to true
        "picked_up" -> "รับของแล้ว" to false
        "in_progress" -> "ส่งลำดับ $dropRank" to true
        "completed" -> "ส่งสำเร็จ" to false
        "cancelled" -> "ยกเลิก" to false
        else -> "รอร้าน" to false
    }
    Text(
        label,
        color = if (active) QgRed else QgMuted,
        fontSize = 8.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.background(if (active) QgRedSoft else Color(0xFFF7F3F4), RoundedCornerShape(99.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp)
    )
}
