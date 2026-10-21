package com.queuego.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

/** The same authenticated ledger/Omise endpoint in Merchant and Rider; never stores keys. */
@Composable
fun QueueGoCreditCard(userId: String, token: String, kind: String) {
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
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (kind == "merchant") "เครดิตร้านค้า" else "เครดิต Rider", style = MaterialTheme.typography.titleMedium)
            val data = summary
            if (data != null) {
                Text("ใช้ได้ ${money(data.optLong("available_minor"))}")
            }
            if (data?.optBoolean("enabled") == true) {
                if (request == null) {
                    OutlinedTextField(amount, { amount = it.take(12) }, label = { Text("จำนวนเงิน 20–100,000 บาท") }, enabled = !busy, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                }
                Button(enabled = !busy && (request != null || amount.isNotBlank()), onClick = {
                    runAction {
                        val pending = request ?: run {
                            val minor = BigDecimal(amount).movePointRight(2).longValueExact()
                            require(minor in 2000L..10000000L)
                            JSONObject().put("id", UUID.randomUUID().toString()).put("kind", kind).put("amount_minor", minor).also { save(it); request = it }
                        }
                        val result = api.obj(api.functionPost("queuego-credit", token, pending))
                        message = when (result.optString("status")) {
                            "successful" -> "เติมเครดิตสำเร็จ"
                            "failed", "expired" -> "รายการไม่สำเร็จหรือหมดอายุ"
                            else -> "รอตรวจสอบเงินเข้า ระบบเพิ่มเครดิตให้อัตโนมัติ"
                        }
                        refresh()
                    }
                }) { Text(if (request == null) "สร้าง QR PromptPay" else "ตรวจสอบรายการเดิม") }
                val topups = data.optJSONArray("topups")
                var active: JSONObject? = null
                for (i in 0 until (topups?.length() ?: 0)) {
                    val item = topups?.optJSONObject(i) ?: continue
                    if (item.optString("id") == request?.optString("id")) active = item
                }
                active?.let { item ->
                    Text(when (item.optString("status")) {
                        "successful" -> "เติมเงินสำเร็จ"
                        "failed" -> "รายการไม่สำเร็จ"
                        "expired" -> "รายการหมดอายุ"
                        "pending" -> "รอชำระเงิน"
                        else -> "รอสร้าง QR / ตรวจสอบรายการเดิม"
                    })
                }
                val qr = active?.optString("qr_url").orEmpty()
                if (qr.startsWith("https://api.omise.co/charges/") && active?.optString("status") == "pending") {
                    QgRemoteImage(source = qr, modifier = Modifier.fillMaxWidth().height(240.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                    Text("สแกน QR ตามยอดที่ระบุ · ไม่ต้องส่งสลิป")
                }
                if (active?.optString("status") in listOf("successful", "failed", "expired")) {
                    OutlinedButton(enabled = !busy, onClick = { runAction { save(null); request = null; amount = ""; message = null } }) { Text("รายการใหม่") }
                }
            } else Text("ระบบเติมเครดิตยังไม่เปิดใช้งาน")
            OutlinedButton(enabled = !busy, onClick = { runAction { refresh(true) } }) { Text("ตรวจสอบรายการ / รีเฟรชยอด") }
            message?.let { Text(it) }
            val entries = data?.optJSONArray("entries")
            for (i in 0 until minOf(entries?.length() ?: 0, 10)) {
                val e = entries?.optJSONObject(i) ?: continue
                val labels = mapOf("topup" to "เพิ่มเครดิต", "promo" to "เพิ่มเครดิต", "reserve" to "กันเครดิต", "release" to "คืนยอดกัน", "capture" to "หักค่าบริการ", "reversal" to "คืนค่าบริการ")
                Text("${labels[e.optString("event_type")] ?: "รายการเครดิต"} · ${money(if (e.optString("event_type") in listOf("reserve", "release")) kotlin.math.abs(e.optLong("held_cash_delta") + e.optLong("held_promo_delta")) else e.optLong("cash_delta") + e.optLong("promo_delta"))}")
            }
            Text("เครดิตใช้ชำระค่าบริการ QueueGo · ถอนหรือโอนไม่ได้", style = MaterialTheme.typography.bodySmall)
        }
    }
}
