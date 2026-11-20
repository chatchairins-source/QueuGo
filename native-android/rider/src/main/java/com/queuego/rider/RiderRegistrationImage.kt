package com.queuego.rider

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RIDER_REGISTRATION_IMAGE_INPUT_MAX_BYTES = 8 * 1024 * 1024
private const val RIDER_REGISTRATION_IMAGE_DECODE_MAX_SIDE = 1400
private const val RIDER_REGISTRATION_IMAGE_OUTPUT_MAX_BYTES = 4 * 1024 * 1024
private const val RIDER_REGISTRATION_IMAGE_MAX_BASE64_CHARS =
    ((RIDER_REGISTRATION_IMAGE_OUTPUT_MAX_BYTES + 2) / 3) * 4
private const val RIDER_REGISTRATION_PDF_INPUT_MAX_BYTES = 5 * 1024 * 1024
private const val RIDER_REGISTRATION_PDF_MAX_BASE64_CHARS =
    ((RIDER_REGISTRATION_PDF_INPUT_MAX_BYTES + 2) / 3) * 4

internal suspend fun readRiderRegistrationDocument(context: Context, uri: Uri, allowPdf: Boolean): String = withContext(Dispatchers.IO) {
    val loaded = context.contentResolver.openInputStream(uri)?.use { input ->
        val prefix = ByteArray(5)
        var prefixCount = 0
        while (prefixCount < prefix.size) {
            val count = input.read(prefix, prefixCount, prefix.size - prefixCount)
            if (count < 0) break
            prefixCount += count
        }
        val isPdf = allowPdf &&
            prefixCount == prefix.size &&
            prefix.contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))
        val limit = if (isPdf) {
            RIDER_REGISTRATION_PDF_INPUT_MAX_BYTES
        } else {
            RIDER_REGISTRATION_IMAGE_INPUT_MAX_BYTES
        }
        val output = ByteArrayOutputStream(minOf(limit, 128 * 1024))
        if (prefixCount > 0) output.write(prefix, 0, prefixCount)
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) {
                if (isPdf) "ไฟล์ PDF ต้องมีขนาดไม่เกิน 5 MB" else "ไฟล์รูปต้องมีขนาดไม่เกิน 8 MB"
            }
            output.write(buffer, 0, count)
        }
        output.toByteArray() to isPdf
    } ?: error("ไม่สามารถอ่านไฟล์ได้ กรุณาเลือกไฟล์ใหม่")

    val bytes = loaded.first
    val isPdf = loaded.second
    if (isPdf) {
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        require(encoded.length <= RIDER_REGISTRATION_PDF_MAX_BASE64_CHARS) {
            "ไฟล์ PDF มีขนาดใหญ่เกินกำหนด"
        }
        return@withContext "data:application/pdf;base64," + encoded
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "ไม่สามารถอ่านรูปภาพได้ กรุณาเลือกไฟล์ใหม่" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > RIDER_REGISTRATION_IMAGE_DECODE_MAX_SIDE) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("ไม่สามารถอ่านรูปภาพได้ กรุณาเลือกไฟล์ใหม่")
    try {
        val orientation = runCatching {
            android.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
                .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)
        }.getOrDefault(1)
        val scale = minOf(
            1f,
            RIDER_REGISTRATION_IMAGE_DECODE_MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        )
        val matrix = android.graphics.Matrix().apply {
            when(orientation) {
                2 -> setScale(-1f,1f)
                3 -> setRotate(180f)
                4 -> setScale(1f,-1f)
                5 -> { setRotate(90f); postScale(-1f,1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f,1f) }
                8 -> setRotate(-90f)
            }
            if (scale < 1f) postScale(scale, scale)
        }
        val transformed = android.graphics.Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
        try {
            val output = ByteArrayOutputStream(256 * 1024)
            check(transformed.compress(Bitmap.CompressFormat.JPEG, 78, output)) { "ไม่สามารถอ่านรูปภาพได้" }
            require(output.size() <= RIDER_REGISTRATION_IMAGE_OUTPUT_MAX_BYTES) {
                "รูปหลังปรับขนาดยังมีขนาดใหญ่เกินกำหนด"
            }
            val encoded = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            require(encoded.length <= RIDER_REGISTRATION_IMAGE_MAX_BASE64_CHARS) {
                "รูปหลังเข้ารหัสมีขนาดใหญ่เกินกำหนด"
            }
            "data:image/jpeg;base64," + encoded
        } finally {
            if (transformed !== bitmap && !transformed.isRecycled) transformed.recycle()
        }
    } finally { bitmap.recycle() }
}
