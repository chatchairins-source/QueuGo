package com.queuego.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

/** Web Production's Rider earnings credit row: balance + explicit recheck only. */
@Composable
fun QueueGoCreditInline(
    userId: String,
    token: String,
    kind: String
) {
    val scope = rememberCoroutineScope()
    val api = remember { QueueGoNativeApi() }
    val accountKey = "$userId:$kind"
    var balanceMinor by remember(accountKey) { mutableStateOf<Long?>(null) }
    var busy by remember(accountKey) { mutableStateOf(false) }

    suspend fun loadBalance() {
        val summary = api.obj(
            api.rpc(
                "queuego_credit_summary",
                token,
                JSONObject().put("p_kind", kind).put("p_topup", JSONObject.NULL)
            )
        )
        balanceMinor = summary.optLong("available_minor")
    }

    LaunchedEffect(accountKey, token) {
        busy = true
        try {
            loadBalance()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            balanceMinor = null
        } finally {
            busy = false
        }
    }

    fun reload() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                loadBalance()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                balanceMinor = null
            } finally {
                busy = false
            }
        }
    }

    fun money(minor: Long) = "฿" + BigDecimal.valueOf(minor, 2).toPlainString()

    Card(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE4E7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    "เครดิต",
                    color = Color(0xFF7D7478),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    balanceMinor?.let(::money) ?: "--",
                    color = Color(0xFF211E20),
                    fontSize = 19.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Black
                )
            }
            OutlinedButton(
                onClick = ::reload,
                enabled = !busy,
                modifier = Modifier.height(34.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC20D31)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8CCD4))
            ) {
                Text(if (busy) "กำลังเช็ก" else "เช็กยอด", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** The same authenticated ledger/Omise endpoint in Merchant and Rider; never stores keys. */
@Composable
fun QueueGoCreditCard(
    userId: String,
    token: String,
    kind: String,
    onSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { QueueGoNativeApi() }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val accountKey = "$userId:$kind"
    var summary by remember(accountKey) { mutableStateOf<JSONObject?>(null) }
    var amount by remember(accountKey) { mutableStateOf("") }
    var request by remember(accountKey) { mutableStateOf<JSONObject?>(null) }
    var busy by remember(accountKey) { mutableStateOf(false) }
    var message by remember(accountKey) { mutableStateOf<String?>(null) }
    var showTopupForm by remember(accountKey) { mutableStateOf(false) }
    var showHistory by remember(accountKey) { mutableStateOf(true) }
    var showInfo by remember(accountKey) { mutableStateOf(false) }
    suspend fun save(value: JSONObject?) = withContext(Dispatchers.IO) {
        val edit = context.getSharedPreferences("qg-credit-requests-v1", 0).edit()
        if (value == null) edit.remove(accountKey) else edit.putString(accountKey, value.toString())
        check(edit.commit()) { "บันทึกรายการไม่ได้ กรุณาลองใหม่" }
    }
    suspend fun refresh(verify: Boolean = false) {
        suspend fun readSummary() = api.obj(api.rpc("queuego_credit_summary", token, JSONObject().put("p_kind", kind).put("p_topup", request?.optString("id") ?: JSONObject.NULL)))
        var next = readSummary()
        if (request == null) {
            val topups = next.optJSONArray("topups")
            for (i in 0 until (topups?.length() ?: 0)) {
                val item = topups?.optJSONObject(i) ?: continue
                if (item.optString("status") in listOf("created", "pending")) {
                    val restored = JSONObject().put("id", item.getString("id")).put("kind", kind).put("amount_minor", item.getLong("amount_minor"))
                    save(restored)
                    request = restored
                    break
                }
            }
        }
        val pending = request
        val topups = next.optJSONArray("topups")
        var active: JSONObject? = null
        for (i in 0 until (topups?.length() ?: 0)) {
            val item = topups?.optJSONObject(i) ?: continue
            if (item.optString("id") == pending?.optString("id")) active = item
        }
        if (verify && pending != null && active?.optString("status") !in listOf("successful", "failed", "expired")) {
            api.functionPost("queuego-credit", token, pending)
            next = readSummary()
        }
        summary = next
        val settled = next.optJSONArray("topups")
        for (i in 0 until (settled?.length() ?: 0)) {
            val item = settled?.optJSONObject(i) ?: continue
            if (item.optString("id") == request?.optString("id") && item.optString("status") in listOf("successful", "failed", "expired")) message = null
        }
    }
    LaunchedEffect(accountKey, token) {
        try {
            request = withContext(Dispatchers.IO) {
                context.getSharedPreferences("qg-credit-requests-v1", 0).getString(accountKey, null)?.let(::JSONObject)
            }
            refresh()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { message = "ยังตรวจสอบเครดิตไม่ได้" }
    }
    LaunchedEffect(accountKey, token, foreground, request?.optString("id")) {
        while (foreground && request != null) {
            delay(10000)
            if (!busy) try { busy = true; refresh(true) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Keep the last acknowledged balance during a network gap. */ }
            finally { busy = false }
        }
    }
    fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { message = null; action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "ยังยืนยันรายการไม่ได้ กรุณาตรวจรายการเดิมอีกครั้ง" }
            finally { busy = false }
        }
    }
    fun money(minor: Long) = "฿" + BigDecimal.valueOf(minor, 2).toPlainString()
    val data = summary
    val topups = data?.optJSONArray("topups")
    var active: JSONObject? = null
    for (i in 0 until (topups?.length() ?: 0)) {
        val item = topups?.optJSONObject(i) ?: continue
        if (item.optString("id") == request?.optString("id")) active = item
    }

    if (kind == "merchant") {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFFAFB), RoundedCornerShape(22.dp))
                    .border(1.dp, Color(0xFFF1DCE2), RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .background(Color(0xFFF10F3A), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        QgIcon("store", Modifier.size(21.dp), Color.White)
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "เครดิตร้านค้า",
                                fontSize = 17.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF181B20)
                            )
                            Spacer(Modifier.width(3.dp))
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clickable { showInfo = !showInfo },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .border(1.dp, Color(0xFFD9DDE3), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "i",
                                        color = Color(0xFF858C95),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                DropdownMenu(
                                    expanded = showInfo,
                                    onDismissRequest = { showInfo = false }
                                ) {
                                    Column(
                                        Modifier
                                            .width(270.dp)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            if (data?.optBoolean("enabled") == true)
                                                "ระบบเติมเครดิตพร้อมใช้งาน"
                                            else
                                                "ระบบเติมเครดิตยังไม่เปิดใช้งาน",
                                            color = Color(0xFF242A31),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text(
                                            "เครดิตร้านค้าใช้ชำระ GP และค่าบริการ QueueGo ระบบหัก GP จากเครดิตอัตโนมัติตามสถานะออเดอร์ เงินสดค่าสินค้าบันทึกตาม flow จริง และเครดิตไม่สามารถถอนหรือโอนได้",
                                            color = Color(0xFF5F6873),
                                            fontSize = 9.5.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            if (data == null) "—" else "ใช้ได้ ${money(data.optLong("available_minor"))}",
                            fontSize = 25.sp,
                            lineHeight = 29.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF171B20),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { showTopupForm = true },
                        enabled = !busy && data?.optBoolean("enabled") == true,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF10F3A),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFF2E1E6),
                            disabledContentColor = Color(0xFFA77B85)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("＋ เติมเครดิต", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                if (data?.optBoolean("enabled") == true && (showTopupForm || request != null)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFFF0E4E7), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (request == null) {
                            OutlinedTextField(
                                amount,
                                { amount = it.take(12) },
                                label = { Text("จำนวนเงิน 20–100,000 บาท") },
                                enabled = !busy,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                                )
                            )
                        }
                        Button(
                            enabled = !busy && (request != null || amount.isNotBlank()),
                            onClick = {
                                runAction {
                                    val pending = request ?: run {
                                        val minor = BigDecimal(amount).movePointRight(2).longValueExact()
                                        require(minor in 2000L..10000000L)
                                        JSONObject()
                                            .put("id", UUID.randomUUID().toString())
                                            .put("kind", kind)
                                            .put("amount_minor", minor)
                                            .also { save(it); request = it }
                                    }
                                    val result = api.obj(api.functionPost("queuego-credit", token, pending))
                                    message = when (result.optString("status")) {
                                        "successful" -> "เติมเครดิตสำเร็จ"
                                        "failed", "expired" -> "รายการไม่สำเร็จหรือหมดอายุ"
                                        else -> "รอตรวจสอบเงินเข้า ระบบเพิ่มเครดิตให้อัตโนมัติ"
                                    }
                                    refresh()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (request == null) "สร้าง QR PromptPay" else "ตรวจสอบรายการเดิม")
                        }

                        active?.let { item ->
                            Text(
                                when (item.optString("status")) {
                                    "successful" -> "เติมเงินสำเร็จ"
                                    "failed" -> "รายการไม่สำเร็จ"
                                    "expired" -> "รายการหมดอายุ"
                                    "pending" -> "รอชำระเงิน"
                                    else -> "รอสร้าง QR / ตรวจสอบรายการเดิม"
                                },
                                color = Color(0xFF5F6670),
                                fontSize = 10.sp
                            )
                        }
                        val qr = active?.optString("qr_url").orEmpty()
                        if (qr.startsWith("https://api.omise.co/charges/") && active?.optString("status") == "pending") {
                            QgRemoteImage(
                                source = qr,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                            Text(
                                "สแกน QR ตามยอดที่ระบุ · ไม่ต้องส่งสลิป",
                                color = Color(0xFF717984),
                                fontSize = 10.sp
                            )
                        }
                        if (active?.optString("status") in listOf("successful", "failed", "expired")) {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    runAction {
                                        save(null)
                                        request = null
                                        amount = ""
                                        message = null
                                        showTopupForm = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("รายการใหม่") }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    MerchantCreditAction(
                        label = "ตรวจสอบรายการ",
                        enabled = !busy,
                        onClick = { runAction { refresh(true) } },
                        modifier = Modifier.weight(1f)
                    )
                    MerchantCreditAction(
                        label = "ประวัติรายการ",
                        enabled = true,
                        onClick = { showHistory = !showHistory },
                        modifier = Modifier.weight(1f)
                    )
                    MerchantCreditAction(
                        label = "ตั้งค่าการใช้งาน",
                        enabled = onSettings != null,
                        onClick = { onSettings?.invoke() },
                        modifier = Modifier.weight(1f)
                    )
                }

                message?.let {
                    Text(
                        it,
                        color = Color(0xFF5F6670),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            if (showHistory) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFFE9EBEF), RoundedCornerShape(20.dp))
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "รายการล่าสุด",
                            modifier = Modifier.weight(1f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF191D22)
                        )
                        Text(
                            "เครดิตร้านค้า",
                            color = Color(0xFF9AA1AA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Divider(color = Color(0xFFEEF0F2))
                    val entries = data?.optJSONArray("entries")
                    if ((entries?.length() ?: 0) == 0) {
                        Text(
                            "ยังไม่มีรายการเครดิต",
                            modifier = Modifier.padding(15.dp),
                            color = Color(0xFF8B929B),
                            fontSize = 10.sp
                        )
                    } else {
                        val labels = mapOf(
                            "topup" to "เพิ่มเครดิต",
                            "promo" to "เพิ่มเครดิต",
                            "reserve" to "กันเครดิต",
                            "release" to "คืนยอดกัน",
                            "capture" to "หักค่าบริการ",
                            "reversal" to "คืนค่าบริการ"
                        )
                        for (i in 0 until minOf(entries?.length() ?: 0, 10)) {
                            val e = entries?.optJSONObject(i) ?: continue
                            val event = e.optString("event_type")
                            val delta = if (event in listOf("reserve", "release")) {
                                kotlin.math.abs(e.optLong("held_cash_delta") + e.optLong("held_promo_delta"))
                            } else {
                                e.optLong("cash_delta") + e.optLong("promo_delta")
                            }
                            val negative = event in listOf("reserve", "capture")
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 15.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .background(
                                            if (negative) Color(0xFFFFEFF2) else Color(0xFFEAF8F0),
                                            RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (negative) "%" else "+",
                                        color = if (negative) Color(0xFFE31338) else Color(0xFF06A65B),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    labels[event] ?: "รายการเครดิต",
                                    modifier = Modifier.weight(1f),
                                    color = Color(0xFF252B32),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    (if (negative) "-" else "+") + money(kotlin.math.abs(delta)),
                                    color = if (negative) Color(0xFFE31338) else Color(0xFF0AA35C),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            if (i < minOf(entries?.length() ?: 0, 10) - 1) {
                                Divider(
                                    modifier = Modifier.padding(horizontal = 15.dp),
                                    color = Color(0xFFEEF0F2)
                                )
                            }
                        }
                    }
                    Text(
                        "เครดิตใช้ชำระค่าบริการ QueueGo · ถอนหรือโอนไม่ได้",
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                        color = Color(0xFF8A919A),
                        fontSize = 9.sp
                    )
                }
            }
        }
    } else {
        Card(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEE4E7)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "เครดิต Rider",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF30282C)
                )
                if (data != null) {
                    Text("ใช้ได้ ${money(data.optLong("available_minor"))}",
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF211E20)
                    )
                }
                if (data?.optBoolean("enabled") == true) {
                    if (request == null) {
                        OutlinedTextField(
                            amount,
                            { amount = it.take(12) },
                            label = { Text("จำนวนเงิน 20–100,000 บาท") },
                            enabled = !busy,
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                            )
                        )
                    }
                    Button(
                        enabled = !busy && (request != null || amount.isNotBlank()),
                        onClick = {
                            runAction {
                                val pending = request ?: run {
                                    val minor = BigDecimal(amount).movePointRight(2).longValueExact()
                                    require(minor in 2000L..10000000L)
                                    JSONObject()
                                        .put("id", UUID.randomUUID().toString())
                                        .put("kind", kind)
                                        .put("amount_minor", minor)
                                        .also { save(it); request = it }
                                }
                                val result = api.obj(api.functionPost("queuego-credit", token, pending))
                                message = when (result.optString("status")) {
                                    "successful" -> "เติมเครดิตสำเร็จ"
                                    "failed", "expired" -> "รายการไม่สำเร็จหรือหมดอายุ"
                                    else -> "รอตรวจสอบเงินเข้า ระบบเพิ่มเครดิตให้อัตโนมัติ"
                                }
                                refresh()
                            }
                        }
                    ) {
                        Text(if (request == null) "สร้าง QR PromptPay" else "ตรวจสอบรายการเดิม")
                    }
                    active?.let { item ->
                        Text(
                            when (item.optString("status")) {
                                "successful" -> "เติมเงินสำเร็จ"
                                "failed" -> "รายการไม่สำเร็จ"
                                "expired" -> "รายการหมดอายุ"
                                "pending" -> "รอชำระเงิน"
                                else -> "รอสร้าง QR / ตรวจสอบรายการเดิม"
                            }
                        )
                    }
                    val qr = active?.optString("qr_url").orEmpty()
                    if (qr.startsWith("https://api.omise.co/charges/") && active?.optString("status") == "pending") {
                        QgRemoteImage(
                            source = qr,
                            modifier = Modifier.fillMaxWidth().height(240.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                        Text("สแกน QR ตามยอดที่ระบุ · ไม่ต้องส่งสลิป")
                    }
                    if (active?.optString("status") in listOf("successful", "failed", "expired")) {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                runAction {
                                    save(null)
                                    request = null
                                    amount = ""
                                    message = null
                                }
                            }
                        ) { Text("รายการใหม่") }
                    }
                } else {
                    Text(
                        "ระบบเติมเครดิตยังไม่เปิดใช้งาน",
                        color = Color(0xFF72646B),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
                OutlinedButton(
                    enabled = !busy,
                    onClick = { runAction { refresh(true) } },
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB90B30)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8CCD4))
                ) {
                    Text("ตรวจสอบรายการ / รีเฟรชยอด", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                message?.let {
                    Text(it, color = Color(0xFF72646B), fontSize = 10.sp, lineHeight = 14.sp)
                }
                val entries = data?.optJSONArray("entries")
                for (i in 0 until minOf(entries?.length() ?: 0, 10)) {
                    val e = entries?.optJSONObject(i) ?: continue
                    val labels = mapOf(
                        "topup" to "เพิ่มเครดิต",
                        "promo" to "เพิ่มเครดิต",
                        "reserve" to "กันเครดิต",
                        "release" to "คืนยอดกัน",
                        "capture" to "หักค่าบริการ",
                        "reversal" to "คืนค่าบริการ"
                    )
                    Text(
                        "${labels[e.optString("event_type")] ?: "รายการเครดิต"} · " +
                            money(
                                if (e.optString("event_type") in listOf("reserve", "release"))
                                    kotlin.math.abs(e.optLong("held_cash_delta") + e.optLong("held_promo_delta"))
                                else
                                    e.optLong("cash_delta") + e.optLong("promo_delta")
                            ),
                        color = Color(0xFF5F565A),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
                Text(
                    "เครดิตใช้ชำระค่าบริการ QueueGo · ถอนหรือโอนไม่ได้",
                    color = Color(0xFF72646B),
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun MerchantCreditAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (enabled) Color(0xFFE31338) else Color(0xFFA6AAB0)
        )
    }
}
