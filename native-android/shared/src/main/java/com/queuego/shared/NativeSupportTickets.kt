package com.queuego.shared

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class NativeSupportTicket(
    val id: String,
    val category: String,
    val details: String,
    val status: String,
    val orderId: String?,
    val createdAt: String?,
    val adminNote: String?,
    val evidencePath: String?
)

class NativeSupportTicketApi(private val http: QueueGoNativeApi = QueueGoNativeApi()) {
    suspend fun tickets(accessToken: String): List<NativeSupportTicket> {
        val rows = http.array(http.get(
            "qg_support_tickets?select=id,category,details,status,order_id,created_at,admin_note,evidence_path" +
                "&order=created_at.desc&limit=50",
            accessToken
        ))
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                add(NativeSupportTicket(
                    id = row.optString("id"),
                    category = row.optString("category"),
                    details = row.optString("details"),
                    status = row.optString("status"),
                    orderId = row.optString("order_id").takeIf { it.isNotBlank() },
                    createdAt = row.optString("created_at").takeIf { it.isNotBlank() },
                    adminNote = row.optString("admin_note").takeIf { it.isNotBlank() },
                    evidencePath = row.optString("evidence_path").takeIf { it.isNotBlank() }
                ))
            }
        }
    }

    suspend fun create(accessToken: String, orderId: String?, category: String, details: String, evidencePath: String?) {
        val clean = details.trim()
        require(clean.length in 10..2000) { "รายละเอียดต้องมี 10–2000 ตัวอักษร" }
        require(category in NATIVE_SUPPORT_TICKET_CATEGORIES.map { it.first }) { "ประเภทปัญหาไม่ถูกต้อง" }
        http.rpc("qg_create_ticket", accessToken, JSONObject()
            .put("p_ticket_id", UUID.randomUUID().toString())
            .put("p_order_id", orderId ?: JSONObject.NULL)
            .put("p_category", category)
            .put("p_details", clean)
            .put("p_evidence_path", evidencePath ?: JSONObject.NULL))
    }

    suspend fun uploadEvidence(context: Context, authUserId: String, accessToken: String, uri: Uri): String =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val type = resolver.getType(uri).orEmpty()
            require(type in setOf("image/jpeg", "image/png", "image/webp")) {
                "หลักฐานต้องเป็น JPG, PNG หรือ WebP"
            }
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_EVIDENCE_BYTES) { "รูปหลักฐานต้องไม่เกิน 5 MB" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("อ่านรูปหลักฐานไม่สำเร็จ")
            val extension = when (type) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val objectPath = authUserId + "/" + UUID.randomUUID() + "." + extension
            val connection = URL("${QueueGoNativeApi.BASE_URL}/storage/v1/object/qg-evidence/$objectPath")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.doOutput = true
                connection.setRequestProperty("apikey", QueueGoNativeApi.PUBLISHABLE_KEY)
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("Content-Type", type)
                connection.setRequestProperty("x-upsert", "false")
                connection.outputStream.use { it.write(bytes) }
                if (connection.responseCode !in 200..299) error("อัปโหลดหลักฐานไม่สำเร็จ")
            } finally {
                connection.disconnect()
            }
            objectPath
        }

    suspend fun signedEvidenceUrl(accessToken: String, path: String): String = withContext(Dispatchers.IO) {
        require(path.isNotBlank()) { "ไม่พบรูปหลักฐาน" }
        val connection = URL("${QueueGoNativeApi.BASE_URL}/storage/v1/object/sign/qg-evidence/$path")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", QueueGoNativeApi.PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(JSONObject().put("expiresIn", 300).toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) error("เปิดหลักฐานไม่สำเร็จ")
            val signed = JSONObject(response).optString("signedURL").ifBlank { JSONObject(response).optString("signedUrl") }
            require(signed.isNotBlank()) { "เปิดหลักฐานไม่สำเร็จ" }
            if (signed.startsWith("http")) signed else QueueGoNativeApi.BASE_URL + "/storage/v1" + signed
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_EVIDENCE_BYTES = 5 * 1024 * 1024
    }
}

val NATIVE_SUPPORT_TICKET_CATEGORIES = listOf(
    "missing_item" to "สินค้าไม่ครบ",
    "wrong_item" to "สินค้าไม่ตรง",
    "damaged_food" to "อาหารเสียหาย",
    "rider" to "ปัญหาไรเดอร์",
    "merchant" to "ปัญหาร้านค้า",
    "payment" to "การชำระเงิน",
    "cash" to "เงินสด",
    "gp" to "GP",
    "other" to "อื่น ๆ"
)
