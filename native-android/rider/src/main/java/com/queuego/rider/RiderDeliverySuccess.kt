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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    auth: QueueGoAuth,
    api: QueueGoApi,
    job: RiderJob,
    recommendation: RiderAfterWorkRecommendation?,
    onRecommendation: (RiderAfterWorkRecommendation) -> Unit,
    onDismiss: () -> Unit
) {
    val eventFeedbackView = LocalView.current
    var ratingSummary by remember(job.id) { mutableStateOf<RiderRatingSummary?>(null) }
    LaunchedEffect(job.id) {
        playRiderEventFeedback(eventFeedbackView, RiderEventFeedbackKind.DELIVERY_SUCCESS)
        ratingSummary = runCatching { api.ratingSummary(auth) }.getOrNull()
    }
    LaunchedEffect(job.id, recommendation?.key) {
        if (recommendation == null) {
            delay(4_500)
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
                    "รายได้สุทธิ ฿" + riderMoneyNumber(job.deliveryFee ?: 0.0),
                    color = QgRed,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                ratingSummary?.let { rating ->
                    Spacer(Modifier.height(12.dp))
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E8))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("คะแนนจากลูกค้า", fontSize = 10.sp, color = QgMuted, fontWeight = FontWeight.Bold)
                                Text(
                                    if (rating.average != null) String.format(java.util.Locale.US, "%.2f / 5", rating.average)
                                    else "ยังไม่มีคะแนน",
                                    modifier = Modifier.padding(top = 2.dp),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Text(
                                if (rating.count > 0) rating.count.toString() + " รีวิว" else "ยังไม่มีรีวิว",
                                fontSize = 10.sp,
                                color = QgMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                recommendation?.let { item ->
                    Spacer(Modifier.height(14.dp))
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3F5))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(item.title, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            Text(
                                item.detail,
                                color = QgMuted,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            Button(
                                onClick = { onRecommendation(item) },
                                modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(42.dp),
                                shape = RoundedCornerShape(13.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF04455))
                            ) {
                                Text(item.actionLabel, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }
                        }
                    }
                }
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
