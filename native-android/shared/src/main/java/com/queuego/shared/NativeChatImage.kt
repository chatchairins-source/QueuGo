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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val CHAT_IMAGE_LIMIT = 5 * 1024 * 1024
private const val CHAT_IMAGE_MAX_BASE64_CHARS = ((CHAT_IMAGE_LIMIT + 2) / 3) * 4
private const val CHAT_IMAGE_MAX_SIDE = 1280
private const val CHAT_IMAGE_DISK_CACHE_MAX_BYTES = 64L * 1024L * 1024L
private val chatImageDecodeGate = Semaphore(2)

private fun nativeChatImageCacheName(source: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(source.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun nativeChatImageCacheDir(context: android.content.Context): File =
    File(context.noBackupFilesDir, "queuego_chat_images_v1").also { runCatching { it.mkdirs() } }

private fun readNativeChatImageCache(context: android.content.Context, source: String): ByteArray? {
    val file = File(nativeChatImageCacheDir(context), nativeChatImageCacheName(source))
    if (!file.isFile || file.length() !in 1L..CHAT_IMAGE_LIMIT.toLong()) return null
    return runCatching {
        file.inputStream().use { it.readChatImage() }.also {
            runCatching { file.setLastModified(System.currentTimeMillis()) }
        }
    }.getOrNull()
}

private fun writeNativeChatImageCache(context: android.content.Context, source: String, bytes: ByteArray) {
    if (bytes.isEmpty() || bytes.size > CHAT_IMAGE_LIMIT) return
    val directory = nativeChatImageCacheDir(context)
    val target = File(directory, nativeChatImageCacheName(source))
    val temp = File(directory, target.name + ".tmp")
    runCatching {
        temp.outputStream().use { it.write(bytes) }
        if (!temp.renameTo(target)) {
            target.outputStream().use { it.write(bytes) }
            temp.delete()
        }
        target.setLastModified(System.currentTimeMillis())
        var total = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.sumOf { it.length().coerceAtLeast(0L) } ?: 0L
        if (total > CHAT_IMAGE_DISK_CACHE_MAX_BYTES) {
            for (file in directory.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".tmp") }.sortedBy { it.lastModified() }) {
                if (total <= CHAT_IMAGE_DISK_CACHE_MAX_BYTES * 3L / 4L) break
                val size = file.length().coerceAtLeast(0L)
                if (runCatching { file.delete() }.getOrDefault(false)) total -= size
            }
        }
    }.onFailure { runCatching { temp.delete() } }
}

private fun InputStream.readChatImage(limit: Int = CHAT_IMAGE_LIMIT): ByteArray {
    val result = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(result.size() + count <= limit) { "รูปต้องไม่เกิน ${limit / (1024 * 1024)} MB" }
        result.write(buffer, 0, count)
    }
    return result.toByteArray()
}

private fun decodeChatImage(bytes: ByteArray): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "เปิดรูปไม่สำเร็จ" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > CHAT_IMAGE_MAX_SIDE) sample *= 2
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
suspend fun prepareNativeChatImage(resolver: ContentResolver, uri: Uri, allowedMimeTypes: Set<String>? = null,
    maxInputBytes: Int = CHAT_IMAGE_LIMIT): String = withContext(Dispatchers.IO) {
    val mime = resolver.getType(uri).orEmpty()
    require(mime.startsWith("image/") && (allowedMimeTypes == null || mime in allowedMimeTypes)) { "กรุณาเลือกรูปภาพ JPG, PNG หรือ WebP" }
    val bytes = resolver.openInputStream(uri)?.use { it.readChatImage(maxInputBytes) } ?: error("เปิดรูปไม่สำเร็จ")
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
    val context = LocalContext.current.applicationContext
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(source) { mutableStateOf(false) }
    LaunchedEffect(source) {
        try {
            bitmap = withContext(Dispatchers.IO) {
                chatImageDecodeGate.withPermit {
                    val bytes = if (source.startsWith("data:image/")) {
                        val comma = source.indexOf(',')
                        require(comma in 1..128 && source.substring(0, comma).endsWith(";base64")) {
                            "รูปในแชตมีรูปแบบไม่ถูกต้อง"
                        }
                        val encodedLength = source.length - comma - 1
                        require(encodedLength in 1..CHAT_IMAGE_MAX_BASE64_CHARS) {
                            "รูปต้องไม่เกิน " + (CHAT_IMAGE_LIMIT / (1024 * 1024)) + " MB"
                        }
                        Base64.decode(source.substring(comma + 1), Base64.DEFAULT).also {
                            require(it.size <= CHAT_IMAGE_LIMIT)
                        }
                    } else {
                        require(source.startsWith("https://")) { "ลิงก์รูปภาพไม่ถูกต้อง" }
                        readNativeChatImageCache(context, source) ?: run {
                            val connection = URL(source).openConnection() as HttpURLConnection
                            connection.connectTimeout = 10000
                            connection.readTimeout = 10000
                            try {
                                connection.inputStream.use { it.readChatImage() }.also {
                                    writeNativeChatImageCache(context, source, it)
                                }
                            } finally {
                                connection.disconnect()
                            }
                        }
                    }
                    decodeChatImage(bytes)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    DisposableEffect(bitmap) {
        val owned = bitmap
        onDispose {
            if (owned != null && !owned.isRecycled) owned.recycle()
        }
    }

    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 320.dp), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) Image(image.asImageBitmap(), "รูปที่ส่งในแชต",
            Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
        else if (failed) Text("เปิดรูปไม่สำเร็จ")
    }
}
