package com.queuego.merchant

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.QgIcon
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import kotlinx.coroutines.launch

@Composable
internal fun MerchantPromotionScreen(
    auth: NativeAuth,
    shop: MerchantShop,
    api: MerchantApi,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var rows by remember(shop.id) { mutableStateOf<List<MerchantPromotion>>(emptyList()) }
    var filter by remember { mutableStateOf("all") }
    var loading by remember { mutableStateOf(true) }
    var creating by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("7") }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        runCatching { api.loadPromotions(auth, shop.id) }
            .onSuccess { rows = it; message = null }
            .onFailure { message = it.message ?: "โหลดโปรโมชั่นไม่สำเร็จ" }
        loading = false
    }

    LaunchedEffect(shop.id) { refresh() }

    val visible = rows.filter {
        filter == "all" || it.status.equals(filter, ignoreCase = true)
    }

    if (creating) {
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
                    onClick = { creating = false },
                    modifier = Modifier.height(38.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) { Text("กลับ") }
                Spacer(Modifier.width(10.dp))
                Text("สร้างโปรโมชั่น", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(17.dp))
                    .border(1.dp, Color(0xFFE6E8EB), RoundedCornerShape(17.dp))
                    .padding(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ชื่อ/ประเภทโปรโมชั่น") },
                    placeholder = { Text("เช่น โปรโมตร้านประจำสัปดาห์") },
                    singleLine = true
                )
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(
                        value = budget,
                        onValueChange = { budget = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        modifier = Modifier.weight(1f),
                        label = { Text("งบสูงสุด") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = days,
                        onValueChange = { days = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f),
                        label = { Text("จำนวนวัน") },
                        singleLine = true
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val amount = budget.toDoubleOrNull()
                        val period = days.toIntOrNull()
                        when {
                            title.trim().isBlank() -> message = "กรุณากรอกชื่อ/ประเภทโปรโมชั่น"
                            amount == null || amount < 0.0 || period == null || period < 1 ->
                                message = "กรุณากรอกงบสูงสุดและจำนวนวันให้ถูกต้อง"
                            else -> {
                                scope.launch {
                                    loading = true
                                    runCatching {
                                        api.createPromotion(auth, shop, title, amount, period)
                                    }.onSuccess {
                                        title = ""
                                        budget = ""
                                        days = "7"
                                        creating = false
                                        message = "ส่งคำขอโปรโมชั่นแล้ว"
                                        refresh()
                                    }.onFailure {
                                        message = it.message ?: "บันทึกโปรโมชั่นไม่สำเร็จ"
                                    }
                                    loading = false
                                }
                            }
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("ส่งคำขอโปรโมชั่น", fontWeight = FontWeight.ExtraBold)
                }
            }
            if (!message.isNullOrBlank()) {
                Text(
                    message!!,
                    color = QgRed,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
            Spacer(Modifier.height(28.dp))
        }
        return
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
            Text("โปรโมชั่น", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PromotionFilter("ทั้งหมด", "all", filter) { filter = it }
            PromotionFilter("กำลังใช้งาน", "active", filter) { filter = it }
            PromotionFilter("รออนุมัติ", "pending", filter) { filter = it }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { creating = true; message = null },
            modifier = Modifier.fillMaxWidth().heightIn(min = 38.dp)
        ) {
            Text("สร้างโปรโมชั่นใหม่")
        }

        if (!message.isNullOrBlank()) {
            Text(
                message!!,
                color = if (message!!.contains("แล้ว")) Color(0xFF0A9660) else QgRed,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }

        when {
            loading && rows.isEmpty() -> Box(
                Modifier.fillMaxWidth().height(140.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            visible.isEmpty() -> Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 340.dp)
                    .padding(horizontal = 25.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.size(88.dp),
                    contentAlignment = Alignment.Center
                ) {
                    QgIcon("megaphone", Modifier.size(88.dp), QgRed)
                }
                Spacer(Modifier.height(12.dp))
                Text("ยังไม่มีโปรโมชั่น", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "สร้างโปรโมชั่นเพื่อเพิ่มยอดขาย\nและดึงดูดลูกค้า",
                    color = QgMuted,
                    fontSize = 11.sp,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { creating = true; message = null },
                    modifier = Modifier.widthIn(max = 220.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("＋ สร้างโปรโมชั่น", fontWeight = FontWeight.Bold)
                }
            }

            else -> visible.forEach { promo ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE6E8EB), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Text(promo.title, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        promo.status,
                        color = QgMuted,
                        fontSize = 10.sp
                    )

                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PromotionFilter(
    label: String,
    value: String,
    selected: String,
    onSelect: (String) -> Unit
) {
    val active = value == selected
    Box(
        Modifier
            .background(
                if (active) Color(0xFFFFF0F3) else Color.White,
                RoundedCornerShape(20.dp)
            )
            .border(
                1.dp,
                if (active) QgRed else Color(0xFFE7E9EC),
                RoundedCornerShape(20.dp)
            )
            .clickable { onSelect(value) }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (active) Color(0xFFD90D2D) else Color(0xFF777E87),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
