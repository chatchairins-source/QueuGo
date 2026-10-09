package com.queuego.rider

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun readRiderRegistrationDocument(context: Context, uri: Uri, allowPdf: Boolean): String = withContext(Dispatchers.IO) {
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            require(output.size() <= 8 * 1024 * 1024) { "ไฟล์ต้องมีขนาดไม่เกิน 8 MB" }
        }
        output.toByteArray()
    }
        ?: error("ไม่สามารถอ่านไฟล์ได้ กรุณาเลือกไฟล์ใหม่")
    require(bytes.size <= 8 * 1024 * 1024) { "ไฟล์ต้องมีขนาดไม่เกิน 8 MB" }
    if (allowPdf && bytes.take(5).toByteArray().contentEquals("%PDF-".toByteArray())) {
        return@withContext "data:application/pdf;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "ไม่สามารถอ่านรูปภาพได้ กรุณาเลือกไฟล์ใหม่" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2800) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("ไม่สามารถอ่านรูปภาพได้ กรุณาเลือกไฟล์ใหม่")
    try {
        val orientation = runCatching {
            android.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
                .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)
        }.getOrDefault(1)
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
        }
        val upright=android.graphics.Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
        val scale = minOf(1f, 1400f / maxOf(upright.width, upright.height))
        val scaled = Bitmap.createScaledBitmap(upright, maxOf(1, (upright.width * scale).toInt()), maxOf(1, (upright.height * scale).toInt()), true)
        try {
            val output = ByteArrayOutputStream()
            check(scaled.compress(Bitmap.CompressFormat.JPEG, 78, output)) { "ไม่สามารถอ่านรูปภาพได้" }
            "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } finally { if (scaled !== upright) scaled.recycle(); if (upright !== bitmap) upright.recycle() }
    } finally { bitmap.recycle() }
}
