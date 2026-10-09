package com.queuego.shared

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

private const val CHAT_IMAGE_LIMIT = 5 * 1024 * 1024

private fun InputStream.readChatImage(): ByteArray {
    val result = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(result.size() + count <= CHAT_IMAGE_LIMIT) { "รูปต้องไม่เกิน 5 MB" }
        result.write(buffer, 0, count)
    }
    return result.toByteArray()
}

private fun decodeChatImage(bytes: ByteArray): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "เปิดรูปไม่สำเร็จ" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("เปิดรูปไม่สำเร็จ")
    val orientation = runCatching { ExifInterface(bytes.inputStream())
        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(1)
    val matrix = Matrix().apply {
        when (orientation) {
            2 -> setScale(-1f, 1f)
            3 -> setRotate(180f)
            4 -> setScale(1f, -1f)
            5 -> { setRotate(90f); postScale(-1f, 1f) }
            6 -> setRotate(90f)
            7 -> { setRotate(270f); postScale(-1f, 1f) }
            8 -> setRotate(270f)
        }
    }
    return if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        .also { if (it !== decoded) decoded.recycle() }
}

/** Same actual photo -> JPEG1000px/70% data URI protocol used by Production Rider web. */
suspend fun prepareNativeChatImage(resolver: ContentResolver, uri: Uri, allowedMimeTypes: Set<String>? = null): String = withContext(Dispatchers.IO) {
    val mime = resolver.getType(uri).orEmpty()
    require(mime.startsWith("image/") && (allowedMimeTypes == null || mime in allowedMimeTypes)) { "กรุณาเลือกรูปภาพ JPG, PNG หรือ WebP" }
    val bytes = resolver.openInputStream(uri)?.use { it.readChatImage() } ?: error("เปิดรูปไม่สำเร็จ")
    val decoded = decodeChatImage(bytes)
    val scale = minOf(1f, 1000f / maxOf(decoded.width, decoded.height))
    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(decoded,
        (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true) else decoded
    try {
        val output = ByteArrayOutputStream()
        check(scaled.compress(Bitmap.CompressFormat.JPEG, 70, output)) { "เตรียมรูปไม่สำเร็จ" }
        "__IMG__data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    } finally {
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
    }
}

@Composable
fun NativeChatImage(source: String) {
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(source) { mutableStateOf(false) }
    LaunchedEffect(source) {
        try {
            bitmap = withContext(Dispatchers.IO) {
                val bytes = if (source.startsWith("data:image/")) {
                    require(source.length <= 7 * 1024 * 1024 && source.substringBefore(',').endsWith(";base64"))
                    Base64.decode(source.substringAfter(','), Base64.DEFAULT).also { require(it.size <= CHAT_IMAGE_LIMIT) }
                } else {
                    require(source.startsWith("https://")) { "ลิงก์รูปภาพไม่ถูกต้อง" }
                    val connection = URL(source).openConnection() as HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    try { connection.inputStream.use { it.readChatImage() } } finally { connection.disconnect() }
                }
                decodeChatImage(bytes)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 320.dp), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) Image(image.asImageBitmap(), "รูปที่ส่งในแชต",
            Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
        else if (failed) Text("เปิดรูปไม่สำเร็จ") else CircularProgressIndicator()
    }
}
