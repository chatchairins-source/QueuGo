package com.queuego.rider

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/** Cache of the server-acknowledged auto_accept bit for background navigation readiness only. */
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

internal fun riderWorkServicesAfterToggle(
    current: Set<String>,
    service: String,
    enabled: Boolean
): Set<String>? {
    if (service !in riderWorkServiceKeys) return null
    val next = if (enabled) current + service else current - service
    return next.takeIf { it.isNotEmpty() }
}

private fun RiderWorkCapabilities.available(service: String): Boolean = when (service) {
    "food" -> food
    "grocery" -> grocery
    "market" -> market
    "express" -> express
    "ride" -> ride
    else -> false
}

private val riderWorkServiceLabels = listOf(
    "food" to "อาหารและเครื่องดื่ม",
    "grocery" to "ร้านขายของชำ",
    "market" to "ตลาดสด",
    "express" to "ส่งพัสดุ Express",
    "ride" to "รับส่งผู้โดยสาร"
)

@Composable
internal fun RiderWorkPreferencesControl(
    preferences: RiderWorkPreferences?,
    busy: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onChange: (autoAccept: Boolean, bundleEnabled: Boolean, services: Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        if (preferences == null) {
            if (error != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "โหลดการรับงานไม่ได้",
                            color = Color(0xFF211E20),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            error,
                            color = QgMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    TextButton(onClick = onRetry, enabled = !busy) {
                        Text("ลองใหม่", color = QgRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
            return@Column
        }

        RiderWorkToggleRow(
            title = "รับงานอัตโนมัติ",
            detail = "รับงานประเภทที่เลือกขณะออนไลน์และเปิดหน้านี้",
            checked = preferences.autoAccept,
            enabled = !busy
        ) { enabled ->
            onChange(enabled, preferences.bundleEnabled, preferences.services)
        }
        HorizontalDivider(color = Color(0xFFF0EAEC))
        RiderWorkToggleRow(
            title = "รับงานพ่วง",
            detail = if (preferences.capabilities.bundle) {
                "แสดงงานพ่วงให้เลือก พร้อมรายได้และระยะทางเพิ่ม"
            } else {
                "บันทึกความต้องการไว้ได้ · บริการงานพ่วงยังไม่เปิดใช้งาน"
            },
            checked = preferences.bundleEnabled,
            enabled = !busy
        ) { enabled ->
            onChange(preferences.autoAccept, enabled, preferences.services)
        }

        Text(
            "ประเภทบริการที่รับ",
            color = Color(0xFF777177),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
        )
        riderWorkServiceLabels.forEachIndexed { index, (service, label) ->
            if (index > 0) HorizontalDivider(color = Color(0xFFF4EFF1))
            val available = preferences.capabilities.available(service)
            val checked = service in preferences.services
            RiderWorkToggleRow(
                title = label,
                detail = when {
                    available -> "เลือกเปิดหรือปิดรับงานได้"
                    service == "ride" -> "ยังไม่ได้รับสิทธิ์รับส่งผู้โดยสาร กรุณาติดต่อทีมงาน"
                    else -> "บริการนี้ยังไม่เปิดใช้งาน"
                },
                checked = checked,
                enabled = !busy && available && !(checked && preferences.services.size == 1)
            ) { enabled ->
                riderWorkServicesAfterToggle(preferences.services, service, enabled)?.let { next ->
                    onChange(preferences.autoAccept, preferences.bundleEnabled, next)
                }
            }
        }
    }
}

@Composable
private fun RiderWorkToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) Color(0xFF211E20) else Color(0xFF9A9397),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                detail,
                color = QgMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = QgRed)
        )
    }
}
