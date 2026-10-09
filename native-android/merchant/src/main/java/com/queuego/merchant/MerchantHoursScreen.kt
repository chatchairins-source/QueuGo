package com.queuego.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "เวลาประเทศไทย เมื่อร้านปิดตามตาราง ลูกค้าจะส่งออเดอร์ใหม่ไม่ได้",
                    color = QgMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
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
                    .border(1.dp, QgLine, RoundedCornerShape(16.dp))
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
                OutlinedTextField(
                    value = specialDay,
                    onValueChange = { specialDay = it.take(10) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("วันที่ YYYY-MM-DD") },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = specialClosed,
                        onCheckedChange = { specialClosed = it }
                    )
                    Text("ปิดทั้งวัน", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = specialOpen,
                        onValueChange = { specialOpen = it.take(5) },
                        modifier = Modifier.weight(1f),
                        enabled = !specialClosed,
                        label = { Text("เปิด") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = specialClose,
                        onValueChange = { specialClose = it.take(5) },
                        modifier = Modifier.weight(1f),
                        enabled = !specialClosed,
                        label = { Text("ปิด") },
                        singleLine = true
                    )
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
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("บันทึกเวลาทำการ", fontWeight = FontWeight.ExtraBold)
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
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            name,
            modifier = Modifier.width(58.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold
        )
        OutlinedTextField(
            value = value.opensAt,
            onValueChange = { onChange(value.copy(opensAt = it.take(5))) },
            modifier = Modifier.weight(1f),
            enabled = !value.isClosed,
            singleLine = true,
            label = { Text("เปิด", fontSize = 9.sp) }
        )
        OutlinedTextField(
            value = value.closesAt,
            onValueChange = { onChange(value.copy(closesAt = it.take(5))) },
            modifier = Modifier.weight(1f),
            enabled = !value.isClosed,
            singleLine = true,
            label = { Text("ปิด", fontSize = 9.sp) }
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Checkbox(
                checked = value.isClosed,
                onCheckedChange = { onChange(value.copy(isClosed = it)) }
            )
            Text("หยุด", color = QgMuted, fontSize = 8.sp)
        }
    }
}
