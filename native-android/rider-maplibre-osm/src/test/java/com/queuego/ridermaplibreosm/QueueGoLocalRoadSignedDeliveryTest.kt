package com.queuego.ridermaplibreosm

import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class QueueGoLocalRoadSignedDeliveryTest {
    private val payload = """{"revision":9}""".toByteArray()

    private fun road(id: String = "road-9") = QueueGoLocalRoad(
        id = id,
        points = listOf(
            QueueGoLocalRoadPoint(15.0000, 103.0000),
            QueueGoLocalRoadPoint(15.0005, 103.0007)
        ),
        source = QueueGoLocalRoadSource.FIELD_SURVEY,
        verified = true
    )

    private fun feed(revision: Long) = QueueGoLocalRoadFeed(
        revision = revision,
        generatedAtEpochMillis = 1_700_000_100_000L,
        records = listOf(
            QueueGoLocalRoadFeedRecord(
                road = road(),
                sourceReference = "field-survey:road-9",
                verifiedAtEpochMillis = 1_700_000_000_000L
            )
        )
    )

    @Test
    fun ecdsaVerifierAcceptsExactPayloadAndRejectsMutation() {
        val keys = KeyPairGenerator.getInstance("EC").apply {
            initialize(256)
        }.generateKeyPair()
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keys.private)
        signer.update(payload)
        val signature = signer.sign()

        val verifier = QueueGoLocalRoadEcdsaVerifier(mapOf("road-key-1" to keys.public))
        assertEquals(true, verifier.verify("road-key-1", payload, signature))
        assertFalse(verifier.verify("unknown-key", payload, signature))
        assertFalse(verifier.verify("road-key-1", payload + 0x01, signature))
    }

    @Test
    fun validNewerSignedSnapshotAppliesAndCachesExactVerifiedPayload() = runBlocking {
        val directory = Files.createTempDirectory("qg-local-roads").toFile()
        try {
            val cache = QueueGoLocalRoadSnapshotCache(directory)
            val envelope = QueueGoLocalRoadSignedEnvelope(
                keyId = "test-key",
                payload = payload,
                payloadSha256Hex = queueGoSha256Hex(payload),
                signature = byteArrayOf(7, 8, 9)
            )
            val delivery = QueueGoLocalRoadSignedDelivery(
                transport = object : QueueGoLocalRoadSnapshotTransport {
                    override suspend fun fetch() = envelope
                },
                decoder = object : QueueGoLocalRoadFeedDecoder {
                    override fun decode(payload: ByteArray) = feed(9)
                },
                verifier = QueueGoLocalRoadSignatureVerifier { keyId, verifiedPayload, signature ->
                    keyId == "test-key" &&
                        verifiedPayload.contentEquals(payload) &&
                        signature.contentEquals(byteArrayOf(7, 8, 9))
                },
                cache = cache
            )

            val result = delivery.refresh()

            assertEquals(QueueGoLocalRoadDeliveryStatus.APPLIED, result.status)
            assertEquals(9L, result.revision)
            assertEquals(listOf("road-9"), result.roads.map { it.id })

            val cached = cache.read()
            assertNotNull(cached)
            assertEquals(1, cached!!.envelopeVersion)
            assertEquals("test-key", cached.keyId)
            assertEquals(9L, cached.revision)
            assertEquals(queueGoSha256Hex(payload), cached.payloadSha256Hex)
            assertArrayEquals(payload, cached.payload)
            assertArrayEquals(byteArrayOf(7, 8, 9), cached.signature)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun digestMismatchFailsBeforeDecodeOrCacheWrite() = runBlocking {
        val directory = Files.createTempDirectory("qg-local-roads").toFile()
        try {
            val cache = QueueGoLocalRoadSnapshotCache(directory)
            val delivery = QueueGoLocalRoadSignedDelivery(
                transport = object : QueueGoLocalRoadSnapshotTransport {
                    override suspend fun fetch() = QueueGoLocalRoadSignedEnvelope(
                        keyId = "test-key",
                        payload = payload,
                        payloadSha256Hex = "0".repeat(64),
                        signature = byteArrayOf(1)
                    )
                },
                decoder = object : QueueGoLocalRoadFeedDecoder {
                    override fun decode(payload: ByteArray): QueueGoLocalRoadFeed {
                        throw AssertionError("Decoder must not run for digest failure")
                    }
                },
                verifier = QueueGoLocalRoadSignatureVerifier { _, _, _ ->
                    throw AssertionError("Signature verifier must not run for digest failure")
                },
                cache = cache
            )

            delivery.refresh()
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun corruptedCacheFailsClosedBeforeTransportCanApplyOlderRevision() = runBlocking {
        val directory = Files.createTempDirectory("qg-local-roads").toFile()
        try {
            val cache = QueueGoLocalRoadSnapshotCache(directory)
            val cachedPayload = "cached-revision-10".toByteArray()
            cache.write(
                10,
                QueueGoLocalRoadSignedEnvelope(
                    keyId = "cache-key",
                    payload = cachedPayload,
                    payloadSha256Hex = queueGoSha256Hex(cachedPayload),
                    signature = byteArrayOf(4, 5, 6)
                )
            )

            val snapshotFile = directory.resolve("local-roads.qglr")
            val bytes = snapshotFile.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
            snapshotFile.writeBytes(bytes)

            val delivery = QueueGoLocalRoadSignedDelivery(
                transport = object : QueueGoLocalRoadSnapshotTransport {
                    override suspend fun fetch(): QueueGoLocalRoadSignedEnvelope {
                        throw AssertionError(
                            "Transport must not run when the persisted revision watermark is corrupt"
                        )
                    }
                },
                decoder = object : QueueGoLocalRoadFeedDecoder {
                    override fun decode(payload: ByteArray): QueueGoLocalRoadFeed {
                        throw AssertionError("Decoder must not run for corrupt cache")
                    }
                },
                verifier = QueueGoLocalRoadSignatureVerifier { _, _, _ ->
                    throw AssertionError("Verifier must not run for corrupt cache")
                },
                cache = cache
            )

            delivery.refresh()
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cachedSnapshotIsReverifiedBeforeOfflineUse() {
        val directory = Files.createTempDirectory("qg-local-roads").toFile()
        try {
            val cache = QueueGoLocalRoadSnapshotCache(directory)
            val envelope = QueueGoLocalRoadSignedEnvelope(
                keyId = "cache-key",
                payload = payload,
                payloadSha256Hex = queueGoSha256Hex(payload),
                signature = byteArrayOf(9, 8, 7)
            )
            cache.write(9, envelope)

            var verifyCount = 0
            val delivery = QueueGoLocalRoadSignedDelivery(
                transport = object : QueueGoLocalRoadSnapshotTransport {
                    override suspend fun fetch(): QueueGoLocalRoadSignedEnvelope? = null
                },
                decoder = object : QueueGoLocalRoadFeedDecoder {
                    override fun decode(payload: ByteArray) = feed(9)
                },
                verifier = QueueGoLocalRoadSignatureVerifier { keyId, verifiedPayload, signature ->
                    verifyCount += 1
                    keyId == "cache-key" &&
                        verifiedPayload.contentEquals(payload) &&
                        signature.contentEquals(byteArrayOf(9, 8, 7))
                },
                cache = cache
            )

            val result = delivery.loadCached()

            assertEquals(QueueGoLocalRoadDeliveryStatus.CACHED, result.status)
            assertEquals(9L, result.revision)
            assertEquals(listOf("road-9"), result.roads.map { it.id })
            assertEquals(1, verifyCount)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun cachedSnapshotWithUntrustedSignatureFailsClosed() {
        val directory = Files.createTempDirectory("qg-local-roads").toFile()
        try {
            val cache = QueueGoLocalRoadSnapshotCache(directory)
            cache.write(
                9,
                QueueGoLocalRoadSignedEnvelope(
                    keyId = "cache-key",
                    payload = payload,
                    payloadSha256Hex = queueGoSha256Hex(payload),
                    signature = byteArrayOf(1, 2, 3)
                )
            )
            val delivery = QueueGoLocalRoadSignedDelivery(
                transport = object : QueueGoLocalRoadSnapshotTransport {
                    override suspend fun fetch(): QueueGoLocalRoadSignedEnvelope? = null
                },
                decoder = object : QueueGoLocalRoadFeedDecoder {
                    override fun decode(payload: ByteArray) = feed(9)
                },
                verifier = QueueGoLocalRoadSignatureVerifier { _, _, _ -> false },
                cache = cache
            )

            delivery.loadCached()
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun staleSignedRevisionNeverReplacesNewerCachedSnapshot() = runBlocking {
        val directory = Files.createTempDirectory("qg-local-roads").toFile()
        try {
            val cache = QueueGoLocalRoadSnapshotCache(directory)
            val cachedPayload = "cached-revision-10".toByteArray()
            cache.write(
                10,
                QueueGoLocalRoadSignedEnvelope(
                    keyId = "cache-key",
                    payload = cachedPayload,
                    payloadSha256Hex = queueGoSha256Hex(cachedPayload),
                    signature = byteArrayOf(4, 5, 6)
                )
            )

            val envelope = QueueGoLocalRoadSignedEnvelope(
                keyId = "test-key",
                payload = payload,
                payloadSha256Hex = queueGoSha256Hex(payload),
                signature = byteArrayOf(1)
            )
            val delivery = QueueGoLocalRoadSignedDelivery(
                transport = object : QueueGoLocalRoadSnapshotTransport {
                    override suspend fun fetch() = envelope
                },
                decoder = object : QueueGoLocalRoadFeedDecoder {
                    override fun decode(payload: ByteArray) =
                        if (payload.contentEquals(cachedPayload)) feed(10) else feed(9)
                },
                verifier = QueueGoLocalRoadSignatureVerifier { _, _, _ -> true },
                cache = cache
            )

            val result = delivery.refresh()

            assertEquals(QueueGoLocalRoadDeliveryStatus.REJECTED_STALE, result.status)
            assertEquals(10L, result.revision)
            assertEquals(10L, cache.read()!!.revision)
            assertArrayEquals(cachedPayload, cache.read()!!.payload)
        } finally {
            directory.deleteRecursively()
        }
    }
}
