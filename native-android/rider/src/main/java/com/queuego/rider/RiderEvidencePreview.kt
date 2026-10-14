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

private const val RIDER_EVIDENCE_MIN_SHORT_EDGE_PX = 480
private const val RIDER_EVIDENCE_MIN_LONG_EDGE_PX = 640
private const val RIDER_EVIDENCE_MAX_BYTES = 5L * 1024L * 1024L

internal data class RiderEvidenceQuality(
    val width: Int,
    val height: Int,
    val byteSize: Long?,
    val acceptable: Boolean,
    val message: String?
)

internal fun riderEvidenceQuality(
    width: Int,
    height: Int,
    byteSize: Long?
): RiderEvidenceQuality {
    val shortEdge = minOf(width, height)
    val longEdge = maxOf(width, height)
    val message = when {
        width <= 0 || height <= 0 ->
            "เปิดรูปไม่สำเร็จ กรุณาถ่ายใหม่"
        byteSize != null && byteSize > RIDER_EVIDENCE_MAX_BYTES ->
            "ไฟล์รูปใหญ่เกิน 5 MB กรุณาถ่ายใหม่"
        shortEdge < RIDER_EVIDENCE_MIN_SHORT_EDGE_PX || longEdge < RIDER_EVIDENCE_MIN_LONG_EDGE_PX ->
            "รูปมีความละเอียดต่ำ กรุณาถ่ายใหม่ให้ภาพชัดขึ้น"
        else -> null
    }
    return RiderEvidenceQuality(
        width = width,
        height = height,
        byteSize = byteSize,
        acceptable = message == null,
        message = message
    )
}

private data class RiderEvidenceDecoded(
    val bitmap: Bitmap,
    val quality: RiderEvidenceQuality
)

/** Decode the actual camera file off the UI thread, with bounded memory and EXIF orientation. */
@Composable
internal fun RiderEvidencePreview(
    uri: Uri,
    modifier: Modifier,
    onQuality: (RiderEvidenceQuality) -> Unit
) {
    val context = LocalContext.current
    var decoded by remember(uri) { mutableStateOf<RiderEvidenceDecoded?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        try {
            decoded = withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unreadable evidence" }

                val byteSize = runCatching {
                    resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                        descriptor.statSize.takeIf { it >= 0L }
                    }
                }.getOrNull()

                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1280) sample *= 2
                val bitmap = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: error("Unreadable evidence")

                val orientation = resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
                val matrix = Matrix().apply {
                    when (orientation) {
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                        ExifInterface.ORIENTATION_TRANSPOSE -> {
                            setRotate(90f)
                            postScale(-1f, 1f)
                        }
                        ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                        ExifInterface.ORIENTATION_TRANSVERSE -> {
                            setRotate(270f)
                            postScale(-1f, 1f)
                        }
                        ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                    }
                }
                val oriented = if (matrix.isIdentity) {
                    bitmap
                } else {
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        .also { if (it !== bitmap) bitmap.recycle() }
                }

                RiderEvidenceDecoded(
                    bitmap = oriented,
                    quality = riderEvidenceQuality(bounds.outWidth, bounds.outHeight, byteSize)
                )
            }
            failed = false
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            failed = true
            decoded = null
        }
    }

    val qualityCallback by rememberUpdatedState(onQuality)
    LaunchedEffect(decoded, failed) {
        val result = decoded?.quality ?: if (failed) {
            riderEvidenceQuality(0, 0, null)
        } else {
            return@LaunchedEffect
        }
        qualityCallback(result)
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        decoded?.bitmap?.let {
            Image(
                it.asImageBitmap(),
                "รูปหลักฐานที่ถ่าย",
                Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } ?: if (failed) {
            Text("เปิดรูปไม่สำเร็จ กรุณาถ่ายใหม่")
        } else {
            CircularProgressIndicator()
        }
    }
}
