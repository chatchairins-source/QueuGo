package com.queuego.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgLine
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRemoteImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun CustomerOrderTrackingScreen(
    auth: NativeAuth,
    order: CustomerOrder?,
    items: List<CustomerOrderItem>,
    context: CustomerOrderContext?,
    shopCategory: String?,
    busy: Boolean,
    onCallShop: () -> Unit,
    onCallRider: () -> Unit,
    onChat: () -> Unit,
    onSupport: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit
) {
    val compact = LocalConfiguration.current.screenWidthDp <= 420

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 10.dp)
    ) {
        if (order == null) {
            CustomerTrackingHeader("ออเดอร์", onBack)
            Text(
                "ไม่พบออเดอร์",
                modifier = Modifier.fillMaxWidth().padding(vertical = 35.dp),
                color = QgMuted,
                textAlign = TextAlign.Center
            )
            return@Column
        }

        CustomerTrackingHeader(order.number, onBack)

        val visibleStatus = if (
            order.status.equals("in_progress", true) &&
            !order.riderArrivedCustomerAt.isNullOrBlank()
        ) "arrived" else order.status

        val stage = customerOrderStage(order.status)
        val terminalFailure = order.status.lowercase() in setOf("cancelled", "no_rider_available")
        val closed = order.trackingClosed()

        Box(
            Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(20.dp),
                    clip = false,
                    ambientColor = Color(0x141E181B),
                    spotColor = Color(0x141E181B)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color.White, Color(0xFFFFF7F8), Color(0xFFFFF0F2))
                    )
                )
                .padding(if (compact) 12.dp else 14.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 52.dp, y = (-58).dp)
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(Color(0x12E6002D))
            )
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "สถานะล่าสุด",
                            color = Color(0xFF8D8589),
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            customerTrackingStatusLabel(visibleStatus),
                            color = Color(0xFF17191D),
                            fontSize = if (compact) 19.sp else 20.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        customerTrackingTime(order, visibleStatus),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color(0xFFEEE8EA), CircleShape)
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        color = Color(0xFF8A8387),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (!terminalFailure) {
                    Spacer(Modifier.height(11.dp))
                    CustomerTrackingProgress(stage)
                }

                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xE6FFFFFF))
                        .border(1.dp, Color(0xFFF0E8EB), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Text(
                        "ส่งไปที่",
                        color = Color(0xFF91898D),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        order.deliveryAddress ?: "ยังไม่มีที่อยู่จัดส่ง",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(5.dp))
                    val shopLine = buildString {
                        append("ร้าน ")
                        append(context?.shop?.name ?: "ร้านค้า")
                        val note = order.note?.trim().orEmpty()
                        if (note.isNotBlank()) append(" · ").append(note)
                    }
                    Text(
                        shopLine,
                        color = Color(0xFF716A6E),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (
                        order.status.lowercase() !in setOf("pending", "searching_rider") &&
                        context?.shop != null
                    ) {
                        CustomerTrackingAction(
                            label = "ติดต่อร้าน",
                            modifier = Modifier.weight(1f),
                            onClick = onCallShop
                        )
                    }
                    if (
                        order.riderId.isNullOrBlank() &&
                        order.status.lowercase() in setOf("pending", "searching_rider")
                    ) {
                        CustomerTrackingAction(
                            label = if (busy) "กำลังยกเลิก..." else "ยกเลิกคำสั่งซื้อ",
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                            danger = true,
                            onClick = onCancel
                        )
                    }
                    CustomerTrackingAction(
                        label = "แจ้งปัญหา",
                        modifier = Modifier.weight(1f),
                        onClick = onSupport
                    )
                }
            }
        }

        if (order.status.equals("searching_rider", true)) {
            Spacer(Modifier.height(8.dp))
            CustomerSearchingRiderCard()
        }

        context?.rider?.let { rider ->
            Spacer(Modifier.height(8.dp))
            CustomerTrackingCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (order.status.equals("completed", true)) "ผู้จัดส่ง" else "Rider ของคุณ",
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        if (order.status.equals("completed", true)) "จัดส่งสำเร็จ"
                        else if (order.status.equals("cancelled", true)) "ออเดอร์ยกเลิกแล้ว"
                        else "กำลังดูแลออเดอร์นี้",
                        color = Color(0xFF91898D),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QgRemoteImage(
                        source = rider.photo,
                        modifier = Modifier.size(46.dp),
                        fallback = rider.name,
                        cornerRadius = 14.dp
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            rider.name,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(3.dp))
                        val vehicle = listOfNotNull(rider.vehicleType, rider.vehiclePlate)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                            .ifBlank { "ข้อมูลรถกำลังอัปเดต" }
                        Text(vehicle, color = Color(0xFF898185), fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!closed) {
                        CustomerTrackingAction(
                            label = "โทรหา Rider",
                            modifier = Modifier.weight(1f),
                            onClick = onCallRider
                        )
                    }
                    if (order.chatAvailable()) {
                        CustomerTrackingAction(
                            label = if (order.status.equals("completed", true))
                                "แชทกับ Rider (ภายใน 30 นาที)"
                            else "แชทกับ Rider",
                            modifier = Modifier.weight(1f),
                            filled = true,
                            onClick = onChat
                        )
                    } else if (order.status.equals("completed", true)) {
                        Text(
                            "แชทปิดแล้วหลังจบงาน 30 นาที",
                            modifier = Modifier.weight(1f),
                            color = QgMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        if (!closed) {
            Spacer(Modifier.height(8.dp))
            CustomerTrackingMap(order, context?.rider)
        }

        CustomerTrackingCard {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "รายการสินค้า",
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    items.sumOf { it.quantity }.toString() + " ชิ้น",
                    color = Color(0xFF91898D),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.height(5.dp))
            if (items.isEmpty()) {
                Text("กำลังโหลดรายการ...", color = QgMuted, fontSize = 11.sp)
            } else {
                items.forEachIndexed { index, item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.name,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (!item.optionSummary.isNullOrBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    item.optionSummary!!,
                                    color = QgMuted,
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp,
                                    maxLines = 2
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "จำนวน " + item.quantity,
                                color = Color(0xFF938B8F),
                                fontSize = 9.sp
                            )
                        }
                        Text(
                            "฿" + "%.0f".format(item.totalPrice),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    if (index < items.lastIndex) {
                        HorizontalDivider(color = Color(0xFFF1EDEF))
                    }
                }
            }

            Spacer(Modifier.height(2.dp))
            HorizontalDivider(color = Color(0xFFEEE8EA))
            Spacer(Modifier.height(6.dp))
            CustomerTrackingSummaryRow("ค่าสินค้า", order.subtotal)
            CustomerTrackingSummaryRow("ค่าจัดส่ง", order.deliveryFee)
            if (order.bundleCustomerSavings > 0) {
                Spacer(Modifier.height(7.dp))
                Text(
                    "ประหยัดจากงานพ่วง ฿" + "%.0f".format(order.bundleCustomerSavings),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEFFAF5))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    color = Color(0xFF178456),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = Color(0xFFEEE8EA))
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    "ยอดรวม",
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "฿" + "%.0f".format(order.total),
                    color = Color(0xFF17191D),
                    fontSize = 22.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        if (order.status.equals("completed", true)) {
            Spacer(Modifier.height(8.dp))
            CustomerReviewCard(
                auth = auth,
                order = order,
                shopCategory = shopCategory
            )
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun CustomerTrackingHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(38.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp), clip = false)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFECECEF), RoundedCornerShape(12.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.qg_shop_back),
                contentDescription = "ย้อนกลับ",
                modifier = Modifier.size(20.dp),
                tint = Color(0xFF24272D)
            )
        }
        Text(
            title,
            fontSize = 19.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun CustomerTrackingProgress(stage: Int) {
    val labels = listOf("สั่งซื้อ", "หา Rider", "รับสินค้า", "กำลังส่ง", "สำเร็จ")
    Row(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.fillMaxWidth().height(17.dp)) {
                    if (index > 0) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth(.5f)
                                .height(2.dp)
                                .background(if (index <= stage) QgRed else Color(0xFFE8E4E6))
                        )
                    }
                    if (index < labels.lastIndex) {
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxWidth(.5f)
                                .height(2.dp)
                                .background(if (index < stage) QgRed else Color(0xFFE8E4E6))
                        )
                    }
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (index <= stage) QgRed else Color(0xFFDEDADD))
                        )
                    }
                }
                Text(
                    label,
                    color = if (index <= stage) Color(0xFF2B2729) else Color(0xFFAAA3A7),
                    fontSize = 7.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun CustomerSearchingRiderCard() {
    CustomerTrackingCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFEEF1))
                    .border(1.dp, Color(0xFFFFDCE3), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(QgRed))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "ระบบกำลังทำงาน",
                    color = Color(0xFF91898D),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "กำลังติดต่อ Rider ที่พร้อมรับงาน",
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "QueueGo เสนอออเดอร์ให้ Rider ทีละคน เพื่อไม่ให้เกิดการแย่งงาน",
                    color = QgMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
            Text(
                "กำลังค้นหา",
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFFFFF0F3))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                color = QgRed,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.height(10.dp))
        CustomerSearchStep(1, "เลือกไรเดอร์ที่เหมาะสม", "พิจารณา Rider ที่ออนไลน์และพร้อมรับงานใกล้จุดรับสินค้า", true)
        CustomerSearchStep(2, "รอการตอบรับสูงสุด 30 วินาที", "ออเดอร์ถูกเสนอให้ Rider เพียงคนเดียวในแต่ละรอบ", false)
        CustomerSearchStep(3, "ส่งต่อให้ Rider คนถัดไปอัตโนมัติ", "หากปฏิเสธหรือหมดเวลา ระบบจะดำเนินการต่อเอง", false)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("ระบบเสนอ Rider ทีละคน", modifier = Modifier.weight(1f), color = QgMuted, fontSize = 9.sp)
            Text("หน้านี้อัปเดตอัตโนมัติ", color = QgMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun CustomerSearchStep(number: Int, title: String, note: String, active: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (active) QgRed else Color(0xFFF1EEF0)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                color = if (active) Color.White else Color(0xFF8F878B),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            Text(note, color = QgMuted, fontSize = 9.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun CustomerTrackingCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 7.dp,
                shape = RoundedCornerShape(18.dp),
                clip = false,
                ambientColor = Color(0x0B1F181B),
                spotColor = Color(0x0B1F181B)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFECE7E9), RoundedCornerShape(18.dp))
            .padding(12.dp),
        content = content
    )
}

@Composable
private fun CustomerTrackingAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) QgRed else Color.White)
            .border(
                1.dp,
                if (filled) QgRed else Color(0xFFEADFE2),
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = when {
                !enabled -> QgMuted
                filled -> Color.White
                danger -> Color(0xFF9B1C31)
                else -> Color(0xFF252126)
            },
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CustomerTrackingSummaryRow(label: String, amount: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = Color(0xFF5F585C), fontSize = 11.sp)
        Text("฿" + "%.0f".format(amount), color = Color(0xFF5F585C), fontSize = 11.sp)
    }
}

private fun customerOrderStage(status: String): Int = when (status.lowercase()) {
    "completed" -> 4
    "picked_up", "in_progress", "rider_to_customer", "arrived" -> 3
    "rider_assigned", "assigned", "preparing", "ready" -> 2
    "accepted", "searching_rider" -> 1
    else -> 0
}

private fun customerTrackingStatusLabel(status: String): String = when (status.lowercase()) {
    "pending" -> "รอร้านรับ"
    "accepted" -> "ร้านรับแล้ว"
    "searching_rider" -> "กำลังหาไรเดอร์"
    "rider_assigned" -> "ไรเดอร์รับงานแล้ว"
    "preparing" -> "กำลังเตรียม"
    "ready" -> "พร้อมรับสินค้า"
    "assigned" -> "ไรเดอร์รับงาน"
    "picked_up" -> "รับสินค้าแล้ว"
    "in_progress", "rider_to_customer" -> "กำลังจัดส่ง"
    "arrived" -> "Rider ถึงลูกค้าแล้ว"
    "completed" -> "ส่งสำเร็จ"
    "no_rider_available" -> "ไม่พบ Rider"
    "cancelled" -> "ยกเลิก"
    else -> status
}

private fun customerTrackingTime(order: CustomerOrder, visibleStatus: String): String {
    val raw = when {
        order.status.lowercase() in setOf("completed", "cancelled", "no_rider_available") ->
            order.completedAt ?: order.updatedAt ?: order.createdAt
        visibleStatus == "arrived" ->
            order.riderArrivedCustomerAt ?: order.updatedAt ?: order.createdAt
        else -> order.createdAt
    } ?: return "-"
    return runCatching {
        val formatter = DateTimeFormatter
            .ofPattern("d MMM HH:mm", Locale("th", "TH"))
            .withZone(ZoneId.of("Asia/Bangkok"))
        formatter.format(Instant.parse(raw))
    }.getOrDefault(raw.take(16).replace('T', ' '))
}

