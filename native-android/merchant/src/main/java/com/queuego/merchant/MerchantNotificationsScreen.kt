package com.queuego.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgIcon
import com.queuego.shared.QgLine
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun merchantNotificationGroup(notification: MerchantNotification): String {
    val type = notification.type.lowercase()
    val title = notification.title.lowercase()
    return when {
        listOf("promo", "campaign", "discount").any { it in type } ||
            "โปรโม" in title || "promotion" in title -> "promo"
        listOf("order", "rider", "delivery", "pickup").any { it in type } ||
            listOf("ออเดอร์", "คำสั่งซื้อ", "ไรเดอร์", "order").any { it in title } -> "orders"
        else -> "system"
    }
}

@Composable
internal fun MerchantNotificationsScreen(
    notifications: List<MerchantNotification>,
    onBack: () -> Unit,
    onMarkAllRead: () -> Unit,
    onTestSound: () -> Unit,
    onOpenOrder: (String) -> Unit
) {
    var filter by remember { mutableStateOf("all") }
    LaunchedEffect(Unit) { onMarkAllRead() }

    val counts = remember(notifications) {
        mapOf(
            "all" to notifications.size,
            "orders" to notifications.count { merchantNotificationGroup(it) == "orders" },
            "system" to notifications.count { merchantNotificationGroup(it) == "system" },
            "promo" to notifications.count { merchantNotificationGroup(it) == "promo" }
        )
    }
    val visible = remember(notifications, filter) {
        if (filter == "all") notifications
        else notifications.filter { merchantNotificationGroup(it) == filter }
    }
    val grouped = remember(visible) {
        visible.groupBy { merchantNotificationDayKey(it.createdAt) }
            .toList()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier.padding(top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.height(38.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) { Text("กลับ") }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "การแจ้งเตือน",
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "ติดตามออเดอร์ สถานะระบบ และข่าวสารของร้านในที่เดียว",
                    color = QgMuted,
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(15.dp))
                .border(1.dp, Color(0xFFE8EAED), RoundedCornerShape(15.dp))
                .clickable(onClick = onTestSound)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(Color(0xFFFFF0F3), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                QgIcon("bell", Modifier.size(19.dp), QgRed)
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("เสียงแจ้งเตือน", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "เปิดเสียงและทดสอบการแจ้งเตือนของร้าน",
                    color = QgMuted,
                    fontSize = 9.sp
                )
            }
            Text("ทดสอบเสียง", color = QgRed, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NotificationFilter("ทั้งหมด", "all", counts["all"] ?: 0, filter) { filter = it }
            NotificationFilter("ออเดอร์", "orders", counts["orders"] ?: 0, filter) { filter = it }
            NotificationFilter("ระบบ", "system", counts["system"] ?: 0, filter) { filter = it }
            NotificationFilter("โปรโมชัน", "promo", counts["promo"] ?: 0, filter) { filter = it }
        }

        if (visible.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE8EAED), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(Color(0xFFF5F6F7), CircleShape),
                    contentAlignment = Alignment.Center
                ) { QgIcon("bell", Modifier.size(23.dp), Color(0xFF858A92)) }
                Spacer(Modifier.height(9.dp))
                Text("ยังไม่มีการแจ้งเตือนในหมวดนี้", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                Text(
                    "รายการใหม่จากออเดอร์ ระบบ และโปรโมชันจะแสดงที่นี่",
                    color = QgMuted,
                    fontSize = 9.sp
                )
            }
        } else {
            grouped.forEach { (key, rows) ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 14.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        merchantNotificationDayLabel(key),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text("${rows.size} รายการ", color = QgMuted, fontSize = 9.sp)
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE8EAED), RoundedCornerShape(16.dp))
                ) {
                    rows.forEachIndexed { index, notification ->
                        MerchantNotificationRow(
                            notification = notification,
                            onClick = {
                                val ref = notification.referenceId
                                if (ref != null && notification.type.equals("order", true)) onOpenOrder(ref)
                            }
                        )
                        if (index != rows.lastIndex) {
                            Spacer(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(QgLine)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun NotificationFilter(
    label: String,
    key: String,
    count: Int,
    selected: String,
    onSelect: (String) -> Unit
) {
    val active = selected == key
    Row(
        Modifier
            .background(if (active) Color(0xFFFFF0F3) else Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, if (active) QgRed else Color(0xFFE4E6E9), RoundedCornerShape(18.dp))
            .clickable { onSelect(key) }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (active) Color(0xFFD90D2D) else Color(0xFF747A82),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(4.dp))
        Text(
            count.toString(),
            color = if (active) QgRed else Color(0xFF9A9EA5),
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun MerchantNotificationRow(
    notification: MerchantNotification,
    onClick: () -> Unit
) {
    val kind = merchantNotificationGroup(notification)
    val color = when (kind) {
        "orders" -> QgRed
        "promo" -> Color(0xFF7356C8)
        else -> Color(0xFF268FC5)
    }
    val icon = when (kind) {
        "orders" -> "orders"
        "promo" -> "tag"
        else -> "bell"
    }
    val canOpen = notification.referenceId != null && notification.type.equals("order", true)

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = canOpen, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(color.copy(alpha = 0.10f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            QgIcon(icon, Modifier.size(18.dp), color)
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    notification.title.ifBlank { "การแจ้งเตือน" },
                    modifier = Modifier.weight(1f),
                    fontSize = 11.5.sp,
                    fontWeight = if (notification.isRead) FontWeight.Bold else FontWeight.ExtraBold,
                    maxLines = 1
                )
                Text(
                    merchantNotificationTime(notification.createdAt),
                    color = QgMuted,
                    fontSize = 8.5.sp
                )
            }
            if (notification.message.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    notification.message,
                    color = QgMuted,
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp,
                    maxLines = 3
                )
            }
        }
        if (canOpen) {
            Spacer(Modifier.width(6.dp))
            Text("›", color = Color(0xFFB3B7BE), fontSize = 19.sp)
        }
    }
}

private val merchantNotificationZone = ZoneId.of("Asia/Bangkok")

private fun merchantNotificationInstant(value: String?): Instant? {
    val raw = value.orEmpty().trim()
    if (raw.isBlank()) return null
    return runCatching { Instant.parse(raw) }.getOrElse {
        runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
    }
}

internal fun merchantNotificationDayKey(value: String?): String {
    val instant = merchantNotificationInstant(value) ?: return "unknown"
    return instant.atZone(merchantNotificationZone).toLocalDate().toString()
}

internal fun merchantNotificationDayLabel(key: String): String {
    val day = runCatching { LocalDate.parse(key) }.getOrNull() ?: return "ก่อนหน้านี้"
    val today = LocalDate.now(merchantNotificationZone)
    return when (day) {
        today -> "วันนี้"
        today.minusDays(1) -> "เมื่อวาน"
        else -> day.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("th", "TH")))
    }
}

internal fun merchantNotificationTime(value: String?): String {
    val instant = merchantNotificationInstant(value) ?: return ""
    return instant.atZone(merchantNotificationZone)
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale("th", "TH")))
}
