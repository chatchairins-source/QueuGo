package com.queuego.rider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QgRiderBg
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun RiderEarningsScreen(
    auth: QueueGoAuth,
    api: QueueGoApi,
    modifier: Modifier = Modifier
) {
    var days by remember { mutableIntStateOf(1) }
    var summary by remember { mutableStateOf<RiderPeriodSummary?>(null) }
    var ledger by remember { mutableStateOf<List<RiderCashLedgerEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(auth.session.accessToken, days) {
        loading = true
        error = null
        val zone = ZoneId.of("Asia/Bangkok")
        val to = LocalDate.now(zone)
        val from = to.minusDays((days - 1).toLong())
        runCatching {
            api.periodSummary(auth, days) to
                api.cashLedger(auth, from.toString(), to.toString())
        }.onSuccess {
            summary = it.first
            ledger = it.second
        }.onFailure {
            error = it.message ?: "โหลดข้อมูลรายได้ไม่สำเร็จ"
        }
        loading = false
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text("รายได้", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to "วันนี้", 7 to "7 วัน", 30 to "30 วัน").forEach { (value, label) ->
                if (days == value) {
                    Button(
                        onClick = { days = value },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(label, fontWeight = FontWeight.Bold) }
                } else {
                    OutlinedButton(
                        onClick = { days = value },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(label, fontWeight = FontWeight.Bold) }
                }
            }
        }

        if (loading) {
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator()
            return@Column
        }
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(error!!, color = QgRed)
            return@Column
        }

        val s = summary ?: RiderPeriodSummary(days, 0, 0.0, 0.0, null)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            RiderEarnStat("รายได้จากค่าส่ง", "฿" + "%.2f".format(s.income), Modifier.weight(1f))
            RiderEarnStat("งานสำเร็จ", s.jobs.toString() + " งาน", Modifier.weight(1f))
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            RiderEarnStat("ออนไลน์", "%.1f ชม.".format(s.onlineHours), Modifier.weight(1f))
            RiderEarnStat(
                "เฉลี่ย/ชม.",
                s.incomePerHour?.let { "฿" + "%.2f".format(it) } ?: "—",
                Modifier.weight(1f)
            )
        }

        val paid = ledger.sumOf { it.cashPaidMerchant }
        val collected = ledger.sumOf { it.cashCollectedCustomer }
        val fees = ledger.sumOf { it.deliveryFee }

        Spacer(Modifier.height(12.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(15.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text("บัญชีเงินสด", fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Text(if (days == 1) "วันนี้" else "$days วัน", color = QgRed, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RiderEarnStat("จ่ายร้าน", "฿" + "%.2f".format(paid), Modifier.weight(1f))
                    RiderEarnStat("เก็บลูกค้า", "฿" + "%.2f".format(collected), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RiderEarnStat("ค่าส่งที่ได้รับ", "฿" + "%.2f".format(fees), Modifier.weight(1f))
                    RiderEarnStat("สุทธิรับ-จ่าย", "฿" + "%.2f".format(collected - paid), Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(15.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text("รายการงานล่าสุด", fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Text(ledger.size.toString() + " งาน", color = QgRed, fontWeight = FontWeight.Bold)
                }
                if (ledger.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("ยังไม่มีรายการ", color = QgMuted)
                } else {
                    ledger.take(30).forEach { row ->
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(row.numberLabel, fontWeight = FontWeight.Bold)
                                Text(row.status, color = QgMuted, style = MaterialTheme.typography.labelSmall)
                            }
                            Column {
                                Text(if (row.status == "completed") "สำเร็จ" else "กำลังดำเนินการ", color = QgMuted)
                                Text("฿" + "%.2f".format(row.deliveryFee), fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(86.dp))
    }
}

@Composable
private fun RiderEarnStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF7F8))
    ) {
        Column(Modifier.padding(11.dp)) {
            Text(label, color = QgMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Black)
        }
    }
}
