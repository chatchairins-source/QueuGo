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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
            Modifier.padding(top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .shadow(3.dp, RoundedCornerShape(14.dp), clip = false)
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(14.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                QgIcon("back", Modifier.size(20.dp), Color(0xFF222222))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "การแจ้งเตือน",
                    color = Color(0xFF17191D),
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "ติดตามออเดอร์ สถานะระบบ และข่าวสารของร้านในที่เดียว",
                    color = Color(0xFF8A9099),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .shadow(5.dp, RoundedCornerShape(18.dp), clip = false)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.White, Color(0xFFFFF7F8))
                    ),
                    RoundedCornerShape(18.dp)
                )
                .border(1.dp, Color(0xFFF0D8DC), RoundedCornerShape(18.dp))
                .clickable(onClick = onTestSound)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(Color(0xFFFFF0F3), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                QgIcon("bell", Modifier.size(22.dp), Color(0xFFE51D3D))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("เสียงแจ้งเตือน", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(3.dp))
                Text(
                    "เปิดเสียงและทดสอบการแจ้งเตือนของร้าน",
                    color = Color(0xFF888F98),
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
            Text(
                "ทดสอบเสียง",
                color = Color(0xFFD91C38),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F6F8), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFFE8EAEE), RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NotificationFilter(Modifier.weight(1f), "ทั้งหมด", "all", counts["all"] ?: 0, filter) { filter = it }
            NotificationFilter(Modifier.weight(1f), "ออเดอร์", "orders", counts["orders"] ?: 0, filter) { filter = it }
            NotificationFilter(Modifier.weight(1f), "ระบบ", "system", counts["system"] ?: 0, filter) { filter = it }
            NotificationFilter(Modifier.weight(1f), "โปรโมชัน", "promo", counts["promo"] ?: 0, filter) { filter = it }
        }

        if (visible.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .background(Color(0xFFFAFBFC), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFFDFE2E7), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .background(Color(0xFFFFF0F3), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) { QgIcon("bell", Modifier.size(25.dp), Color(0xFFE12A45)) }
                Spacer(Modifier.height(12.dp))
                Text(
                    "ยังไม่มีการแจ้งเตือนในหมวดนี้",
                    color = Color(0xFF3F444C),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "รายการใหม่จากออเดอร์ ระบบ และโปรโมชันจะแสดงที่นี่",
                    color = Color(0xFF8A9099),
                    fontSize = 10.sp
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
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rows.forEach { notification ->
                        MerchantNotificationRow(
                            notification = notification,
                            onClick = {
                                val ref = notification.referenceId
                                if (ref != null && notification.type.equals("order", true)) onOpenOrder(ref)
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun NotificationFilter(
    modifier: Modifier = Modifier,
    label: String,
    key: String,
    count: Int,
    selected: String,
    onSelect: (String) -> Unit
) {
    val active = selected == key
    Row(
        modifier
            .height(38.dp)
            .background(
                if (active) Color.White else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable { onSelect(key) }
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            color = if (active) Color(0xFF191B1F) else Color(0xFF7E858F),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(5.dp))
        Box(
            Modifier
                .height(18.dp)
                .background(
                    if (active) Color(0xFFFFF0F3) else Color(0xFFE7E9ED),
                    CircleShape
                )
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                count.toString(),
                color = if (active) Color(0xFFD91C38) else Color(0xFF717782),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun MerchantNotificationRow(
    notification: MerchantNotification,
    onClick: () -> Unit
) {
    val kind = merchantNotificationGroup(notification)
    val color = when (kind) {
        "orders" -> Color(0xFF2F80ED)
        "promo" -> Color(0xFFDF941F)
        else -> Color(0xFF7057D8)
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
            .shadow(3.dp, RoundedCornerShape(17.dp), clip = false)
            .background(Color.White, RoundedCornerShape(17.dp))
            .border(1.dp, Color(0xFFE9EBEF), RoundedCornerShape(17.dp))
            .clickable(enabled = canOpen, onClick = onClick)
            .padding(start = 13.dp, end = 13.dp, top = 13.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(
                    when (kind) {
                        "orders" -> Color(0xFFEEF6FF)
                        "promo" -> Color(0xFFFFF6E9)
                        else -> Color(0xFFF2F0FF)
                    },
                    RoundedCornerShape(13.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            QgIcon(icon, Modifier.size(21.dp), color)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    notification.title.ifBlank { "การแจ้งเตือน" },
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontWeight = if (notification.isRead) FontWeight.Bold else FontWeight.ExtraBold,
                    maxLines = 1
                )
                Text(
                    merchantNotificationTime(notification.createdAt),
                    color = QgMuted,
                    fontSize = 9.sp
                )
            }
            if (notification.message.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    notification.message,
                    color = QgMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    maxLines = 3
                )
            }
        }
        if (canOpen) {
            Spacer(Modifier.width(6.dp))
            Text("›", color = Color(0xFFB1B5BC), fontSize = 17.sp)
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
