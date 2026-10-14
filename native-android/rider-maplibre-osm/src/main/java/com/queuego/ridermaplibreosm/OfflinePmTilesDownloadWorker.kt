package com.queuego.ridermaplibreosm

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

internal const val OFFLINE_INPUT_ID = "map_id"
internal const val OFFLINE_INPUT_URL = "map_url"
internal const val OFFLINE_INPUT_SHA256 = "map_sha256"
const val OFFLINE_PROGRESS_DOWNLOADED_BYTES = "downloaded_bytes"
const val OFFLINE_PROGRESS_TOTAL_BYTES = "total_bytes"
const val OFFLINE_PROGRESS_PERCENT = "percent"
const val OFFLINE_OUTPUT_SOURCE_URI = "source_uri"

private const val PROGRESS_BYTES_STEP = 512L * 1024L
private const val PROGRESS_TIME_STEP_MS = 500L

internal enum class DownloadWriteMode { APPEND, RESTART, COMPLETE, RETRY, FAIL }

internal data class DownloadWritePlan(
    val mode: DownloadWriteMode,
    val totalBytes: Long?
)

internal fun downloadWritePlan(
    existingBytes: Long,
    statusCode: Int,
    contentLength: Long?,
    contentRange: String?
): DownloadWritePlan {
    if (statusCode == 416) {
        val total = contentRange
            ?.substringAfter("*/", "")
            ?.toLongOrNull()
        return if (total != null && total == existingBytes) {
            DownloadWritePlan(DownloadWriteMode.COMPLETE, total)
        } else {
            DownloadWritePlan(DownloadWriteMode.RETRY, total)
        }
    }

    if (statusCode in 500..599 || statusCode == 408 || statusCode == 429) {
        return DownloadWritePlan(DownloadWriteMode.RETRY, null)
    }
    if (statusCode !in 200..299) {
        return DownloadWritePlan(DownloadWriteMode.FAIL, null)
    }

    if (statusCode == 206 && existingBytes > 0L) {
        val start = contentRange
            ?.substringAfter("bytes ", "")
            ?.substringBefore("-", "")
            ?.toLongOrNull()
        val total = contentRange
            ?.substringAfter("/", "")
            ?.takeUnless { it == "*" }
            ?.toLongOrNull()
        return if (start == existingBytes) {
            DownloadWritePlan(DownloadWriteMode.APPEND, total)
        } else {
            DownloadWritePlan(DownloadWriteMode.RETRY, total)
        }
    }

    val total = contentLength?.takeIf { it >= 0L }
    return DownloadWritePlan(DownloadWriteMode.RESTART, total)
}

class OfflinePmTilesDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(OFFLINE_INPUT_ID)
            ?: return@withContext Result.failure()
        val url = inputData.getString(OFFLINE_INPUT_URL)
            ?: return@withContext Result.failure()
        val sha256 = inputData.getString(OFFLINE_INPUT_SHA256)?.takeIf { it.isNotBlank() }

        val pkg = try {
            OfflineMapPackage(id = id, downloadUrl = url, sha256 = sha256)
        } catch (_: IllegalArgumentException) {
            return@withContext Result.failure()
        }

        val partial = pkg.partialFile(applicationContext)
        val final = pkg.finalFile(applicationContext)
        if (final.isFile && validateSha256(final, sha256)) {
            return@withContext success(pkg, final)
        }
        if (final.exists()) final.delete()

        val existingBytes = partial.takeIf { it.isFile }?.length() ?: 0L
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "QueueGo-Rider-Map/1.0")
            .apply {
                if (existingBytes > 0L) header("Range", "bytes=" + existingBytes + "-")
            }
            .get()
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (_: IOException) {
            return@withContext Result.retry()
        }

        response.use { res ->
            val plan = downloadWritePlan(
                existingBytes = existingBytes,
                statusCode = res.code,
                contentLength = res.body?.contentLength()?.takeIf { it >= 0L },
                contentRange = res.header("Content-Range")
            )

            when (plan.mode) {
                DownloadWriteMode.COMPLETE ->
                    return@withContext finalizePackage(pkg, partial, final, sha256)
                DownloadWriteMode.RETRY -> {
                    if (res.code == 416 || res.code == 206) partial.delete()
                    return@withContext Result.retry()
                }
                DownloadWriteMode.FAIL -> return@withContext Result.failure()
                DownloadWriteMode.APPEND,
                DownloadWriteMode.RESTART -> Unit
            }

            val body = res.body ?: return@withContext Result.retry()
            val append = plan.mode == DownloadWriteMode.APPEND
            if (!append && partial.exists()) partial.delete()

            var downloaded = if (append) existingBytes else 0L
            val total = plan.totalBytes
                ?: body.contentLength().takeIf { it >= 0L }?.let { downloaded + it }

            FileOutputStream(partial, append).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    var lastReportedBytes = downloaded
                    var lastReportedAt = SystemClock.elapsedRealtime()
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count

                        val now = SystemClock.elapsedRealtime()
                        if (
                            downloaded - lastReportedBytes >= PROGRESS_BYTES_STEP ||
                            now - lastReportedAt >= PROGRESS_TIME_STEP_MS
                        ) {
                            setProgress(progressData(downloaded, total))
                            lastReportedBytes = downloaded
                            lastReportedAt = now
                        }
                    }
                    output.fd.sync()
                }
            }
            setProgress(progressData(downloaded, total))
            finalizePackage(pkg, partial, final, sha256)
        }
    }

    private fun finalizePackage(
        pkg: OfflineMapPackage,
        partial: File,
        final: File,
        sha256: String?
    ): Result {
        if (!partial.isFile || partial.length() <= 0L) return Result.retry()
        if (!validateSha256(partial, sha256)) {
            partial.delete()
            return Result.retry()
        }
        if (final.exists() && !final.delete()) return Result.retry()
        if (!partial.renameTo(final)) return Result.retry()
        return success(pkg, final)
    }

    private fun success(pkg: OfflineMapPackage, final: File): Result =
        Result.success(
            Data.Builder()
                .putString(OFFLINE_OUTPUT_SOURCE_URI, pkg.installedSourceUri(applicationContext))
                .putLong(OFFLINE_PROGRESS_DOWNLOADED_BYTES, final.length())
                .putLong(OFFLINE_PROGRESS_TOTAL_BYTES, final.length())
                .putInt(OFFLINE_PROGRESS_PERCENT, 100)
                .build()
        )

    private fun progressData(downloaded: Long, total: Long?): Data {
        val builder = Data.Builder()
            .putLong(OFFLINE_PROGRESS_DOWNLOADED_BYTES, downloaded)
        if (total != null && total > 0L) {
            builder.putLong(OFFLINE_PROGRESS_TOTAL_BYTES, total)
            builder.putInt(
                OFFLINE_PROGRESS_PERCENT,
                ((downloaded.coerceAtMost(total) * 100L) / total).toInt()
            )
        }
        return builder.build()
    }

    private fun validateSha256(file: File, expected: String?): Boolean {
        if (expected.isNullOrBlank()) return true
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expected, ignoreCase = true)
    }

    companion object {
        private val client = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()
    }
}
