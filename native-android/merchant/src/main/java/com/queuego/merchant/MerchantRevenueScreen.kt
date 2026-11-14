package com.queuego.merchant

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgIcon
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.QueueGoCreditCard
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RevenueSurface = Color(0xFFF7F8FA)
private val RevenueCardBorder = Color(0xFFE9EBEF)
private val RevenueRed = Color(0xFFF10F3A)
private val RevenueGreen = Color(0xFF12A65B)
private val RevenueOrange = Color(0xFFF27A20)

@Composable
internal fun MerchantRevenueScreen(
    auth: NativeAuth,
    api: MerchantApi,
    shopName: String,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now(ZoneId.of("Asia/Bangkok")) }
    var selectedDate by remember { mutableStateOf(today.toString()) }
    var rangeDays by remember { mutableStateOf(1) }
    var rangeMode by remember { mutableStateOf("today") }
    var rows by remember { mutableStateOf<List<MerchantRevenueDay>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var csvPayload by remember { mutableStateOf<String?>(null) }

    suspend fun load(days: Int, endDate: String, mode: String) {
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
            rangeMode = mode
        }.onFailure {
            message = it.message ?: "โหลดรายได้ไม่สำเร็จ"
        }
        loading = false
    }

    LaunchedEffect(Unit) { load(1, selectedDate, "today") }

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
                    append("วันที่,จำนวนออเดอร์,ยอดค่าสินค้า,GP ที่หักจากเครดิต,GP เดิมก่อนระบบเครดิต,เงินสดค่าสินค้าที่ร้านได้รับ\r\n")
                    reportRows.forEach { row ->
                        append(csvCell(row.dateKey)).append(',')
                        append(row.orderCount).append(',')
                        append(row.grossSales).append(',')
                        append(row.gpAmount).append(',')
                        append(row.legacyGpDue).append(',')
                        append(row.cashReceived).append("\r\n")
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

    val totalOrders = rows.sumOf { it.orderCount }
    val totalSales = rows.sumOf { it.grossSales }
    val totalGp = rows.sumOf { it.gpAmount }
    val totalCash = rows.sumOf { it.cashReceived }
    val totalLegacyGp = rows.sumOf { it.legacyGpDue }

    Column(
        Modifier
            .fillMaxSize()
            .background(RevenueSurface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        RevenuePageHeader(
            shopName = shopName,
            onBack = onBack
        )

        Spacer(Modifier.height(12.dp))
        RevenueRangeRow(
            selected = rangeMode,
            onToday = {
                selectedDate = today.toString()
                scope.launch { load(1, today.toString(), "today") }
            },
            onSevenDays = {
                selectedDate = today.toString()
                scope.launch { load(7, today.toString(), "7") }
            },
            onThirtyDays = {
                selectedDate = today.toString()
                scope.launch { load(30, today.toString(), "30") }
            },
            onDate = {
                val current = runCatching { LocalDate.parse(selectedDate) }.getOrDefault(today)
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        val picked = LocalDate.of(year, month + 1, day)
                        if (!picked.isAfter(today)) {
                            selectedDate = picked.toString()
                            scope.launch { load(1, picked.toString(), "custom") }
                        }
                    },
                    current.year,
                    current.monthValue - 1,
                    current.dayOfMonth
                ).apply {
                    datePicker.maxDate = System.currentTimeMillis()
                }.show()
            }
        )

        Spacer(Modifier.height(12.dp))

        if (!loading || rows.isNotEmpty()) {
            RevenueHeroCard(
                cash = totalCash,
                caption = revenueRangeCaption(rangeMode, selectedDate, rangeDays)
            )
            Spacer(Modifier.height(8.dp))
            RevenueMetricRow(
                orderCount = totalOrders,
                grossSales = totalSales,
                gp = totalGp,
                cash = totalCash
            )
        }

        if (totalLegacyGp > 0.0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "GP เดิมก่อนระบบเครดิต: ${money(totalLegacyGp)}",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF7E8), RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFFF2DEB8), RoundedCornerShape(14.dp))
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                color = Color(0xFF8A5E0D),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))
        QueueGoCreditCard(
            userId = auth.user.id,
            token = auth.session.accessToken,
            kind = "merchant",
            onSettings = onSettings
        )

        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                message!!,
                color = if (message!!.contains("แล้ว")) RevenueGreen else QgRed,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .border(1.dp, RevenueCardBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            )
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { exportThirtyDays() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                "ดาวน์โหลดรายงาน 30 วัน (CSV)",
                color = RevenueRed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun RevenuePageHeader(
    shopName: String,
    onBack: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, RevenueCardBorder, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text("←", fontSize = 24.sp, color = Color(0xFF20242A))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "วิเคราะห์ร้านค้า",
                fontSize = MerchantPageTitleSize,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF171A1F)
            )
            Text(
                "รายได้ คำสั่งซื้อ ค่าใช้จ่าย และเครดิตร้านค้า",
                fontSize = 9.5.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF8A919B)
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .widthIn(max = 126.dp)
                .height(40.dp)
                .background(Color.White, RoundedCornerShape(22.dp))
                .border(1.dp, RevenueCardBorder, RoundedCornerShape(22.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QgIcon("store", Modifier.size(16.dp), Color(0xFF2A2E34))
            Spacer(Modifier.width(6.dp))
            Text(
                shopName.ifBlank { "ร้านปัจจุบัน" },
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF2A2E34)
            )
        }
    }
}

@Composable
private fun RevenueRangeRow(
    selected: String,
    onToday: () -> Unit,
    onSevenDays: () -> Unit,
    onThirtyDays: () -> Unit,
    onDate: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RevenueRangeButton(
            label = "วันนี้",
            active = selected == "today",
            onClick = onToday,
            modifier = Modifier.weight(1f)
        )
        RevenueRangeButton(
            label = "7 วันที่ผ่านมา",
            active = selected == "7",
            onClick = onSevenDays,
            modifier = Modifier.weight(1f)
        )
        RevenueRangeButton(
            label = "30 วันที่ผ่านมา",
            active = selected == "30",
            onClick = onThirtyDays,
            modifier = Modifier.weight(1f)
        )
        RevenueRangeButton(
            label = "เลือกวันที่",
            active = selected == "custom",
            onClick = onDate,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RevenueRangeButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .height(44.dp)
            .background(
                if (active) RevenueRed else Color.White,
                RoundedCornerShape(23.dp)
            )
            .border(
                1.dp,
                if (active) RevenueRed else RevenueCardBorder,
                RoundedCornerShape(23.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (active) Color.White else Color(0xFF242930)
        )
    }
}

@Composable
private fun RevenueHeroCard(cash: Double, caption: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(RevenueRed, RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                QgIcon("store", Modifier.size(17.dp), Color.White)
            }
            Spacer(Modifier.width(9.dp))
            Text(
                "เงินสดค่าสินค้าที่ร้านได้รับ",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            money(cash),
            fontSize = 35.sp,
            lineHeight = 39.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            caption,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD8E0)
        )
    }
}

@Composable
private fun RevenueMetricRow(
    orderCount: Int,
    grossSales: Double,
    gp: Double,
    cash: Double
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RevenueMetricCard(
            "คำสั่งซื้อทั้งหมด",
            orderCount.toString(),
            "orders",
            RevenueRed,
            Color(0xFFFFEFF2),
            Modifier.weight(1f)
        )
        RevenueMetricCard(
            "ยอดขายรวม",
            money(grossSales),
            "bag",
            RevenueOrange,
            Color(0xFFFFF3E8),
            Modifier.weight(1f)
        )
        RevenueMetricCard(
            "GP ที่หักจากเครดิต",
            money(gp),
            "orders",
            Color(0xFFFF5B2E),
            Color(0xFFFFF1EC),
            Modifier.weight(1f)
        )
        RevenueMetricCard(
            "เงินสดที่ได้รับ",
            money(cash),
            "store",
            RevenueGreen,
            Color(0xFFECF8F1),
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun RevenueMetricCard(
    label: String,
    value: String,
    icon: String,
    iconColor: Color,
    iconBackground: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .heightIn(min = 126.dp)
            .background(Color.White, RoundedCornerShape(17.dp))
            .border(1.dp, RevenueCardBorder, RoundedCornerShape(17.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Box(
            Modifier
                .size(29.dp)
                .background(iconBackground, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            QgIcon(icon, Modifier.size(16.dp), iconColor)
        }
        Spacer(Modifier.height(9.dp))
        Text(
            label,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xFF858C96),
            fontSize = 7.5.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xFF171B20),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black
        )
    }
}

private fun revenueRangeCaption(
    mode: String,
    selectedDate: String,
    rangeDays: Int
): String = when (mode) {
    "today" -> thaiRevenueDate(selectedDate) + " · บันทึกอัตโนมัติตามออเดอร์จริง"
    "custom" -> thaiRevenueDate(selectedDate) + " · วันที่เลือก"
    else -> "$rangeDays วันที่ผ่านมา · ถึง " + thaiRevenueDate(selectedDate)
}

private fun money(value: Double): String =
    "฿" + String.format(Locale.US, "%,.2f", value)

private fun thaiRevenueDate(date: String): String = runCatching {
    LocalDate.parse(date).format(
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale("th", "TH"))
    )
}.getOrDefault(date)

private fun csvCell(value: String): String =
    "\"" + value.replace("\"", "\"\"") + "\""
