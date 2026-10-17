package com.queuego.ridermaplibreosm

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun nearestNodeSnapsOnlyInsideExplicitRadius() {
        val graph = QueueGoTrustedLocalRoadSnapshot(
            revision = 1L,
            roads = listOf(road()),
            junctions = emptyList()
        ).buildGraphSession()

        val near = graph.nearestNode(
            point = QueueGoLocalRoadPoint(15.00001, 103.00001),
            maxSnapMeters = 10.0
        )
        assertNotNull(near)
        assertTrue(near!!.distanceMeters < 2.0)
        assertEquals(graph.exactNodeId(a), near.nodeId)

        val far = graph.nearestNode(
            point = QueueGoLocalRoadPoint(15.01, 103.01),
            maxSnapMeters = 40.0
        )
        assertNull(far)
    }

    @Test
    fun routeBetweenUsesTrustedGraphGeometryAndNeverBridgesMissingCoverage() {
        val graph = QueueGoTrustedLocalRoadSnapshot(
            revision = 1L,
            roads = listOf(road()),
            junctions = emptyList()
        ).buildGraphSession()

        val route = graph.routeBetween(
            start = QueueGoLocalRoadPoint(15.00001, 103.00001),
            end = QueueGoLocalRoadPoint(15.00001, 103.00099),
            vehicle = QueueGoRoutingVehicle.MOTORCYCLE,
            maxSnapMeters = 10.0
        )
        assertEquals(QueueGoLocalRoadPathStatus.FOUND, route.status)
        assertEquals(listOf(a, b), route.points)
        assertTrue((route.startSnapMeters ?: Double.MAX_VALUE) < 2.0)
        assertTrue((route.endSnapMeters ?: Double.MAX_VALUE) < 2.0)
        assertTrue(route.totalDistanceMeters > 100.0)

        val outside = graph.routeBetween(
            start = a,
            end = QueueGoLocalRoadPoint(15.01, 103.01),
            vehicle = QueueGoRoutingVehicle.MOTORCYCLE,
            maxSnapMeters = 40.0
        )
        assertEquals(QueueGoLocalRoadPathStatus.INVALID_ENDPOINT, outside.status)
        assertTrue(outside.points.isEmpty())
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
