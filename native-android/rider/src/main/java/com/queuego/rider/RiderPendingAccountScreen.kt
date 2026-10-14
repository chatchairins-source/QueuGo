package com.queuego.rider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class RiderAccountStatusCopy(
    val badge: String,
    val title: String,
    val description: String,
    val showReason: Boolean
)

internal fun riderAccountStatusCopy(user: QueueGoUser): RiderAccountStatusCopy = when (user.status) {
    "pending" -> RiderAccountStatusCopy(
        badge = "รอตรวจสอบ",
        title = "ใบสมัครของคุณอยู่ระหว่างตรวจสอบ",
        description = "ใบสมัครถูกบันทึกแล้ว เมื่อ Admin อนุมัติ คุณจะสามารถเปิดรับงานได้ทันที",
        showReason = false
    )
    "rejected" -> RiderAccountStatusCopy(
        badge = "ใบสมัครไม่ผ่าน",
        title = "ยังไม่สามารถเปิดรับงานได้",
        description = "Admin ตรวจใบสมัครแล้ว กรุณาตรวจข้อมูลที่ได้รับจาก QueueGo ก่อนส่งข้อมูลใหม่หรือติดต่อฝ่ายดูแล",
        showReason = !user.suspensionReason.isNullOrBlank()
    )
    "suspended" -> RiderAccountStatusCopy(
        badge = "บัญชีถูกพักสิทธิ์",
        title = "หยุดรับงานชั่วคราว",
        description = "บัญชีนี้ยังเข้าสู่ระบบได้ แต่ไม่สามารถเปิดรับงานจนกว่าสถานะจะถูกปลดพัก",
        showReason = true
    )
    "deleted" -> RiderAccountStatusCopy(
        badge = "บัญชีถูกปิด",
        title = "บัญชีนี้ไม่พร้อมใช้งาน",
        description = "บัญชีถูกปิดในระบบ QueueGo และไม่สามารถเปิดรับงานได้",
        showReason = false
    )
    else -> RiderAccountStatusCopy(
        badge = "ตรวจสอบสถานะบัญชี",
        title = "บัญชียังไม่พร้อมรับงาน",
        description = "กรุณาตรวจสถานะอีกครั้ง หรือติดต่อฝ่ายดูแล QueueGo หากสถานะไม่ถูกต้อง",
        showReason = !user.suspensionReason.isNullOrBlank()
    )
}

internal fun riderAccountStatusTimeLabel(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) } ?: return null
    val instant = runCatching { Instant.parse(value) }
        .getOrElse {
            runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull() ?: return null
        }
    return DateTimeFormatter
        .ofPattern("d MMM yyyy · HH:mm", Locale("th", "TH"))
        .withZone(ZoneId.of("Asia/Bangkok"))
        .format(instant)
}

@Composable
internal fun RiderAccountStatusScreen(
    modifier: Modifier,
    user: QueueGoUser,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    val copy = riderAccountStatusCopy(user)
    RiderAccountCard(
        modifier = modifier,
        badge = copy.badge,
        title = copy.title,
        description = copy.description,
        reason = user.suspensionReason?.takeIf { copy.showReason && it.isNotBlank() },
        statusTime = riderAccountStatusTimeLabel(user.suspendedAt),
        primaryButton = if (refreshing) "กำลังตรวจสถานะ…" else "ตรวจสถานะอีกครั้ง",
        primaryEnabled = !refreshing,
        onPrimary = onRefresh,
        secondaryButton = "ออกจากระบบ",
        onSecondary = onLogout
    )
}

@Composable
internal fun RiderConnectionRecoveryScreen(modifier: Modifier, onRetry: () -> Unit) {
    RiderAccountCard(
        modifier = modifier,
        badge = null,
        title = "เชื่อมต่อไม่ได้",
        description = "บัญชียังอยู่ในเครื่อง กรุณาต่ออินเทอร์เน็ตแล้วลองใหม่",
        reason = null,
        statusTime = null,
        primaryButton = "ลองใหม่",
        primaryEnabled = true,
        onPrimary = onRetry,
        secondaryButton = null,
        onSecondary = {}
    )
}

@Composable
private fun RiderAccountCard(
    modifier: Modifier,
    badge: String?,
    title: String,
    description: String,
    reason: String?,
    statusTime: String?,
    primaryButton: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    secondaryButton: String?,
    onSecondary: () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize().drawWithCache {
        val direction = Offset(.34202015f, .9396926f)
        val length = size.width * direction.x + size.height * direction.y
        val center = Offset(size.width / 2f, size.height / 2f)
        val brush = Brush.linearGradient(
            listOf(Color.White, Color(0xFFFFF1F4)),
            start = center - direction * (length / 2f),
            end = center + direction * (length / 2f)
        )
        onDrawBehind { drawRect(brush) }
    }) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .heightIn(min = (maxHeight - 40.dp).coerceAtLeast(0.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                Modifier
                    .widthIn(max = 430.dp)
                    .fillMaxWidth()
                    .drawWithCache {
                        val sigma = 46.dp.toPx() / 2f
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.WHITE
                            setShadowLayer(
                                (sigma - .5f) / .57735f,
                                0f,
                                18.dp.toPx(),
                                android.graphics.Color.argb(31, 58, 19, 30)
                            )
                        }
                        val radius = 28.dp.toPx()
                        onDrawBehind {
                            drawIntoCanvas {
                                it.nativeCanvas.drawRoundRect(
                                    0f, 0f, size.width, size.height, radius, radius, paint
                                )
                            }
                        }
                    },
                shape = RoundedCornerShape(28.dp),
                color = Color.White
            ) {
                Column(
                    Modifier.padding(30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFE6002D)) {
                        Box(Modifier.size(62.dp), contentAlignment = Alignment.Center) {
                            Text("Q", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    if (badge != null) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            badge,
                            color = Color(0xFFE6002D),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .background(Color(0xFFFFF0F3), RoundedCornerShape(999.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = Color(0xFF17171B),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        description,
                        fontSize = 12.sp,
                        lineHeight = 19.2.sp,
                        color = Color(0xFF77747B),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    if (!reason.isNullOrBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFFF6F7),
                            border = BorderStroke(1.dp, Color(0xFFFFD6DC))
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "เหตุผลจาก QueueGo",
                                    color = Color(0xFFE6002D),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    reason,
                                    color = Color(0xFF4D4448),
                                    fontSize = 11.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(top = 5.dp)
                                )
                                statusTime?.let {
                                    Text(
                                        "อัปเดตสถานะ $it",
                                        color = Color(0xFF8A8387),
                                        fontSize = 8.5.sp,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = onPrimary,
                        enabled = primaryEnabled,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE6002D))
                    ) {
                        Text(primaryButton, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                    secondaryButton?.let {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onSecondary,
                            enabled = !refreshingIfKnown(primaryEnabled, primaryButton),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color(0xFFF3C7D1)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE6002D))
                        ) {
                            Text(it, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

private fun refreshingIfKnown(primaryEnabled: Boolean, primaryButton: String): Boolean =
    !primaryEnabled && primaryButton.contains("กำลัง")
