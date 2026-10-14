package com.queuego.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import kotlinx.coroutines.delay

@Composable
internal fun RiderDeliverySuccess(
    job: RiderJob,
    recommendation: RiderAfterJobRecommendation?,
    onNavigateRecommendation: (RiderAfterJobRecommendation) -> Unit,
    onDismiss: () -> Unit
) {
    val eventFeedbackView = LocalView.current
    LaunchedEffect(job.id) {
        playRiderEventFeedback(eventFeedbackView, RiderEventFeedbackKind.DELIVERY_SUCCESS)
    }
    LaunchedEffect(job.id, recommendation?.zone?.latitude, recommendation?.zone?.longitude) {
        if (recommendation == null) {
            delay(7_000)
            onDismiss()
        }
    }
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            Modifier.padding(24.dp).widthIn(max = 360.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(54.dp).background(Color(0xFFFFEEF2), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = QgRed, fontSize = 30.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(12.dp))
                Text("จัดส่งสำเร็จ", fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(job.numberLabel, color = QgMuted, fontSize = 10.sp)
                Spacer(Modifier.height(9.dp))
                Text(
                    "รายได้ค่าส่ง ฿" + riderMoneyNumber(job.deliveryFee ?: 0.0),
                    color = QgRed,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
                recommendation?.let { next ->
                    Spacer(Modifier.height(14.dp))
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6F8))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(13.dp)) {
                            Text("แนะนำพื้นที่งานถัดไป", color = QgMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(
                                riderAfterJobLevelLabel(next.zone.level),
                                color = QgRed,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            Text(
                                riderAfterJobDistanceLabel(next.distanceKm) +
                                    " · ข้อมูลพื้นที่งาน " + next.zone.windowHours + " ชม.",
                                color = Color(0xFF5F575B),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            Text(
                                "เป็นคำแนะนำจากข้อมูลจริง ไม่รับประกันว่าจะมีงาน และไม่กระทบการรับงานแบบปกติ",
                                color = QgMuted,
                                fontSize = 8.sp,
                                lineHeight = 11.sp,
                                modifier = Modifier.padding(top = 5.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onNavigateRecommendation(next) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF04455))
                    ) {
                        Text("นำทางไปพื้นที่งานถัดไป", fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(7.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Text("กลับหน้าหลัก", fontWeight = FontWeight.Bold)
                    }
                } ?: run {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF04455))
                    ) {
                        Text("รับทราบ", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
