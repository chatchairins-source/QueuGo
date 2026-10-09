package com.queuego.rider

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decode the actual camera file off the UI thread, with bounded memory and EXIF orientation. */
@Composable
internal fun RiderEvidencePreview(uri: Uri, modifier: Modifier, onDecoded: (Boolean) -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        try {
            bitmap = withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unreadable evidence" }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1280) sample *= 2
                val decoded = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: error("Unreadable evidence")
                val orientation = resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
                val matrix = Matrix().apply {
                    when (orientation) {
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                        ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                        ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                        ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                        ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                    }
                }
                if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            failed = true
        }
    }
    val decodedCallback by rememberUpdatedState(onDecoded)
    LaunchedEffect(bitmap, failed) { decodedCallback(bitmap != null && !failed) }
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), "รูปหลักฐานที่ถ่าย", Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
            ?: if (failed) Text("เปิดรูปไม่สำเร็จ กรุณาถ่ายใหม่") else CircularProgressIndicator()
    }
}
