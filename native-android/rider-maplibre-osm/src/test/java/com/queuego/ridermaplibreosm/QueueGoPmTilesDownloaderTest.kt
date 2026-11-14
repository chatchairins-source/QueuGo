package com.queuego.ridermaplibreosm

import java.io.File
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

    @Test fun downloadsFreshArchiveAndRenamesPartFile() = runBlocking {
        val payload = "pmtiles-v3-data".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", payload.size)
                .setBody(okio.Buffer().write(payload))
        )
        val destination = File(directory, "buriram.pmtiles")
        val progress = mutableListOf<QueueGoPmTilesProgress>()

        QueueGoPmTilesDownloader(client).download(
            sourceUrl = server.url("/buriram.pmtiles").toString(),
            destination = destination,
            onProgress = progress::add
        )

        assertTrue(destination.exists())
        assertFalse(File(directory, "buriram.pmtiles.part").exists())
        assertArrayEquals(payload, destination.readBytes())
        assertEquals(payload.size.toLong(), progress.last().downloadedBytes)
    }

    @Test fun resumesExistingPartWithRangeRequest() = runBlocking {
        val prefix = "partial-".toByteArray()
        val suffix = "remaining".toByteArray()
        val total = prefix.size + suffix.size
        File(directory, "buriram.pmtiles.part").writeBytes(prefix)
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes ${prefix.size}-${total - 1}/$total")
                .setHeader("Content-Length", suffix.size)
                .setBody(okio.Buffer().write(suffix))
        )

        val destination = File(directory, "buriram.pmtiles")
        QueueGoPmTilesDownloader(client).download(
            sourceUrl = server.url("/buriram.pmtiles").toString(),
            destination = destination
        )

        val request = server.takeRequest()
        assertEquals("bytes=${prefix.size}-", request.getHeader("Range"))
        assertArrayEquals(prefix + suffix, destination.readBytes())
    }
}
