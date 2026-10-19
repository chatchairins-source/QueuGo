package com.queuego.rider

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val RIDER_AUTO_ACCEPT_MIN_SECONDS_LEFT = 3

internal class RiderAcceptanceModeStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("rider_acceptance_mode_v1", Context.MODE_PRIVATE)

    private fun key(userId: String) = "auto_accept:" + userId

    suspend fun autoAcceptEnabled(userId: String): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(key(userId), false)
    }

    suspend fun setAutoAcceptEnabled(userId: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(key(userId), enabled).apply()
    }
}

internal fun riderAutoAcceptEligible(
    enabled: Boolean,
    networkAvailable: Boolean,
    online: Boolean,
    hasActiveWork: Boolean,
    actionBusy: Boolean,
    secondsLeft: Int
): Boolean =
    enabled &&
        networkAvailable &&
        online &&
        !hasActiveWork &&
        !actionBusy &&
        secondsLeft >= RIDER_AUTO_ACCEPT_MIN_SECONDS_LEFT

@Composable
internal fun RiderAcceptanceModeControl(
    autoAcceptEnabled: Boolean,
    enabled: Boolean,
    onAutoAcceptChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "รับงานอัตโนมัติ",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = Color(0xFF211E20)
                )
                Text(
                    if (autoAcceptEnabled) {
                        "อัตโนมัติ · รับเฉพาะงานที่ QueueGo ส่งตรงถึงคุณ"
                    } else {
                        "เลือกเอง · ดูรายละเอียดแล้วกดรับภายใน 30 วินาที"
                    },
                    color = QgMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (autoAcceptEnabled) {
                    Text(
                        "งานพ่วงและงานฝากซักยังต้องกดรับเอง",
                        color = QgRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Switch(
                checked = autoAcceptEnabled,
                onCheckedChange = onAutoAcceptChanged,
                enabled = enabled,
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = QgRed)
            )
        }
    }
}
