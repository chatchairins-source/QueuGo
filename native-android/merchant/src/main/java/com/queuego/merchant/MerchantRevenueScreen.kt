package com.queuego.merchant

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun MerchantRevenueScreen(
    auth: NativeAuth,
    api: MerchantApi,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now(ZoneId.of("Asia/Bangkok")) }
    var selectedDate by remember { mutableStateOf(today.toString()) }
    var rangeDays by remember { mutableStateOf(1) }
    var rows by remember { mutableStateOf<List<MerchantRevenueDay>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var submittingSlip by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var csvPayload by remember { mutableStateOf<String?>(null) }

    suspend fun load(days: Int, endDate: String) {
        loading = true
        message = null
        runCatching {
            val end = LocalDate.parse(endDate)
            require(!end.isAfter(today)) { "เลือกวันที่ในอนาคตไม่ได้" }
            val start = end.minusDays((days - 1).coerceAtLeast(0).toLong())
            api.revenueDays(auth, start.toString(), end.toString())
        }.onSuccess {
            rows = it
            rangeDays = days
        }.onFailure {
            message = it.message ?: "โหลดรายได้ไม่สำเร็จ"
        }
        loading = false
    }

    LaunchedEffect(Unit) { load(1, selectedDate) }

    val slipPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !submittingSlip) {
            val day = rows.firstOrNull { it.dateKey == selectedDate }
            if (day == null) {
                message = "ไม่พบรายงานวันที่เลือก"
            } else {
                submittingSlip = true
                scope.launch {
                    runCatching {
                        val path = uploadMerchantGpSlip(context, auth, uri, selectedDate)
                        api.reportGpSlip(auth, selectedDate, path)
                    }.onSuccess {
                        message = "ส่งสลิปและแจ้งโอนให้แอดมินแล้ว"
                        load(1, selectedDate)
                    }.onFailure {
                        message = "แจ้งโอนไม่สำเร็จ: " + (it.message ?: "เกิดข้อผิดพลาด")
                    }
                    submittingSlip = false
                }
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val csv = csvPayload
        if (uri != null && csv != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(("\uFEFF" + csv).toByteArray(Charsets.UTF_8))
                } ?: error("เปิดไฟล์ปลายทางไม่สำเร็จ")
            }.onSuccess {
                message = "บันทึกรายงาน CSV แล้ว"
            }.onFailure {
                message = "บันทึกรายงานไม่สำเร็จ: " + (it.message ?: "เกิดข้อผิดพลาด")
            }
        }
        csvPayload = null
    }

    fun exportThirtyDays() {
        scope.launch {
            loading = true
            message = null
            runCatching {
                val end = today
                val start = end.minusDays(29)
                api.revenueDays(auth, start.toString(), end.toString())
            }.onSuccess { reportRows ->
                val csv = buildString {
                    append("วันที่,จำนวนออเดอร์,ยอดค่าสินค้า,GP,เงินสดค่าสินค้าที่ร้านได้รับ,สถานะ GP\r\n")
                    reportRows.forEach { row ->
                        append(csvCell(row.dateKey)).append(',')
                        append(row.orderCount).append(',')
                        append(row.grossSales).append(',')
                        append(row.gpDue).append(',')
                        append(row.cashReceived).append(',')
                        append(csvCell(row.gpStatus)).append("\r\n")
                    }
                }
                csvPayload = csv
                csvLauncher.launch("QueueGo-revenue-" + today + ".csv")
            }.onFailure {
                message = "ดาวน์โหลดรายงานไม่สำเร็จ: " + (it.message ?: "เกิดข้อผิดพลาด")
            }
            loading = false
        }
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
            Text(
                "รายได้ทั้งหมด",
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Text(
            "เงินสดค่าสินค้า: ไรเดอร์จ่ายร้านตอนรับสินค้า แล้วเก็บจากลูกค้าเอง · GP คือค่าบริการร้าน",
            color = Color(0xFF607E6E),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFFE5EEE8), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Text("เลือกวันที่", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(7.dp))
            OutlinedTextField(
                value = selectedDate,
                onValueChange = { selectedDate = it.take(10) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("YYYY-MM-DD") }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RevenueQuickButton("วันนี้") {
                    selectedDate = today.toString()
                    scope.launch { load(1, today.toString()) }
                }
                RevenueQuickButton("7 วันที่ผ่านมา") {
                    selectedDate = today.toString()
                    scope.launch { load(7, today.toString()) }
                }
                RevenueQuickButton("30 วันที่ผ่านมา") {
                    selectedDate = today.toString()
                    scope.launch { load(30, today.toString()) }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { scope.launch { load(1, selectedDate) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("โหลดวันที่เลือก") }
            OutlinedButton(
                onClick = { exportThirtyDays() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("ดาวน์โหลดรายงาน 30 วัน (CSV)") }
        }

        if (!message.isNullOrBlank()) {
            Text(
                message!!,
                color = if (message!!.contains("แล้ว")) Color(0xFF0A9660) else QgRed,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp)
            )
        }

        when {
            loading -> Box(
                Modifier.fillMaxWidth().height(150.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            rows.isEmpty() -> RevenueReportCard {
                Text("ไม่พบรายงาน", color = QgMuted)
            }

            rangeDays == 1 -> {
                val day = rows.firstOrNull()
                if (day != null) {
                    RevenueDailyReport(day)
                    if (day.gpDue > 0.0 && day.gpStatus == "pending") {
                        OutlinedButton(
                            onClick = {
                                slipPicker.launch(
                                    arrayOf("image/png", "image/jpeg", "image/webp", "application/pdf")
                                )
                            },
                            enabled = !submittingSlip,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            if (submittingSlip) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text("แนบสลิปและแจ้งโอน")
                        }
                    }
                }
            }

            else -> RevenuePeriodReport(rangeDays, rows)
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun RevenueQuickButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(Color.White, RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFFD6EADF), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, color = Color(0xFF447263), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RevenueDailyReport(day: MerchantRevenueDay) {
    val net = day.grossSales - day.gpDue
    RevenueReportCard {
        Text(
            thaiRevenueDate(day.dateKey),
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF173C31)
        )
        Spacer(Modifier.height(10.dp))
        RevenueGrid(
            listOf(
                "ยอดค่าสินค้าหลังไรเดอร์รับ" to money(day.grossSales),
                "GP ที่ต้องชำระ" to money(day.gpDue),
                "เงินสดค่าสินค้าที่ร้านได้รับ" to money(day.cashReceived),
                "ออเดอร์ที่ไรเดอร์รับสินค้า" to day.orderCount.toString()
            )
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "ประมาณการยอดค่าสินค้าหลังหัก GP: ${money(net)} · สถานะ GP: ${gpStatusLabel(day.gpStatus)}",
            color = Color(0xFF607E6E),
            fontSize = 10.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "ยอดเงินสดบันทึกอัตโนมัติตาม flow การรับสินค้าของออเดอร์ ไม่ต้องกดยืนยันการรับเงินเพิ่ม",
            color = QgMuted,
            fontSize = 9.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun RevenuePeriodReport(days: Int, rows: List<MerchantRevenueDay>) {
    RevenueReportCard {
        Text(
            "รายได้ $days วัน",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF173C31)
        )
        Spacer(Modifier.height(10.dp))
        RevenueGrid(
            listOf(
                "ยอดค่าสินค้า" to money(rows.sumOf { it.grossSales }),
                "จำนวนออเดอร์" to rows.sumOf { it.orderCount }.toString(),
                "GP" to money(rows.sumOf { it.gpDue }),
                "เงินสดค่าสินค้าที่ร้านได้รับ" to money(rows.sumOf { it.cashReceived })
            )
        )
    }
    rows.forEach { row ->
        RevenueReportCard {
            Text(row.dateKey, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
            Text(
                "${row.orderCount} ออเดอร์ · ${money(row.grossSales)}",
                color = QgMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun RevenueReportCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFE5EEE8), RoundedCornerShape(14.dp))
            .padding(12.dp),
        content = content
    )
}

@Composable
private fun RevenueGrid(items: List<Pair<String, String>>) {
    items.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (label, value) ->
                Column(
                    Modifier
                        .weight(1f)
                        .background(Color(0xFFF4FAF6), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(label, color = Color(0xFF688478), fontSize = 9.sp, lineHeight = 12.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(value, color = Color(0xFF173C31), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun money(value: Double): String =
    "฿" + String.format(Locale.US, "%,.2f", value)

private fun gpStatusLabel(status: String): String = when (status.lowercase()) {
    "paid", "approved", "settled" -> "ชำระแล้ว"
    "submitted", "reviewing" -> "ส่งตรวจแล้ว"
    "rejected" -> "ต้องแก้ไข"
    else -> "รอชำระ"
}

private fun thaiRevenueDate(date: String): String = runCatching {
    LocalDate.parse(date).format(
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("th", "TH"))
    )
}.getOrDefault(date)

private fun csvCell(value: String): String =
    "\"" + value.replace("\"", "\"\"") + "\""
