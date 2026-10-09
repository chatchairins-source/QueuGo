package com.queuego.merchant

import android.content.Context
import android.net.Uri
import com.queuego.shared.NativeAuth
import com.queuego.shared.QueueGoNativeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal suspend fun uploadMerchantGpSlip(
    context: Context,
    auth: NativeAuth,
    uri: Uri,
    date: String
): String = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri).orEmpty().lowercase()
    val extension = when (mime) {
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        "application/pdf" -> "pdf"
        else -> error("รองรับ JPG, PNG, WebP หรือ PDF")
    }

    val payload = resolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > 5 * 1024 * 1024) error("สลิปต้องไม่เกิน 5 MB")
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    } ?: error("อ่านไฟล์สลิปไม่สำเร็จ")

    val safeDate = date.filter { it.isDigit() || it == '-' }
    val objectPath = auth.session.authUserId + "/" + safeDate + "-" + UUID.randomUUID() + "." + extension
    val connection = URL(
        QueueGoNativeApi.BASE_URL + "/storage/v1/object/gp-slips/" + objectPath
    ).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.doOutput = true
        connection.setRequestProperty("apikey", QueueGoNativeApi.PUBLISHABLE_KEY)
        connection.setRequestProperty("Authorization", "Bearer " + auth.session.accessToken)
        connection.setRequestProperty("Content-Type", mime)
        connection.outputStream.use { it.write(payload) }

        val code = connection.responseCode
        if (code !in 200..299) {
            val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val message = runCatching {
                val json = JSONObject(body)
                json.optString("message").ifBlank { json.optString("error") }
            }.getOrNull().orEmpty().ifBlank { "Storage HTTP $code" }
            error(message)
        }
    } finally {
        connection.disconnect()
    }
    objectPath
}
