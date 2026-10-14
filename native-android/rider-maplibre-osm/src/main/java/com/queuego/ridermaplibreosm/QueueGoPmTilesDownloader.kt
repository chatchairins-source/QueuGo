package com.queuego.ridermaplibreosm

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class QueueGoPmTilesProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?
) {
    val fraction: Double?
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { (downloadedBytes.toDouble() / it.toDouble()).coerceIn(0.0, 1.0) }
}

private const val PROGRESS_MIN_BYTES = 512L * 1024L
private const val PROGRESS_MIN_INTERVAL_NANOS = 500_000_000L

internal fun shouldReportPmTilesProgress(
    downloadedBytes: Long,
    lastReportedBytes: Long,
    nowNanos: Long,
    lastReportedNanos: Long,
    complete: Boolean
): Boolean =
    complete ||
        downloadedBytes - lastReportedBytes >= PROGRESS_MIN_BYTES ||
        nowNanos - lastReportedNanos >= PROGRESS_MIN_INTERVAL_NANOS

class QueueGoPmTilesDownloader(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun download(
        sourceUrl: String,
        destination: File,
        onProgress: (QueueGoPmTilesProgress) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        require(sourceUrl.startsWith("https://")) {
            "PMTiles archive download must use HTTPS"
        }
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, destination.name + ".part")
        downloadInternal(
            sourceUrl = sourceUrl,
            destination = destination,
            partial = partial,
            allowRestart = true,
            onProgress = onProgress
        )
    }

    private suspend fun downloadInternal(
        sourceUrl: String,
        destination: File,
        partial: File,
        allowRestart: Boolean,
        onProgress: (QueueGoPmTilesProgress) -> Unit
    ): File {
        val existingBytes = partial.takeIf(File::exists)?.length() ?: 0L
        val request = Request.Builder()
            .url(sourceUrl)
            .apply {
                if (existingBytes > 0L) header("Range", "bytes=$existingBytes-")
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 416 && allowRestart) {
                partial.delete()
                return downloadInternal(
                    sourceUrl,
                    destination,
                    partial,
                    allowRestart = false,
                    onProgress = onProgress
                )
            }
            check(response.isSuccessful) {
                "PMTiles download failed with HTTP ${response.code}"
            }

            val append = existingBytes > 0L && response.code == 206
            val baseBytes = if (append) existingBytes else 0L
            val contentRange = response.header("Content-Range")
            if (append) {
                val rangeStart = contentRange
                    ?.substringAfter("bytes ")
                    ?.substringBefore('-')
                    ?.toLongOrNull()
                check(rangeStart == existingBytes) {
                    "PMTiles resume response started at unexpected byte"
                }
            }

            val body = checkNotNull(response.body) { "PMTiles response has no body" }
            val totalBytes = parseTotalBytes(
                contentRange = contentRange,
                contentLength = body.contentLength(),
                baseBytes = baseBytes
            )

            if (!append && partial.exists()) partial.delete()
            FileOutputStream(partial, append).use { output ->
                val input = body.byteStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloaded = baseBytes
                var lastReportedBytes = baseBytes
                var lastReportedNanos = System.nanoTime()
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    downloaded += count
                    val nowNanos = System.nanoTime()
                    if (
                        shouldReportPmTilesProgress(
                            downloadedBytes = downloaded,
                            lastReportedBytes = lastReportedBytes,
                            nowNanos = nowNanos,
                            lastReportedNanos = lastReportedNanos,
                            complete = totalBytes != null && downloaded >= totalBytes
                        )
                    ) {
                        onProgress(QueueGoPmTilesProgress(downloaded, totalBytes))
                        lastReportedBytes = downloaded
                        lastReportedNanos = nowNanos
                    }
                }
                output.fd.sync()
                if (downloaded != lastReportedBytes) {
                    onProgress(QueueGoPmTilesProgress(downloaded, totalBytes))
                }
            }

            totalBytes?.let { expected ->
                check(partial.length() == expected) {
                    "PMTiles download incomplete: ${partial.length()} of $expected bytes"
                }
            }
        }

        moveAtomically(partial, destination)
        return destination
    }

    private fun parseTotalBytes(
        contentRange: String?,
        contentLength: Long,
        baseBytes: Long
    ): Long? {
        val rangeTotal = contentRange
            ?.substringAfterLast('/')
            ?.takeUnless { it == "*" }
            ?.toLongOrNull()
        if (rangeTotal != null) return rangeTotal
        return contentLength.takeIf { it >= 0L }?.let { baseBytes + it }
    }

    private fun moveAtomically(source: File, destination: File) {
        runCatching {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        }.recoverCatching {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }.getOrThrow()
    }
}
