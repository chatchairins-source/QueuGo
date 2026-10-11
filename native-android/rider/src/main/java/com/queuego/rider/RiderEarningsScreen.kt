package com.queuego.rider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.QgIcon
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Composable
fun RiderEarningsScreen(
    auth: QueueGoAuth,
    api: QueueGoApi,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var days by remember { mutableIntStateOf(1) }
    var summary by remember { mutableStateOf<RiderPeriodSummary?>(null) }
    var ledger by remember { mutableStateOf<List<RiderCashLedgerEntry>>(emptyList()) }
    var loadingSummary by remember { mutableStateOf(true) }
    var loadingLedger by remember { mutableStateOf(true) }
    var summaryError by remember { mutableStateOf<String?>(null) }
    var ledgerError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(auth.session.accessToken, days) {
        val zone = ZoneId.of("Asia/Bangkok")
        val to = LocalDate.now(zone)
        val from = to.minusDays((days - 1).toLong())

        loadingSummary = true
        summaryError = null
        runCatching { api.periodSummary(auth, days) }
            .onSuccess { summary = it }
            .onFailure { summaryError = it.message ?: "โหลดสรุปงานไม่สำเร็จ" }
        loadingSummary = false

        loadingLedger = true
        ledgerError = null
        runCatching { api.cashLedger(auth, from.toString(), to.toString()) }
            .onSuccess { ledger = it }
            .onFailure { ledgerError = it.message ?: "โหลดบัญชีเงินสดไม่สำเร็จ" }
        loadingLedger = false
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFFEEE6E8), RoundedCornerShape(14.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                QgIcon("back", Modifier.size(20.dp), Color(0xFF211E20))
            }
            Text(
                "รายได้",
                modifier = Modifier.weight(1f),
                color = Color(0xFF211E20),
                fontSize = 26.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(64.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(18.dp))
                .border(1.dp, Color(0xFFEEE6E8), RoundedCornerShape(18.dp))
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(1 to "วันนี้", 7 to "7 วัน", 30 to "30 วัน").forEach { (value, label) ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(
                            if (days == value) QgRed else Color.Transparent,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { days = value },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (days == value) Color.White else Color(0xFF8B8488),
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        if (loadingSummary) {
            RiderLoadingCard("กำลังโหลดสรุปงาน...")
        } else if (!summaryError.isNullOrBlank()) {
            RiderLoadingCard("โหลดสรุปงานไม่สำเร็จ: " + summaryError)
        } else {
            val s = summary ?: RiderPeriodSummary(days, 0, 0.0, 0.0, null)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                RiderEarnStat(
                    label = "รายได้จากค่าส่ง",
                    value = riderBaht(s.income),
                    modifier = Modifier.weight(1f),
                    container = Color.White,
                    valueColor = QgRed
                )
                RiderEarnStat(
                    label = "งานสำเร็จ",
                    value = s.jobs.toString() + " งาน",
                    modifier = Modifier.weight(1f),
                    container = Color(0xFFF2FBF6)
                )
            }
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                RiderEarnStat(
                    label = "ออนไลน์",
                    value = riderNumber(s.onlineHours) + " ชม.",
                    modifier = Modifier.weight(1f),
                    container = Color(0xFFF3F8FF)
                )
                RiderEarnStat(
                    label = "เฉลี่ย/ชม.",
                    value = s.incomePerHour?.let(::riderBaht) ?: "—",
                    modifier = Modifier.weight(1f),
                    container = Color(0xFFFFF9EF)
                )
            }
        }

        if (loadingLedger) {
            RiderLoadingCard("กำลังโหลดบัญชีเงินสด...")
        } else if (!ledgerError.isNullOrBlank()) {
            RiderLoadingCard("โหลดบัญชีเงินสดไม่สำเร็จ: " + ledgerError)
        } else {
            val paid = ledger.sumOf { it.cashPaidMerchant }
            val collected = ledger.sumOf { it.cashCollectedCustomer }
            val fees = ledger.sumOf { it.deliveryFee }

            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(22.dp))
                    .border(1.dp, Color(0xFFEEE6E8), RoundedCornerShape(22.dp))
                    .padding(15.dp)
            ) {
                RiderSectionTitle(
                    title = "บัญชีเงินสด",
                    trailing = if (days == 1) "วันนี้" else "$days วัน"
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RiderCashCell("จ่ายร้าน", riderBaht(paid), Modifier.weight(1f))
                    RiderCashCell("เก็บลูกค้า", riderBaht(collected), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RiderCashCell("ค่าส่งที่ได้รับ", riderBaht(fees), Modifier.weight(1f))
                    RiderCashCell(
                        "สุทธิรับ-จ่าย",
                        riderBaht(collected - paid),
                        Modifier.weight(1f),
                        valueColor = QgRed
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(22.dp))
                    .border(1.dp, Color(0xFFEEE6E8), RoundedCornerShape(22.dp))
                    .padding(15.dp)
            ) {
                RiderSectionTitle("รายการงานล่าสุด", ledger.size.toString() + " งาน")
                if (ledger.isEmpty()) {
                    Text(
                        "ยังไม่มีรายการ",
                        color = QgMuted,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
                    ledger.take(30).forEachIndexed { index, row ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.numberLabel, fontWeight = FontWeight.Bold)
                                Text(
                                    row.status,
                                    color = Color(0xFF8A8387),
                                    fontSize = 8.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (row.status == "completed") "สำเร็จ" else "กำลังดำเนินการ",
                                    color = Color(0xFF8A8387),
                                    fontSize = 8.sp
                                )
                                Text(riderBaht(row.deliveryFee), fontWeight = FontWeight.Black)
                            }
                        }
                        if (index != ledger.take(30).lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFFF0EAEC))
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(86.dp))
    }
}

@Composable
private fun RiderLoadingCard(text: String) {
    Text(
        text,
        color = Color(0xFF8A8387),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 28.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun RiderEarnStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    container: Color,
    valueColor: Color = Color(0xFF211E20)
) {
    Column(
        modifier
            .background(container, RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFFEEE6E8), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(label, color = Color(0xFF8A8387), fontSize = 9.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            color = valueColor,
            fontSize = 21.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun RiderCashCell(
    label: String,
    value: String,
    modifier: Modifier,
    valueColor: Color = Color(0xFF211E20)
) {
    Column(
        modifier
            .background(Color(0xFFFAF7F8), RoundedCornerShape(14.dp))
            .padding(11.dp)
    ) {
        Text(label, color = Color(0xFF8D8589), fontSize = 8.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun RiderSectionTitle(title: String, trailing: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Black)
        Text(trailing, color = QgRed, fontWeight = FontWeight.Bold)
    }
}

private fun riderBaht(value: Double): String =
    "฿" + String.format(Locale("th", "TH"), "%,.2f", value)

private fun riderNumber(value: Double): String =
    String.format(Locale("th", "TH"), "%s", value)
