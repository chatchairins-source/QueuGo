package com.queuego.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgLine
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

private val merchantDayNames =
    listOf("อาทิตย์", "จันทร์", "อังคาร", "พุธ", "พฤหัส", "ศุกร์", "เสาร์")

@Composable
internal fun MerchantHoursScreen(
    auth: NativeAuth,
    shop: MerchantShop,
    api: MerchantApi,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now(ZoneId.of("Asia/Bangkok")).toString() }
    var days by remember(shop.id) {
        mutableStateOf((0..6).map { MerchantBusinessDay(it, "06:00", "22:00", false) })
    }
    var specialDay by remember(shop.id) { mutableStateOf("") }
    var specialClosed by remember(shop.id) { mutableStateOf(true) }
    var specialOpen by remember(shop.id) { mutableStateOf("06:00") }
    var specialClose by remember(shop.id) { mutableStateOf("22:00") }
    var loading by remember(shop.id) { mutableStateOf(true) }
    var saving by remember(shop.id) { mutableStateOf(false) }
    var message by remember(shop.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(shop.id) {
        loading = true
        runCatching { api.loadBusinessHours(auth, shop.id, today) }
            .onSuccess { state ->
                days = state.days
                state.special?.let {
                    specialDay = it.day.orEmpty()
                    specialClosed = it.isClosed
                    specialOpen = it.opensAt
                    specialClose = it.closesAt
                }
                message = null
            }
            .onFailure { message = it.message ?: "โหลดเวลาทำการไม่สำเร็จ" }
        loading = false
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
                    "เวลาทำการและวันหยุด",
                    color = Color(0xFF183D32),
                    fontSize = 19.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "เวลาประเทศไทย เมื่อร้านปิดตามตาราง ลูกค้าจะส่งออเดอร์ใหม่ไม่ได้",
                    color = Color(0xFF688276),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        if (loading) {
            Box(
                Modifier.fillMaxWidth().height(140.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE7E9EC), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                days.forEach { day ->
                    MerchantHoursDayRow(
                        name = merchantDayNames.getOrElse(day.weekday) { "วัน" },
                        value = day,
                        onChange = { changed ->
                            days = days.map { if (it.weekday == changed.weekday) changed else it }
                        }
                    )
                    if (day.weekday != 6) {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(QgLine)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "วันหยุดหรือเวลาพิเศษ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(8.dp))
                Text("วันที่", color = Color(0xFF628072), fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                MerchantCompactInput(
                    value = specialDay,
                    onValueChange = { specialDay = it.filter { ch -> ch.isDigit() || ch == '-' }.take(10) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    fontSize = 12.sp,
                    placeholder = "YYYY-MM-DD"
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        Modifier.weight(1f).height(40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = specialClosed,
                            onCheckedChange = { specialClosed = it }
                        )
                        Text("ปิดทั้งวัน", color = Color(0xFF628072), fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("เปิด", color = Color(0xFF628072), fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        MerchantCompactInput(
                            value = specialOpen,
                            onValueChange = { specialOpen = compactTimeValue(it) },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            fontSize = 11.sp,
                            enabled = !specialClosed
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("ปิด", color = Color(0xFF628072), fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        MerchantCompactInput(
                            value = specialClose,
                            onValueChange = { specialClose = compactTimeValue(it) },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            fontSize = 11.sp,
                            enabled = !specialClosed
                        )
                    }
                }

                Button(
                    onClick = {
                        if (!saving) {
                            saving = true
                            message = null
                            scope.launch {
                                val special = if (specialDay.isBlank()) null else MerchantSpecialHours(
                                    day = specialDay,
                                    opensAt = specialOpen,
                                    closesAt = specialClose,
                                    isClosed = specialClosed
                                )
                                runCatching { api.saveBusinessHours(auth, days, special) }
                                    .onSuccess {
                                        message = "บันทึกเวลาทำการแล้ว"
                                        onSaved()
                                    }
                                    .onFailure { message = it.message ?: "บันทึกเวลาทำการไม่สำเร็จ" }
                                saving = false
                            }
                        }
                    },
                    enabled = !loading && !saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 14.dp),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Text("บันทึกเวลาทำการ", fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        if (!message.isNullOrBlank()) {
            Text(
                message!!,
                color = if (message!!.contains("แล้ว")) Color(0xFF0A9660) else QgRed,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp)
            )
        } else {
            Text(
                "เลือกวันหยุดพิเศษแล้วบันทึกเพื่อเพิ่มหรือแก้ไขวันนั้น",
                color = QgMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp)
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun MerchantHoursDayRow(
    name: String,
    value: MerchantBusinessDay,
    onChange: (MerchantBusinessDay) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            name,
            modifier = Modifier.width(70.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold
        )
        MerchantCompactInput(
            value = value.opensAt,
            onValueChange = { onChange(value.copy(opensAt = compactTimeValue(it))) },
            modifier = Modifier.weight(1f).height(38.dp),
            fontSize = 11.sp,
            enabled = !value.isClosed
        )
        MerchantCompactInput(
            value = value.closesAt,
            onValueChange = { onChange(value.copy(closesAt = compactTimeValue(it))) },
            modifier = Modifier.weight(1f).height(38.dp),
            fontSize = 11.sp,
            enabled = !value.isClosed
        )
        Column(
            Modifier.width(50.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Checkbox(
                checked = value.isClosed,
                onCheckedChange = { onChange(value.copy(isClosed = it)) }
            )
            Text("หยุด", color = Color(0xFF677D70), fontSize = 10.sp)
        }
    }
}

@Composable
private fun MerchantCompactInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit,
    enabled: Boolean = true,
    placeholder: String = ""
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(
            color = if (enabled) Color(0xFF202329) else Color(0xFF9AA09D),
            fontSize = fontSize
        ),
        cursorBrush = SolidColor(QgRed),
        modifier = modifier
            .background(
                if (enabled) Color.White else Color(0xFFF7F8F9),
                RoundedCornerShape(8.dp)
            )
            .border(1.dp, Color(0xFFD7E7DC), RoundedCornerShape(8.dp)),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxSize().padding(horizontal = 7.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isBlank() && placeholder.isNotBlank()) {
                    Text(placeholder, color = QgMuted, fontSize = fontSize)
                }
                inner()
            }
        }
    )
}

private fun compactTimeValue(raw: String): String =
    raw.filter { it.isDigit() || it == ':' }.take(5)
