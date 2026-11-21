package com.queuego.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MerchantPromotionScreen(
    auth: NativeAuth,
    shop: MerchantShop,
    api: MerchantApi
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cache = remember(context.applicationContext) {
        MerchantPromotionCache(context.applicationContext)
    }
    var rows by remember(shop.id) { mutableStateOf<List<MerchantPromotion>>(emptyList()) }
    var filter by remember { mutableStateOf("all") }
    var loading by remember { mutableStateOf(true) }
    var creating by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("7") }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun refresh(force: Boolean = false) {
        loading = rows.isEmpty()
        val cached = if (!force && rows.isEmpty()) {
            withContext(Dispatchers.IO) {
                cache.read(auth.user.id, shop.id)
            }
        } else null
        if (cached != null) {
            rows = cached.rows
            loading = false
            if (cached.fresh) {
                message = null
                return
            }
        }
        try {
            val fresh = api.loadPromotions(auth, shop.id)
            rows = fresh
            withContext(Dispatchers.IO) {
                cache.write(auth.user.id, shop.id, fresh)
            }
            message = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (rows.isEmpty()) message = failure.message ?: "โหลดโปรโมชั่นไม่สำเร็จ"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(shop.id) { refresh() }

    val visible = rows.filter {
        filter == "all" || it.status.equals(filter, ignoreCase = true)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PromotionFilter("ทั้งหมด", "all", filter) { filter = it }
            PromotionFilter("กำลังใช้งาน", "active", filter) { filter = it }
            PromotionFilter("รออนุมัติ", "pending", filter) { filter = it }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { creating = !creating },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (creating) "ปิดฟอร์มสร้างโปรโมชั่น" else "สร้างโปรโมชั่นใหม่")
        }

        if (creating) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE6E8EB), RoundedCornerShape(16.dp))
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
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        if (amount == null || period == null) {
                            message = "กรุณากรอกงบสูงสุดและจำนวนวันให้ถูกต้อง"
                        } else {
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
                                    refresh(force = true)
                                }.onFailure {
                                    message = it.message ?: "บันทึกโปรโมชั่นไม่สำเร็จ"
                                }
                                loading = false
                            }
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ส่งคำขอโปรโมชั่น", fontWeight = FontWeight.ExtraBold) }
            }
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
            loading && rows.isEmpty() -> Unit

            visible.isEmpty() -> Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE6E8EB), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ยังไม่มีโปรโมชั่น", fontWeight = FontWeight.ExtraBold)
                    Text(
                        "สร้างโปรโมชั่นเพื่อเพิ่มยอดขายและดึงดูดลูกค้า",
                        color = QgMuted,
                        fontSize = 10.sp
                    )
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
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "งบสูงสุด ฿" + "%.0f".format(promo.maxBudget) + " · " + promo.periodDays + " วัน",
                        color = QgMuted,
                        fontSize = 9.sp
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
