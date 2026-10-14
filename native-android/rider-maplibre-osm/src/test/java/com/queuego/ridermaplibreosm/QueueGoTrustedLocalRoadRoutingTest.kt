package com.queuego.ridermaplibreosm

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QueueGoTrustedLocalRoadRoutingTest {
    private val a = QueueGoLocalRoadPoint(15.0000, 103.0000)
    private val b = QueueGoLocalRoadPoint(15.0000, 103.0010)
    private val payload = "trusted-local-road-feed".toByteArray()

    private fun road() = QueueGoLocalRoad(
        id = "field-survey:trusted-road",
        points = listOf(a, b),
        source = QueueGoLocalRoadSource.FIELD_SURVEY,
        verified = true
    )

    private fun feed() = QueueGoLocalRoadFeed(
        revision = 12,
        generatedAtEpochMillis = 1_700_000_100_000L,
        records = listOf(
            QueueGoLocalRoadFeedRecord(
                road = road(),
                sourceReference = "field-survey:trusted-road",
                verifiedAtEpochMillis = 1_700_000_000_000L
            )
        )
    )

    @Test
    fun trustedDeliveryProducesOnlyPublicGraphSessionPath() = runBlocking {
        val directory = Files.createTempDirectory("qg-trusted-local-roads").toFile()
        try {
            val envelope = QueueGoLocalRoadSignedEnvelope(
                keyId = "trusted-key",
                payload = payload,
                payloadSha256Hex = queueGoSha256Hex(payload),
                signature = byteArrayOf(4, 5, 6)
            )
            val delivery = QueueGoLocalRoadSignedDelivery(
                transport = object : QueueGoLocalRoadSnapshotTransport {
                    override suspend fun fetch() = envelope
                },
                decoder = object : QueueGoLocalRoadFeedDecoder {
                    override fun decode(payload: ByteArray) = feed()
                },
                verifier = QueueGoLocalRoadSignatureVerifier { keyId, verifiedPayload, signature ->
                    keyId == "trusted-key" &&
                        verifiedPayload.contentEquals(payload) &&
                        signature.contentEquals(byteArrayOf(4, 5, 6))
                },
                cache = QueueGoLocalRoadSnapshotCache(directory)
            )

            val snapshot = delivery.refreshTrustedSnapshot()
            assertNotNull(snapshot)
            assertEquals(12L, snapshot!!.revision)
            assertEquals(1, snapshot.roadCount)

            val session = snapshot.buildGraphSession()
            val start = session.exactNodeId(a)
            val end = session.exactNodeId(b)
            assertNotNull(start)
            assertNotNull(end)
            assertNull(
                session.exactNodeId(
                    QueueGoLocalRoadPoint(15.0000001, 103.0000001)
                )
            )

            val result = session.findPath(
                QueueGoLocalRoadPathRequest(
                    startNodeId = requireNotNull(start),
                    endNodeId = requireNotNull(end),
                    vehicle = QueueGoRoutingVehicle.MOTORCYCLE
                )
            )
            assertEquals(QueueGoLocalRoadPathStatus.FOUND, result.status)
            assertEquals(listOf("field-survey:trusted-road"), result.edges.map { it.roadId })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun noVerifiedSnapshotProducesNoTrustedToken() {
        val directory = Files.createTempDirectory("qg-trusted-local-roads").toFile()
        try {
            val delivery = QueueGoLocalRoadSignedDelivery(
                transport = object : QueueGoLocalRoadSnapshotTransport {
                    override suspend fun fetch(): QueueGoLocalRoadSignedEnvelope? = null
                },
                decoder = object : QueueGoLocalRoadFeedDecoder {
                    override fun decode(payload: ByteArray): QueueGoLocalRoadFeed {
                        throw AssertionError("Decoder must not run")
                    }
                },
                verifier = QueueGoLocalRoadSignatureVerifier { _, _, _ ->
                    throw AssertionError("Verifier must not run")
                },
                cache = QueueGoLocalRoadSnapshotCache(directory)
            )

            assertNull(delivery.loadTrustedSnapshot())
        } finally {
            directory.deleteRecursively()
        }
    }
}
