package com.queuego.merchant

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

data class MerchantPrinterSettings(
    val widthMm: Int = 80,
    val bridgeUrl: String = "",
    val autoKitchen: Boolean = false,
    val autoReceipt: Boolean = false
) {
    fun validated(): MerchantPrinterSettings {
        require(widthMm in setOf(58, 80)) { "รองรับกระดาษ 58 หรือ 80 มม. เท่านั้น" }
        if (bridgeUrl.isNotBlank()) validateMerchantPrintBridgeUrl(bridgeUrl)
        return copy(bridgeUrl = bridgeUrl.trim().trimEnd('/'))
    }
}

class MerchantPrinterStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("queuego-merchant-printer-v1", Context.MODE_PRIVATE)

    fun load(): MerchantPrinterSettings = MerchantPrinterSettings(
        widthMm = prefs.getInt("width_mm", 80).takeIf { it in setOf(58, 80) } ?: 80,
        bridgeUrl = prefs.getString("bridge_url", "").orEmpty(),
        autoKitchen = prefs.getBoolean("auto_kitchen", false),
        autoReceipt = prefs.getBoolean("auto_receipt", false)
    )

    fun save(settings: MerchantPrinterSettings) {
        val safe = settings.validated()
        prefs.edit()
            .putInt("width_mm", safe.widthMm)
            .putString("bridge_url", safe.bridgeUrl)
            .putBoolean("auto_kitchen", safe.autoKitchen)
            .putBoolean("auto_receipt", safe.autoReceipt)
            .apply()
    }

    fun wasPrinted(key: String): Boolean =
        prefs.getStringSet("printed_keys", emptySet()).orEmpty().contains(key)

    suspend fun markPrinted(key: String) = withContext(Dispatchers.IO) {
        val old = prefs.getStringSet("printed_keys", emptySet()).orEmpty()
            .associateWith { 0L }.toMutableMap()
        runCatching { JSONObject(prefs.getString("printed_history", "{}").orEmpty()) }.getOrNull()?.let { stored ->
            stored.keys().forEach { savedKey -> old[savedKey] = stored.optLong(savedKey, 0L) }
        }
        val next = merchantPrintHistory(old, key, System.currentTimeMillis())
        val persisted = prefs.edit().putString("printed_history", JSONObject(next).toString())
            .putStringSet("printed_keys", next.keys.toSet()).commit()
        check(persisted) { "ส่งพิมพ์แล้ว แต่บันทึกสถานะไม่สำเร็จ กรุณาตรวจเครื่องพิมพ์ก่อนพิมพ์ซ้ำ" }
    }

}

class MerchantPrintBridge {
    suspend fun print(
        settings: MerchantPrinterSettings,
        type: String,
        text: String,
        shopId: String,
        shopName: String
    ) = withContext(Dispatchers.IO) {
        val safe = settings.validated()
        require(safe.bridgeUrl.isNotBlank()) { "กรุณาตั้งค่า HTTPS Print Bridge ก่อนพิมพ์" }
        require(type in setOf("kitchen", "receipt", "test")) { "ประเภทงานพิมพ์ไม่ถูกต้อง" }
        require(text.isNotBlank()) { "ไม่มีข้อมูลสำหรับพิมพ์" }

        val endpoint = URL(safe.bridgeUrl)
        val connection = endpoint.openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val body = JSONObject()
                .put("type", type)
                .put("width", safe.widthMm.toString())
                .put("text", text)
                .put("shop_id", shopId)
                .put("shop_name", shopName)
                .toString()
                .toByteArray(StandardCharsets.UTF_8)
            connection.outputStream.use { it.write(body) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText()
            }.orEmpty()
            if (status !in 200..299) {
                val reason = runCatching {
                    JSONObject(response).optString("message").ifBlank {
                        JSONObject(response).optString("error")
                    }
                }.getOrNull()?.takeIf { it.isNotBlank() }
                error(reason ?: "Print Bridge ตอบกลับ HTTP " + status)
            }
        } finally {
            connection.disconnect()
        }
    }
}

fun validateMerchantPrintBridgeUrl(value: String) {
    val clean = value.trim()
    val uri = runCatching { URI(clean) }.getOrElse {
        throw IllegalArgumentException("URL ของ Print Bridge ไม่ถูกต้อง")
    }
    require(uri.scheme.equals("https", ignoreCase = true)) {
        "แอป Android อนุญาตเฉพาะ HTTPS Print Bridge"
    }
    require(!uri.host.isNullOrBlank()) { "URL ของ Print Bridge ต้องมีชื่อโฮสต์" }
    require(uri.userInfo.isNullOrBlank()) { "ห้ามใส่รหัสผ่านไว้ใน URL ของ Print Bridge" }
    require(uri.fragment.isNullOrBlank()) { "URL ของ Print Bridge ต้องไม่มี #fragment" }
}

fun merchantKitchenPrintKey(snapshot: PosSnapshot, bill: PosBill): String? {
    val lines = snapshot.linesByOrder[bill.id].orEmpty()
    if (lines.isEmpty() || bill.status == "cancelled" || bill.paymentStatus == "REFUNDED") return null
    val batch = lines.maxOfOrNull { it.batch } ?: 0
    if (bill.kitchenStatus !in setOf("SENT_TO_KITCHEN", "COOKING", "READY", "SERVED")) return null
    return "kitchen:" + snapshot.shopId + ":" + bill.id + ":" + batch
}

fun merchantReceiptPrintKey(snapshot: PosSnapshot, bill: PosBill): String? =
    if (bill.paymentStatus == "PAID") "receipt:" + snapshot.shopId + ":" + bill.id else null

fun merchantKitchenTicket(snapshot: PosSnapshot, bill: PosBill, requestedBatch: Int? = null): String {
    val all = snapshot.linesByOrder[bill.id].orEmpty()
    val batch = requestedBatch ?: (all.maxOfOrNull { it.batch } ?: 0)
    return merchantPrintTicket(snapshot, bill, all.filter { it.batch == batch }, receipt = false)
}

fun merchantReceiptTicket(snapshot: PosSnapshot, bill: PosBill): String =
    merchantPrintTicket(snapshot, bill, snapshot.linesByOrder[bill.id].orEmpty(), receipt = true)

private fun merchantPrintTicket(snapshot: PosSnapshot, bill: PosBill, lines: List<PosLine>, receipt: Boolean): String {
    val separator = "--------------------------------"
    val where = if (bill.type == "DINE_IN") {
        if (bill.tableId == null) "ไม่ระบุโต๊ะ"
        else snapshot.tables.firstOrNull { it.id == bill.tableId }?.label ?: "โต๊ะ"
    } else "รับกลับ"
    val created = bill.createdAt?.let { value -> runCatching {
        java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy HH:mm:ss", java.util.Locale.forLanguageTag("th-TH"))
            .withChronology(java.time.chrono.ThaiBuddhistChronology.INSTANCE)
            .format(java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault()))
    }.getOrNull() } ?: "-"
    return buildString {
        appendLine("QueueGo")
        appendLine(snapshot.shopName)
        appendLine(if (receipt) "ใบเสร็จรับเงิน" else "ใบเข้าครัว")
        appendLine(separator)
        appendLine(bill.number)
        appendLine(where)
        appendLine(created)
        appendLine(separator)
        lines.forEach { line ->
            appendLine(line.quantity.toString() + " x " + line.name)
            if (!line.description.isNullOrBlank()) appendLine("  " + line.description)
        }
        if (receipt) {
            appendLine(separator)
            appendLine("รวม " + merchantPrinterMoney(bill.total))
            if (bill.discount > 0) appendLine("ส่วนลด " + merchantPrinterMoney(bill.discount))
            if (!bill.paymentMethod.isNullOrBlank()) appendLine("ชำระ " + bill.paymentMethod)
            if (bill.paymentMethod == "cash") {
                appendLine("รับเงิน " + merchantPrinterMoney(bill.cashTendered ?: 0.0))
                appendLine("เงินทอน " + merchantPrinterMoney(bill.cashChange ?: 0.0))
            }
        }
        appendLine(separator)
        appendLine()
    }
}

private fun merchantPrinterMoney(value: Double): String =
    String.format(java.util.Locale.US, "%,.2f ฿", value)

internal fun merchantPrintHistory(previous: Map<String, Long>, key: String, printedAt: Long): Map<String, Long> {
    val next = previous.toMutableMap()
    // A device clock correction must not evict the just-acknowledged job.
    next[key] = maxOf(printedAt, (previous.values.maxOrNull() ?: 0L) + 1L)
    return next.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
        .take(300).associate { it.key to it.value }
}
