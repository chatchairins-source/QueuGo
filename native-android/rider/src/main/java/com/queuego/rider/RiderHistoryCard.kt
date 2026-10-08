package com.queuego.rider

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.QgCard
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed

@Composable
fun RiderRecentHistoryCard(history: List<RiderHistoryOrder>) {
    QgCard(Modifier.fillMaxWidth()) {
        Column {
            Text("งานล่าสุด", fontWeight = FontWeight.Black)
            Text("ประวัติงานที่ส่งสำเร็จจาก QueueGo Production", color = QgMuted)
            Spacer(Modifier.height(8.dp))
            if (history.isEmpty()) {
                Text("ยังไม่มีประวัติงานสำเร็จ", color = QgMuted)
            } else {
                history.take(5).forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(item.number, fontWeight = FontWeight.ExtraBold)
                            val route = listOfNotNull(item.pickupAddress, item.deliveryAddress)
                                .joinToString(" → ")
                            if (route.isNotBlank()) {
                                Text(route, color = QgMuted, maxLines = 2)
                            }
                        }
                        Text(
                            "฿" + "%.0f".format(item.deliveryFee),
                            color = QgRed,
                            fontWeight = FontWeight.Black
                        )
                    }
                    if (index < history.take(5).lastIndex) HorizontalDivider()
                }
            }
        }
    }
}
