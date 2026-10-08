package com.queuetech.queuego.rider

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.queuetech.queuego.core.auth.SecureSessionStore
import com.queuetech.queuego.core.network.QueueGoApi
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RiderEvidencePhotoStore(
    context: Context,
    private val api: QueueGoApi,
    private val sessionStore: SecureSessionStore,
) {
    private val appContext = context.applicationContext

    fun createCaptureUri(): Uri {
        val directory = File(appContext.cacheDir, "rider-evidence").apply { mkdirs() }
        val file = File(directory, "proof-" + UUID.randomUUID().toString() + ".jpg")
        return FileProvider.getUriForFile(
            appContext,
            appContext.packageName + ".files",
            file,
        )
    }

    suspend fun upload(uri: Uri): String = withContext(Dispatchers.IO) {
        val session = sessionStore.read()
            ?: throw IllegalStateException("กรุณาเข้าสู่ระบบใหม่")
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("อ่านรูปหลักฐานไม่สำเร็จ")
        val jpeg = normalizeJpeg(bytes)
        api.uploadEvidenceJpeg(
            accessToken = session.accessToken,
            authUserId = session.authUserId,
            jpegBytes = jpeg,
        )
    }

    private fun normalizeJpeg(original: ByteArray): ByteArray {
        if (original.isEmpty()) throw IllegalArgumentException("รูปหลักฐานว่างเปล่า")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("ไฟล์ที่ถ่ายไม่ใช่รูปภาพที่รองรับ")
        }

        var sample = 1
        while (
            bounds.outWidth / sample > MAX_DIMENSION ||
            bounds.outHeight / sample > MAX_DIMENSION
        ) {
            sample *= 2
        }

        val bitmap = BitmapFactory.decodeByteArray(
            original,
            0,
            original.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: throw IllegalArgumentException("เปิดรูปหลักฐานไม่สำเร็จ")

        try {
            for (quality in intArrayOf(88, 80, 72, 64, 56)) {
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                val bytes = output.toByteArray()
                if (bytes.size <= MAX_BYTES) return bytes
            }
        } finally {
            bitmap.recycle()
        }

        throw IllegalArgumentException("รูปหลักฐานมีขนาดใหญ่เกิน 5 MB กรุณาถ่ายใหม่")
    }

    private companion object {
        const val MAX_DIMENSION = 1920
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
