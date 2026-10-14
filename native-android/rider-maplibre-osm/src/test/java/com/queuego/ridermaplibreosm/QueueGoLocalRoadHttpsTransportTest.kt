package com.queuego.ridermaplibreosm

import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueGoLocalRoadHttpsTransportTest {
    private data class TlsFixture(
        val server: MockWebServer,
        val client: OkHttpClient
    )

    private fun tlsFixture(): TlsFixture {
        val heldCertificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(heldCertificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(heldCertificate.certificate)
            .build()

        val server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()

        val client = OkHttpClient.Builder()
            .sslSocketFactory(
                clientCertificates.sslSocketFactory(),
                clientCertificates.trustManager
            )
            .build()
        return TlsFixture(server, client)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cleartextEndpointIsRejectedBeforeAnyRequest() {
        QueueGoLocalRoadHttpsTransport(
            client = OkHttpClient(),
            endpoint = "http://127.0.0.1/local-roads".toHttpUrl()
        )
    }

    @Test
    fun validHttpsResponseProducesRawSignedEnvelopeOnly() = runBlocking {
        val fixture = tlsFixture()
        val payload = """{"schemaVersion":1,"revision":9}""".toByteArray()
        val signature = byteArrayOf(1, 2, 3, 4)

        try {
            fixture.server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", QUEUEGO_LOCAL_ROAD_MEDIA_TYPE)
                    .setHeader(QUEUEGO_LOCAL_ROAD_HEADER_KEY_ID, "roads-key-1")
                    .setHeader(QUEUEGO_LOCAL_ROAD_HEADER_SHA256, queueGoSha256Hex(payload))
                    .setHeader(
                        QUEUEGO_LOCAL_ROAD_HEADER_SIGNATURE,
                        Base64.getEncoder().encodeToString(signature)
                    )
                    .setBody(Buffer().write(payload))
            )

            val envelope = QueueGoLocalRoadHttpsTransport(
                client = fixture.client,
                endpoint = fixture.server.url("/local-roads/v1/snapshot")
            ).fetch()

            assertEquals("roads-key-1", envelope.keyId)
            assertEquals(queueGoSha256Hex(payload), envelope.payloadSha256Hex)
            assertArrayEquals(payload, envelope.payload)
            assertArrayEquals(signature, envelope.signature)

            val request = fixture.server.takeRequest()
            assertEquals(QUEUEGO_LOCAL_ROAD_MEDIA_TYPE, request.getHeader("Accept"))
            assertEquals("GET", request.method)
        } finally {
            fixture.server.shutdown()
        }
    }

    @Test
    fun missingSignatureHeaderFailsClosed() = runBlocking {
        val fixture = tlsFixture()
        val payload = """{"revision":9}""".toByteArray()

        try {
            fixture.server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", QUEUEGO_LOCAL_ROAD_MEDIA_TYPE)
                    .setHeader(QUEUEGO_LOCAL_ROAD_HEADER_KEY_ID, "roads-key-1")
                    .setHeader(QUEUEGO_LOCAL_ROAD_HEADER_SHA256, queueGoSha256Hex(payload))
                    .setBody(Buffer().write(payload))
            )

            val failure = runCatching {
                QueueGoLocalRoadHttpsTransport(
                    client = fixture.client,
                    endpoint = fixture.server.url("/local-roads/v1/snapshot")
                ).fetch()
            }

            assertTrue(failure.isFailure)
        } finally {
            fixture.server.shutdown()
        }
    }

    @Test
    fun wrongContentTypeFailsClosed() = runBlocking {
        val fixture = tlsFixture()
        val payload = """{"revision":9}""".toByteArray()

        try {
            fixture.server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setHeader(QUEUEGO_LOCAL_ROAD_HEADER_KEY_ID, "roads-key-1")
                    .setHeader(QUEUEGO_LOCAL_ROAD_HEADER_SHA256, queueGoSha256Hex(payload))
                    .setHeader(
                        QUEUEGO_LOCAL_ROAD_HEADER_SIGNATURE,
                        Base64.getEncoder().encodeToString(byteArrayOf(1))
                    )
                    .setBody(Buffer().write(payload))
            )

            val failure = runCatching {
                QueueGoLocalRoadHttpsTransport(
                    client = fixture.client,
                    endpoint = fixture.server.url("/local-roads/v1/snapshot")
                ).fetch()
            }

            assertTrue(failure.isFailure)
        } finally {
            fixture.server.shutdown()
        }
    }
}
