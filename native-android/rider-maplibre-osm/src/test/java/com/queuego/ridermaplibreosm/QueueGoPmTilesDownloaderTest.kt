package com.queuego.ridermaplibreosm

import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QueueGoPmTilesDownloaderTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var directory: File

    @Before fun setUp() {
        val held = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(held)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(held.certificate)
            .build()
        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()
        client = OkHttpClient.Builder()
            .sslSocketFactory(
                clientCertificates.sslSocketFactory(),
                clientCertificates.trustManager
            )
            .hostnameVerifier { _, _ -> true }
            .build()
        directory = createTempDirectory("queuego-pmtiles-").toFile()
    }

    @After fun tearDown() {
        server.shutdown()
        directory.deleteRecursively()
    }

    @Test fun downloadsFreshArchiveValidatesVersionAndRenamesPartFile() = runBlocking {
        val payload = pmTilesPayload("fresh-map")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"buriram-v1\"")
                .setHeader("Content-Length", payload.size)
                .setBody(okio.Buffer().write(payload))
        )
        val destination = File(directory, "buriram.pmtiles")
        val progress = mutableListOf<QueueGoPmTilesProgress>()

        QueueGoPmTilesDownloader(client).download(
            sourceUrl = server.url("/buriram.pmtiles").toString(),
            destination = destination,
            expectedSha256 = sha256(payload),
            onProgress = progress::add
        )

        assertTrue(destination.exists())
        assertFalse(File(directory, "buriram.pmtiles.part").exists())
        assertFalse(File(directory, "buriram.pmtiles.part.meta").exists())
        assertArrayEquals(payload, destination.readBytes())
        assertEquals(payload.size.toLong(), progress.last().downloadedBytes)
    }

    @Test fun resumesOnlyWithMatchingArchiveValidator() = runBlocking {
        val payload = pmTilesPayload("resume-map-content")
        val prefix = payload.copyOfRange(0, 10)
        val suffix = payload.copyOfRange(10, payload.size)
        val sourceUrl = server.url("/buriram.pmtiles").toString()
        val destination = File(directory, "buriram.pmtiles")
        val partial = File(directory, "buriram.pmtiles.part")
        val metadata = File(directory, "buriram.pmtiles.part.meta")
        partial.writeBytes(prefix)
        writePmTilesResumeMetadata(
            metadata,
            QueueGoPmTilesResumeMetadata(
                sourceUrl = sourceUrl,
                etag = "\"v1\"",
                lastModified = null,
                totalBytes = payload.size.toLong()
            )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("ETag", "\"v1\"")
                .setHeader(
                    "Content-Range",
                    "bytes " + prefix.size + "-" + (payload.size - 1) + "/" + payload.size
                )
                .setHeader("Content-Length", suffix.size)
                .setBody(okio.Buffer().write(suffix))
        )

        QueueGoPmTilesDownloader(client).download(
            sourceUrl = sourceUrl,
            destination = destination,
            expectedSha256 = sha256(payload)
        )

        val request = server.takeRequest()
        assertEquals("bytes=" + prefix.size + "-", request.getHeader("Range"))
        assertEquals("\"v1\"", request.getHeader("If-Range"))
        assertArrayEquals(payload, destination.readBytes())
    }

    @Test fun validatorChangeRestartsInsteadOfAppendingWrongArchive() = runBlocking {
        val oldPayload = pmTilesPayload("old-map")
        val newPayload = pmTilesPayload("new-map-complete")
        val sourceUrl = server.url("/buriram.pmtiles").toString()
        val destination = File(directory, "buriram.pmtiles")
        val partial = File(directory, "buriram.pmtiles.part")
        val metadata = File(directory, "buriram.pmtiles.part.meta")
        partial.writeBytes(oldPayload.copyOfRange(0, 9))
        writePmTilesResumeMetadata(
            metadata,
            QueueGoPmTilesResumeMetadata(
                sourceUrl = sourceUrl,
                etag = "\"old\"",
                lastModified = null,
                totalBytes = oldPayload.size.toLong()
            )
        )

        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("ETag", "\"new\"")
                .setHeader(
                    "Content-Range",
                    "bytes 9-" + (newPayload.size - 1) + "/" + newPayload.size
                )
                .setBody(okio.Buffer().write(newPayload.copyOfRange(9, newPayload.size)))
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"new\"")
                .setHeader("Content-Length", newPayload.size)
                .setBody(okio.Buffer().write(newPayload))
        )

        QueueGoPmTilesDownloader(client).download(
            sourceUrl = sourceUrl,
            destination = destination,
            expectedSha256 = sha256(newPayload)
        )

        assertArrayEquals(newPayload, destination.readBytes())
        assertEquals("bytes=9-", server.takeRequest().getHeader("Range"))
        assertNull(server.takeRequest().getHeader("Range"))
    }

    @Test fun invalidMagicIsNeverPublished() = runBlocking {
        val invalid = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 3, 9, 9)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", invalid.size)
                .setBody(okio.Buffer().write(invalid))
        )
        val destination = File(directory, "buriram.pmtiles")

        val failed = runCatching {
            QueueGoPmTilesDownloader(client).download(
                sourceUrl = server.url("/invalid.pmtiles").toString(),
                destination = destination
            )
        }

        assertTrue(failed.isFailure)
        assertFalse(destination.exists())
        assertFalse(File(directory, "buriram.pmtiles.part").exists())
    }

    @Test fun wrongChecksumIsNeverPublished() = runBlocking {
        val payload = pmTilesPayload("checksum-map")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", payload.size)
                .setBody(okio.Buffer().write(payload))
        )
        val destination = File(directory, "buriram.pmtiles")

        val failed = runCatching {
            QueueGoPmTilesDownloader(client).download(
                sourceUrl = server.url("/buriram.pmtiles").toString(),
                destination = destination,
                expectedSha256 = "0".repeat(64)
            )
        }

        assertTrue(failed.isFailure)
        assertFalse(destination.exists())
        assertFalse(File(directory, "buriram.pmtiles.part").exists())
    }

    private fun pmTilesPayload(body: String): ByteArray =
        "PMTiles".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(3) +
            body.toByteArray()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
