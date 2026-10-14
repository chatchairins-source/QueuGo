package com.queuego.rider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class RiderConnectivityNoticeModel(
    val title: String,
    val detail: String
)

internal fun riderConnectivityNotice(
    consecutiveRefreshFailures: Int,
    hasSnapshot: Boolean,
    hasActiveWork: Boolean
): RiderConnectivityNoticeModel? {
    if (!hasSnapshot || consecutiveRefreshFailures < 2) return null
    return RiderConnectivityNoticeModel(
        title = "สัญญาณขัดข้อง",
        detail = if (hasActiveWork) {
            "งานยังอยู่ในเครื่อง · กำลังเชื่อมต่อใหม่"
        } else {
            "กำลังเชื่อมต่อ QueueGo ใหม่"
        }
    )
}

@Composable
internal fun RiderConnectivityNotice(
    model: RiderConnectivityNoticeModel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8EC)),
        border = BorderStroke(1.dp, Color(0xFFE9C98E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                Modifier
                    .size(9.dp)
                    .background(Color(0xFFE49A24), RoundedCornerShape(99.dp))
            ) {}
            Column(Modifier.weight(1f)) {
                Text(
                    model.title,
                    color = Color(0xFF4A3517),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    model.detail,
                    color = Color(0xFF765A2D),
                    fontSize = 10.sp,
                    maxLines = 2
                )
            }
        }
    }
}
