package com.queuego.merchant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

internal suspend fun uploadMerchantImage(
    context: Context,
    auth: NativeAuth,
    uri: Uri,
    kind: String
): String = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri).orEmpty().lowercase()
    require(mime in setOf("image/jpeg", "image/png", "image/webp")) {
        "รองรับ JPG, PNG หรือ WebP เท่านั้น"
    }

    val source = resolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > 12 * 1024 * 1024) error("รูปต้นฉบับต้องไม่เกิน 12 MB")
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    } ?: error("อ่านไฟล์รูปไม่สำเร็จ")

    val decoded = BitmapFactory.decodeByteArray(source, 0, source.size)
        ?: error("ไฟล์รูปไม่ถูกต้อง")
    val maxSide = maxOf(decoded.width, decoded.height)
    val resized = if (maxSide > 1600) {
        val scale = 1600.0 / maxSide.toDouble()
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else decoded

    fun compress(quality: Int): ByteArray {
        val output = ByteArrayOutputStream()
        check(resized.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
            "ปรับขนาดรูปไม่สำเร็จ"
        }
        return output.toByteArray()
    }

    var payload = compress(82)
    if (payload.size > 3 * 1024 * 1024) payload = compress(70)
    if (payload.size > 3 * 1024 * 1024) error("รูปหลังปรับขนาดยังใหญ่เกิน 3 MB")

    if (resized !== decoded) resized.recycle()
    decoded.recycle()

    val safeKind = kind.lowercase().replace(Regex("[^a-z0-9-]"), "").ifBlank { "image" }
    val objectPath = auth.session.authUserId + "/" + safeKind + "-" + UUID.randomUUID() + ".jpg"
    val connection = URL(
        QueueGoNativeApi.BASE_URL + "/storage/v1/object/merchant-media/" + objectPath
    ).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.doOutput = true
        connection.setRequestProperty("apikey", QueueGoNativeApi.PUBLISHABLE_KEY)
        connection.setRequestProperty("Authorization", "Bearer " + auth.session.accessToken)
        connection.setRequestProperty("Content-Type", "image/jpeg")
        connection.setRequestProperty("cache-control", "3600")
        connection.outputStream.use { it.write(payload) }

        val code = connection.responseCode
        if (code !in 200..299) {
            val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val message = runCatching {
                JSONObject(body).optString("message").ifBlank { JSONObject(body).optString("error") }
            }.getOrNull().orEmpty().ifBlank { "Storage HTTP $code" }
            error(message)
        }
    } finally {
        connection.disconnect()
    }

    QueueGoNativeApi.BASE_URL + "/storage/v1/object/public/merchant-media/" + objectPath
}
