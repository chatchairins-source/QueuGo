package com.queuego.ridermaplibreosm

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class QueueGoPmTilesProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?
) {
    val fraction: Double?
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { (downloadedBytes.toDouble() / it.toDouble()).coerceIn(0.0, 1.0) }
}

internal data class QueueGoPmTilesResumeMetadata(
    val sourceUrl: String,
    val etag: String?,
    val lastModified: String?,
    val totalBytes: Long?
) {
    val ifRangeValidator: String?
        get() = etag
            ?.takeUnless { it.startsWith("W/") }
            ?: lastModified
}

internal fun writePmTilesResumeMetadata(
    file: File,
    metadata: QueueGoPmTilesResumeMetadata
) {
    file.parentFile?.mkdirs()
    val properties = Properties().apply {
        setProperty("source_url", metadata.sourceUrl)
        metadata.etag?.let { setProperty("etag", it) }
        metadata.lastModified?.let { setProperty("last_modified", it) }
        metadata.totalBytes?.let { setProperty("total_bytes", it.toString()) }
    }
    FileOutputStream(file, false).use { output ->
        properties.store(output, "QueueGo PMTiles resume metadata")
        output.fd.sync()
    }
}

internal fun readPmTilesResumeMetadata(file: File): QueueGoPmTilesResumeMetadata? =
    runCatching {
        if (!file.isFile) return@runCatching null
        val properties = Properties()
        file.inputStream().use(properties::load)
        QueueGoPmTilesResumeMetadata(
            sourceUrl = properties.getProperty("source_url") ?: return@runCatching null,
            etag = properties.getProperty("etag"),
            lastModified = properties.getProperty("last_modified"),
            totalBytes = properties.getProperty("total_bytes")?.toLongOrNull()
        )
    }.getOrNull()

class QueueGoPmTilesDownloader(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun download(
        sourceUrl: String,
        destination: File,
        expectedSha256: String? = null,
        onProgress: (QueueGoPmTilesProgress) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        require(sourceUrl.startsWith("https://")) {
            "PMTiles archive download must use HTTPS"
        }
        val normalizedSha256 = normalizeSha256(expectedSha256)
        destination.parentFile?.mkdirs()

        if (destination.isFile) {
            val existingValid = runCatching {
                validatePmTilesArchive(destination, normalizedSha256)
            }.isSuccess
            if (existingValid) {
                onProgress(QueueGoPmTilesProgress(destination.length(), destination.length()))
                return@withContext destination
            }
            destination.delete()
        }

        val partial = File(destination.parentFile, destination.name + ".part")
        val metadataFile = File(destination.parentFile, destination.name + ".part.meta")
        downloadInternal(
            sourceUrl = sourceUrl,
            destination = destination,
            partial = partial,
            metadataFile = metadataFile,
            expectedSha256 = normalizedSha256,
            allowRestart = true,
            onProgress = onProgress
        )
    }

    private suspend fun downloadInternal(
        sourceUrl: String,
        destination: File,
        partial: File,
        metadataFile: File,
        expectedSha256: String?,
        allowRestart: Boolean,
        onProgress: (QueueGoPmTilesProgress) -> Unit
    ): File {
        var resumeMetadata = readPmTilesResumeMetadata(metadataFile)
        val partialBytes = partial.takeIf(File::isFile)?.length() ?: 0L

        if (
            partialBytes <= 0L ||
            resumeMetadata == null ||
            resumeMetadata.sourceUrl != sourceUrl
        ) {
            if (partial.exists()) partial.delete()
            if (metadataFile.exists()) metadataFile.delete()
            resumeMetadata = null
        }

        val existingBytes = if (resumeMetadata != null) partial.length() else 0L
        val request = Request.Builder()
            .url(sourceUrl)
            .apply {
                if (existingBytes > 0L) {
                    header("Range", "bytes=$existingBytes-")
                    resumeMetadata?.ifRangeValidator?.let { header("If-Range", it) }
                }
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 416 && allowRestart) {
                clearPartial(partial, metadataFile)
                return downloadInternal(
                    sourceUrl = sourceUrl,
                    destination = destination,
                    partial = partial,
                    metadataFile = metadataFile,
                    expectedSha256 = expectedSha256,
                    allowRestart = false,
                    onProgress = onProgress
                )
            }
            check(response.isSuccessful) {
                "PMTiles download failed with HTTP " + response.code
            }

            val append = existingBytes > 0L && response.code == 206
            if (append) {
                val rangeStart = response.header("Content-Range")
                    ?.substringAfter("bytes ")
                    ?.substringBefore('-')
                    ?.toLongOrNull()
                if (
                    rangeStart != existingBytes ||
                    !resumeResponseMatches(resumeMetadata, response)
                ) {
                    check(allowRestart) { "PMTiles resume validator changed during retry" }
                    clearPartial(partial, metadataFile)
                    return downloadInternal(
                        sourceUrl = sourceUrl,
                        destination = destination,
                        partial = partial,
                        metadataFile = metadataFile,
                        expectedSha256 = expectedSha256,
                        allowRestart = false,
                        onProgress = onProgress
                    )
                }
            }

            val baseBytes = if (append) existingBytes else 0L
            if (!append && existingBytes > 0L) {
                clearPartial(partial, metadataFile)
            }

            val body = checkNotNull(response.body) { "PMTiles response has no body" }
            val contentRange = response.header("Content-Range")
            val totalBytes = parseTotalBytes(
                contentRange = contentRange,
                contentLength = body.contentLength(),
                baseBytes = baseBytes
            )
            val nextMetadata = QueueGoPmTilesResumeMetadata(
                sourceUrl = sourceUrl,
                etag = response.header("ETag") ?: resumeMetadata?.etag,
                lastModified = response.header("Last-Modified") ?: resumeMetadata?.lastModified,
                totalBytes = totalBytes ?: resumeMetadata?.totalBytes
            )
            writePmTilesResumeMetadata(metadataFile, nextMetadata)

            FileOutputStream(partial, append).use { output ->
                val input = body.byteStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloaded = baseBytes
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    downloaded += count
                    onProgress(QueueGoPmTilesProgress(downloaded, totalBytes))
                }
                output.fd.sync()
            }

            totalBytes?.let { expected ->
                check(partial.length() == expected) {
                    "PMTiles download incomplete: " + partial.length() + " of " + expected + " bytes"
                }
            }
        }

        try {
            validatePmTilesArchive(partial, expectedSha256)
        } catch (error: Throwable) {
            clearPartial(partial, metadataFile)
            throw error
        }

        moveAtomically(partial, destination)
        metadataFile.delete()
        onProgress(QueueGoPmTilesProgress(destination.length(), destination.length()))
        return destination
    }

    private fun resumeResponseMatches(
        metadata: QueueGoPmTilesResumeMetadata?,
        response: Response
    ): Boolean {
        metadata ?: return false
        val responseEtag = response.header("ETag")
        if (metadata.etag != null && responseEtag != null && metadata.etag != responseEtag) {
            return false
        }
        val responseLastModified = response.header("Last-Modified")
        if (
            metadata.lastModified != null &&
            responseLastModified != null &&
            metadata.lastModified != responseLastModified
        ) {
            return false
        }
        val responseTotal = response.header("Content-Range")
            ?.substringAfterLast('/')
            ?.takeUnless { it == "*" }
            ?.toLongOrNull()
        if (
            metadata.totalBytes != null &&
            responseTotal != null &&
            metadata.totalBytes != responseTotal
        ) {
            return false
        }
        return true
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

    private fun validatePmTilesArchive(file: File, expectedSha256: String?) {
        check(file.length() >= PMTILES_HEADER_BYTES) {
            "PMTiles archive is too small"
        }
        val header = ByteArray(PMTILES_HEADER_BYTES)
        FileInputStream(file).use { input ->
            check(input.read(header) == PMTILES_HEADER_BYTES) {
                "PMTiles archive header is incomplete"
            }
        }
        val magic = header.copyOfRange(0, PMTILES_MAGIC.size)
        check(magic.contentEquals(PMTILES_MAGIC)) {
            "PMTiles archive has an invalid magic header"
        }
        val version = header[PMTILES_MAGIC.size].toInt() and 0xff
        check(version == PMTILES_VERSION) {
            "Unsupported PMTiles archive version " + version
        }

        expectedSha256?.let { expected ->
            val actual = sha256(file)
            check(actual == expected) {
                "PMTiles SHA-256 mismatch"
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun normalizeSha256(value: String?): String? {
        val normalized = value?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: return null
        require(normalized.length == 64 && normalized.all { it in '0'..'9' || it in 'a'..'f' }) {
            "expectedSha256 must be a 64-character hexadecimal SHA-256"
        }
        return normalized
    }

    private fun clearPartial(partial: File, metadataFile: File) {
        partial.delete()
        metadataFile.delete()
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

    companion object {
        private val PMTILES_MAGIC = "PMTiles".toByteArray(Charsets.US_ASCII)
        private const val PMTILES_VERSION = 3
        private const val PMTILES_HEADER_BYTES = 8
    }
}
