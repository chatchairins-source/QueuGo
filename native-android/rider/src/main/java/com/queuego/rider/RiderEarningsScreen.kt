package com.queuego.rider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRedSoft
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RiderEarningsScreen(auth: QueueGoAuth, api: QueueGoApi, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var days by remember { mutableIntStateOf(1) }
    var periodRefresh by remember { mutableIntStateOf(0) }
    var summary by remember { mutableStateOf<RiderPeriodSummary?>(null) }
    var ledger by remember { mutableStateOf<List<RiderCashLedgerEntry>?>(null) }
    var summaryError by remember { mutableStateOf<String?>(null) }
    var ledgerError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(auth.session.accessToken, days, periodRefresh) {
        summary = null; summaryError = null
        try { summary = api.periodSummary(auth, days) } catch (e: CancellationException) { throw e } catch (e: Exception) { summaryError = "โหลดสรุปงานไม่สำเร็จ: " + (e.message ?: "เชื่อมต่อไม่ได้") }
    }
    LaunchedEffect(auth.session.accessToken, days, periodRefresh) {
        ledger = null; ledgerError = null
        val to = LocalDate.now(ZoneId.of("Asia/Bangkok"))
        val from = to.minusDays((days - 1).toLong())
        try {
            ledger = api.cashLedger(auth, from.toString(), to.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ledgerError = "โหลดบัญชีเงินสดไม่สำเร็จ: " + (e.message ?: "เชื่อมต่อไม่ได้")
        }
    }
    Column(modifier.fillMaxSize().background(Color(0xFFF8F7F8)).verticalScroll(rememberScrollState())
        .padding(horizontal = 14.dp).padding(top = 15.dp, bottom = 78.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp).background(QgRedSoft, RoundedCornerShape(99.dp))) {
                Icon(painterResource(R.drawable.qg_rider_flow_back), contentDescription = "ย้อนกลับ", tint = QgRed, modifier = Modifier.size(23.dp))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("รายได้", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(64.dp))
        }
        com.queuego.shared.QueueGoCreditInline(auth.user.id, auth.session.accessToken, "rider")
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFEEE6E8)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to "วันนี้", 7 to "7 วัน", 30 to "30 วัน").forEach { (value, label) ->
                    Box(Modifier.weight(1f).height(44.dp).background(if (days == value) QgRed else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button) { days = value; periodRefresh++ }, contentAlignment = Alignment.Center) {
                        Text(label, color = if (days == value) Color.White else Color(0xFF8B8488), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        when {
            summaryError != null -> RiderEarnError(summaryError!!)
            summary == null -> RiderEarnLoading()
            else -> {
                val s = summary!!
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    RiderEarnStat("รายได้จากค่าส่ง", baht(s.income), Color.White, QgRed, Modifier.weight(1f))
                    RiderEarnStat("งานสำเร็จ", "${s.jobs} งาน", Color(0xFFF2FBF6), Color(0xFF17171B), Modifier.weight(1f))
                }
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    RiderEarnStat("ออนไลน์", "${s.onlineHours} ชม.", Color(0xFFF3F8FF), Color(0xFF17171B), Modifier.weight(1f))
                    RiderEarnStat("เฉลี่ย/ชม.", s.incomePerHour?.let(::baht) ?: "—", Color(0xFFFFF9EF), Color(0xFF17171B), Modifier.weight(1f))
                }


            }
        }
        when {
            ledgerError != null -> RiderEarnError(ledgerError!!)
            ledger == null -> RiderEarnLoading()
            else -> {
                val rows = ledger.orEmpty()
                Spacer(Modifier.height(12.dp))
                RiderEarningsCard {
                    RiderEarnSectionTitle("บัญชีเงินสด", if (days == 1) "วันนี้" else "$days วัน")
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RiderCashStat("จ่ายร้าน", baht(rows.sumOf { it.cashPaidMerchant }), Modifier.weight(1f))
                        RiderCashStat("เก็บลูกค้า", baht(rows.sumOf { it.cashCollectedCustomer }), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RiderCashStat("ค่าส่งที่ได้รับ", baht(rows.sumOf { it.deliveryFee }), Modifier.weight(1f))
                        RiderCashStat(
                            "สุทธิรับ-จ่าย",
                            baht(rows.sumOf { it.cashCollectedCustomer - it.cashPaidMerchant }),
                            Modifier.weight(1f),
                            QgRed
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                RiderEarningsCard {
                    RiderEarnSectionTitle("รายการงานล่าสุด", "${rows.size} งาน")
                    if (rows.isEmpty()) RiderEarnLoading("ยังไม่มีรายการ")
                    val visibleRows = rows.take(30)
                    visibleRows.forEachIndexed { index, row ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 11.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.numberLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(row.status, color = QgMuted, fontSize = 8.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (row.status == "completed") "สำเร็จ" else "กำลังดำเนินการ",
                                    color = QgMuted,
                                    fontSize = 8.sp
                                )
                                Text(baht(row.deliveryFee), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                        if (index < visibleRows.lastIndex) HorizontalDivider(color = Color(0xFFF0EAEC))
                    }
                }
            }
        }
    }
}

@Composable
private fun RiderJobHistoryDetail(
    row: RiderCashLedgerEntry,
    detail: RiderHistoryOrder?
) {
    Spacer(Modifier.height(9.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFFAF7F8), RoundedCornerShape(14.dp))
            .padding(11.dp)
    ) {
        Text("เส้นทางงาน", color = QgMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(
            "รับ · " + detail?.pickupAddress.orEmpty().ifBlank { "ไม่มีรายละเอียดจุดรับ" },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            "ส่ง · " + detail?.deliveryAddress.orEmpty().ifBlank { "ไม่มีรายละเอียดจุดส่ง" },
            color = Color(0xFF5E575B),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            RiderCashStat("ค่าส่ง", baht(row.deliveryFee), Modifier.weight(1f))
            RiderCashStat("จ่ายร้าน", baht(row.cashPaidMerchant), Modifier.weight(1f))
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            RiderCashStat("คืนจากร้าน", baht(row.cashRefundedMerchant), Modifier.weight(1f))
            RiderCashStat("เก็บลูกค้า", baht(row.cashCollectedCustomer), Modifier.weight(1f))
        }
        Spacer(Modifier.height(7.dp))
        if (row.platformCancellationFunding > 0.0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                RiderCashStat(
                    if (row.platformCancellationFundingStatus == "settled") "QueueGo ชำระแล้ว" else "รอ QueueGo ชำระ",
                    baht(if (row.platformCancellationFundingStatus == "settled") row.platformCancellationFundingCash else row.platformCancellationFunding),
                    Modifier.weight(1f),
                    if (row.platformCancellationFundingStatus == "settled") Color(0xFF12845E) else Color(0xFF8C5A00)
                )
            }
            row.platformCancellationFundingReference?.let {
                Text("อ้างอิง $it", color = QgMuted, fontSize = 8.sp, modifier = Modifier.padding(top = 5.dp))
            }
            Spacer(Modifier.height(7.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            RiderCashStat(
                "เงินสดรับ-จ่าย",
                baht(riderHistoryCashFlow(row)),
                Modifier.weight(1f),
                if (riderHistoryCashFlow(row) >= 0.0) Color(0xFF17171B) else QgRed
            )
        }
        Text(
            "เงินสดรับ-จ่ายรวมเงิน QueueGo เฉพาะรายการที่ยืนยันชำระแล้ว และไม่ใช่กำไรสุทธิ",
            color = QgMuted,
            fontSize = 8.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun RiderMissionRow(mission: RiderMission) {
    val fraction = if (mission.targetJobs <= 0) 0f
        else (mission.progressJobs.toFloat() / mission.targetJobs.toFloat()).coerceIn(0f, 1f)
    val stateLabel = when (mission.state) {
        "completed" -> "สำเร็จแล้ว"
        "upcoming" -> "กำลังจะเริ่ม"
        else -> "กำลังทำ"
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(mission.title, fontSize = 14.sp, fontWeight = FontWeight.Black)
                mission.description?.let {
                    Text(it, color = QgMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
            Text(
                stateLabel,
                color = if (mission.completed) Color(0xFF12845E) else QgRed,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color(0xFFF0EAEC), RoundedCornerShape(99.dp))
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(if (mission.completed) Color(0xFF12845E) else QgRed, RoundedCornerShape(99.dp))
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${mission.progressJobs} / ${mission.targetJobs} เที่ยว",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            riderMissionEndLabel(mission.endsAt)?.let {
                Text("ถึง $it", color = QgMuted, fontSize = 8.sp)
            }
        }
        mission.rewardNote?.let {
            Text(
                "รางวัล · $it",
                color = Color(0xFF6E5C15),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 7.dp)
                    .background(Color(0xFFFFF7D6), RoundedCornerShape(99.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

internal fun riderMissionEndLabel(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) } ?: return null
    val instant = runCatching { Instant.parse(value) }
        .getOrElse {
            runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull() ?: return null
        }
    return DateTimeFormatter
        .ofPattern("d MMM HH:mm", Locale("th", "TH"))
        .withZone(ZoneId.of("Asia/Bangkok"))
        .format(instant)
}

@Composable
private fun RiderEarnStat(label: String, value: String, background: Color, color: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFFEEE6E8)), colors = CardDefaults.cardColors(containerColor = background)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = Color(0xFF8A8387), fontSize = 9.sp)
            Text(value, color = color, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun RiderCashStat(label: String, value: String, modifier: Modifier, color: Color = Color(0xFF17171B)) {
    Column(modifier.background(Color(0xFFFAF7F8), RoundedCornerShape(14.dp)).padding(11.dp)) {
        Text(label, color = Color(0xFF8D8589), fontSize = 8.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun RiderEarningsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color(0xFFEEE6E8)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(15.dp), content = content)
    }
}

@Composable
private fun RiderEarnSectionTitle(title: String, count: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(count, color = QgRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RiderEarnLoading(message: String? = null) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (message.isNullOrBlank()) 1.dp else 28.dp), contentAlignment = Alignment.Center) {
        if (!message.isNullOrBlank()) Text(message, color = Color(0xFF8A8387), fontSize = 12.sp)
    }
}

@Composable
private fun RiderEarnError(message: String) {
    Box(Modifier.fillMaxWidth().padding(top = 12.dp).background(Color(0xFFFFF3F5), RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 28.dp)) {
        Text(message, color = Color(0xFF7D2738), fontSize = 12.sp)
    }
}

internal fun riderHistoryCashFlow(row: RiderCashLedgerEntry): Double =
    row.cashCollectedCustomer + row.cashRefundedMerchant + row.platformCancellationFundingCash - row.cashPaidMerchant

internal fun riderHistoryStatusLabel(status: String): String = when (status.lowercase()) {
    "completed" -> "งานส่งสำเร็จ"
    "cancelled" -> "งานถูกยกเลิก"
    "no_rider_available" -> "งานสิ้นสุด"
    "picked_up" -> "รับสินค้าแล้ว"
    "in_progress" -> "กำลังจัดส่ง"
    "ready" -> "พร้อมรับสินค้า"
    "preparing" -> "ร้านกำลังเตรียม"
    "rider_assigned", "assigned" -> "รับงานแล้ว"
    else -> status.ifBlank { "สถานะงาน" }
}

internal fun riderHistoryCompletedLabel(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) } ?: return null
    val instant = runCatching { Instant.parse(value) }
        .getOrElse {
            runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull() ?: return null
        }
    val formatter = DateTimeFormatter
        .ofPattern("d MMM · HH:mm", Locale("th", "TH"))
        .withZone(ZoneId.of("Asia/Bangkok"))
    return formatter.format(instant)
}

internal fun riderOfferPercent(value: Double): String =
    String.format(Locale.US, "%.1f%%", value.coerceIn(0.0, 100.0))

internal fun riderOfferResponseTime(seconds: Double): String {
    val safe = seconds.coerceAtLeast(0.0)
    return if (safe < 60.0) String.format(Locale.US, "%.1f วินาที", safe)
    else String.format(Locale.US, "%.1f นาที", safe / 60.0)
}

private fun baht(value: Double) = riderMoneyBaht(value, fixedCashDecimals = true)
