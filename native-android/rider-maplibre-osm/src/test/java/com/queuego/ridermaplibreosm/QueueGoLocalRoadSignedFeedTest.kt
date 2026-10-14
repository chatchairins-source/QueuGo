package com.queuego.ridermaplibreosm

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueGoLocalRoadSignedFeedTest {
    private fun keyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()

    private fun signature(payload: ByteArray, keyPair: KeyPair): String {
        val bytes = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(payload)
            sign()
        }
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun validFeed(revision: Long = 7L): QueueGoLocalRoadFeed =
        QueueGoLocalRoadFeed(
            revision = revision,
            generatedAtEpochMillis = 1_800_000_000_000L,
            records = emptyList()
        )

    @Test
    fun verifierAcceptsMatchingDigestSignatureAndKeyId() {
        val payload = """{"revision":7}""".toByteArray()
        val keyPair = keyPair()
        val verifier = QueueGoLocalRoadEcdsaVerifier(
            expectedKeyId = "local-roads-2026-a",
            publicKeyDer = keyPair.public.encoded
        )

        verifier.verify(
            QueueGoLocalRoadSignedPayload(
                bytes = payload,
                headers = QueueGoLocalRoadSignedHeaders(
                    keyId = "local-roads-2026-a",
                    sha256Hex = payload.sha256Hex(),
                    signatureBase64 = signature(payload, keyPair)
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun tamperedPayloadFailsClosed() {
        val signed = """{"revision":7}""".toByteArray()
        val tampered = """{"revision":8}""".toByteArray()
        val keyPair = keyPair()

        QueueGoLocalRoadEcdsaVerifier(
            expectedKeyId = "local-roads-2026-a",
            publicKeyDer = keyPair.public.encoded
        ).verify(
            QueueGoLocalRoadSignedPayload(
                bytes = tampered,
                headers = QueueGoLocalRoadSignedHeaders(
                    keyId = "local-roads-2026-a",
                    sha256Hex = signed.sha256Hex(),
                    signatureBase64 = signature(signed, keyPair)
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongKeyIdFailsClosed() {
        val payload = """{"revision":7}""".toByteArray()
        val keyPair = keyPair()

        QueueGoLocalRoadEcdsaVerifier(
            expectedKeyId = "local-roads-2026-a",
            publicKeyDer = keyPair.public.encoded
        ).verify(
            QueueGoLocalRoadSignedPayload(
                bytes = payload,
                headers = QueueGoLocalRoadSignedHeaders(
                    keyId = "local-roads-old-key",
                    sha256Hex = payload.sha256Hex(),
                    signatureBase64 = signature(payload, keyPair)
                )
            )
        )
    }

    @Test
    fun httpsClientVerifiesBeforeDecoderAndReturnsRevisionDecision() = runBlocking {
        val payload = """{"schema_version":1,"revision":8}""".toByteArray()
        val signingKey = keyPair()
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

        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(QUEUEGO_LOCAL_ROAD_HEADER_KEY_ID, "local-roads-2026-a")
                    .setHeader(QUEUEGO_LOCAL_ROAD_HEADER_SHA256, payload.sha256Hex())
                    .setHeader(
                        QUEUEGO_LOCAL_ROAD_HEADER_SIGNATURE,
                        signature(payload, signingKey)
                    )
                    .setBody(Buffer().write(payload))
            )
            val decoderCalls = AtomicInteger(0)
            val client = OkHttpClient.Builder()
                .sslSocketFactory(
                    clientCertificates.sslSocketFactory(),
                    clientCertificates.trustManager
                )
                .build()
            val feedClient = QueueGoLocalRoadSignedFeedClient(
                client = client,
                endpoint = server.url("/local-roads/v1/snapshot"),
                verifier = QueueGoLocalRoadEcdsaVerifier(
                    expectedKeyId = "local-roads-2026-a",
                    publicKeyDer = signingKey.public.encoded
                ),
                decoder = QueueGoLocalRoadFeedDecoder {
                    decoderCalls.incrementAndGet()
                    validFeed(revision = 8L)
                }
            )

            val result = feedClient.fetch(currentRevision = 7L)

            assertEquals(1, decoderCalls.get())
            assertEquals(QueueGoLocalRoadFeedDecision.APPLY, result.decision)
            assertEquals(8L, result.revision)
            assertEquals(
                "application/vnd.queuego.local-roads+json",
                server.takeRequest().getHeader("Accept")
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun badSignatureIsRejectedBeforeDecoderRuns() = runBlocking {
        val payload = """{"revision":8}""".toByteArray()
        val signingKey = keyPair()
        val wrongKey = keyPair()
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

        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(QUEUEGO_LOCAL_ROAD_HEADER_KEY_ID, "local-roads-2026-a")
                    .setHeader(QUEUEGO_LOCAL_ROAD_HEADER_SHA256, payload.sha256Hex())
                    .setHeader(
                        QUEUEGO_LOCAL_ROAD_HEADER_SIGNATURE,
                        signature(payload, wrongKey)
                    )
                    .setBody(Buffer().write(payload))
            )
            val decoderCalls = AtomicInteger(0)
            val client = OkHttpClient.Builder()
                .sslSocketFactory(
                    clientCertificates.sslSocketFactory(),
                    clientCertificates.trustManager
                )
                .build()
            val feedClient = QueueGoLocalRoadSignedFeedClient(
                client = client,
                endpoint = server.url("/local-roads/v1/snapshot"),
                verifier = QueueGoLocalRoadEcdsaVerifier(
                    expectedKeyId = "local-roads-2026-a",
                    publicKeyDer = signingKey.public.encoded
                ),
                decoder = QueueGoLocalRoadFeedDecoder {
                    decoderCalls.incrementAndGet()
                    validFeed(8L)
                }
            )

            val failure = runCatching { feedClient.fetch(currentRevision = 7L) }
            assertTrue(failure.isFailure)
            assertEquals(0, decoderCalls.get())
        } finally {
            server.shutdown()
        }
    }
}
