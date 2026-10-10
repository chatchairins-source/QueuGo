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
    val widthMm: Int = 58,
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
        widthMm = prefs.getInt("width_mm", 58).takeIf { it in setOf(58, 80) } ?: 58,
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

    fun markPrinted(key: String) {
        val old = prefs.getStringSet("printed_keys", emptySet()).orEmpty()
        val next = (old + key).toList().takeLast(300).toSet()
        prefs.edit().putStringSet("printed_keys", next).apply()
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
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val body = JSONObject()
                .put("type", type)
                .put("width", safe.widthMm)
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
    if (lines.isEmpty()) return null
    val batch = lines.maxOfOrNull { it.batch } ?: 0
    if (bill.kitchenStatus !in setOf("SENT_TO_KITCHEN", "COOKING", "READY", "SERVED")) return null
    return "kitchen:" + snapshot.shopId + ":" + bill.id + ":" + batch
}

fun merchantReceiptPrintKey(snapshot: PosSnapshot, bill: PosBill): String? =
    if (bill.paymentStatus == "PAID") "receipt:" + snapshot.shopId + ":" + bill.id else null

fun merchantKitchenTicket(snapshot: PosSnapshot, bill: PosBill): String {
    val all = snapshot.linesByOrder[bill.id].orEmpty()
    val batch = all.maxOfOrNull { it.batch } ?: 0
    val lines = all.filter { it.batch == batch }
    val table = snapshot.tables.firstOrNull { it.id == bill.tableId }?.label
    return buildString {
        appendLine(snapshot.shopName)
        appendLine("ใบครัว · " + bill.number)
        appendLine(
            when (bill.type) {
                "DINE_IN" -> "ทานที่ร้าน · " + (table ?: "ไม่ระบุโต๊ะ")
                else -> "รับกลับ"
            }
        )
        appendLine("ชุดครัว #" + batch)
        appendLine("--------------------------------")
        lines.forEach { line ->
            appendLine(line.quantity.toString() + " x " + line.name)
            if (!line.description.isNullOrBlank()) appendLine("  " + line.description)
        }
        appendLine("--------------------------------")
        appendLine("QueueGo POS")
    }.trimEnd()
}

fun merchantReceiptTicket(snapshot: PosSnapshot, bill: PosBill): String {
    val lines = snapshot.linesByOrder[bill.id].orEmpty()
    val table = snapshot.tables.firstOrNull { it.id == bill.tableId }?.label
    val payment = when (bill.paymentMethod) {
        "cash" -> "เงินสด"
        "bank_transfer" -> "โอนเงิน"
        "promptpay" -> "พร้อมเพย์"
        "card" -> "บัตร"
        "other" -> "อื่น ๆ"
        else -> bill.paymentMethod ?: "-"
    }
    return buildString {
        appendLine(snapshot.shopName)
        appendLine("ใบเสร็จ · " + bill.number)
        appendLine(
            when (bill.type) {
                "DINE_IN" -> "ทานที่ร้าน · " + (table ?: "ไม่ระบุโต๊ะ")
                else -> "รับกลับ"
            }
        )
        appendLine("--------------------------------")
        lines.forEach { line ->
            appendLine(line.quantity.toString() + " x " + line.name + "  ฿" + merchantPrinterMoney(line.total))
        }
        appendLine("--------------------------------")
        appendLine("ยอดก่อนลด  ฿" + merchantPrinterMoney(bill.subtotal))
        if (bill.discount > 0) appendLine("ส่วนลด      -฿" + merchantPrinterMoney(bill.discount))
        appendLine("ยอดสุทธิ     ฿" + merchantPrinterMoney(bill.total))
        appendLine("ชำระด้วย     " + payment)
        if (bill.paymentMethod == "cash" && bill.cashTendered != null) {
            appendLine("รับเงิน       ฿" + merchantPrinterMoney(bill.cashTendered))
            appendLine("เงินทอน      ฿" + merchantPrinterMoney(bill.cashChange ?: 0.0))
        }
        appendLine("--------------------------------")
        appendLine("ขอบคุณที่ใช้บริการ")
        appendLine("QueueGo POS")
    }.trimEnd()
}

private fun merchantPrinterMoney(value: Double): String =
    String.format(java.util.Locale.US, "%,.2f", value.coerceAtLeast(0.0))
