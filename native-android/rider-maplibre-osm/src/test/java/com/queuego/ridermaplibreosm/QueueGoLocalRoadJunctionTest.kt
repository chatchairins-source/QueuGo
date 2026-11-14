package com.queuego.ridermaplibreosm

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class QueueGoLocalRoadJunctionTest {
    private val start = QueueGoLocalRoadPoint(15.0000, 103.0000)
    private val junctionA = QueueGoLocalRoadPoint(15.0000, 103.0010)
    private val junctionB = QueueGoLocalRoadPoint(15.00002, 103.00102)
    private val end = QueueGoLocalRoadPoint(15.0000, 103.0020)

    private fun road(id: String, points: List<QueueGoLocalRoadPoint>) =
        QueueGoLocalRoad(
            id = id,
            points = points,
            source = QueueGoLocalRoadSource.FIELD_SURVEY,
            verified = true
        )

    private fun records(): List<QueueGoLocalRoadFeedRecord> = listOf(
        QueueGoLocalRoadFeedRecord(
            road = road("road-a", listOf(start, junctionA)),
            sourceReference = "field-survey:road-a",
            verifiedAtEpochMillis = 1_700_000_000_000L
        ),
        QueueGoLocalRoadFeedRecord(
            road = road("road-b", listOf(junctionB, end)),
            sourceReference = "field-survey:road-b",
            verifiedAtEpochMillis = 1_700_000_000_000L
        )
    )

    private fun junction(
        id: String = "junction:ab",
        secondPointIndex: Int = 0
    ) = QueueGoVerifiedLocalRoadJunction(
        id = id,
        members = listOf(
            QueueGoLocalRoadJunctionMember("road-a", 1),
            QueueGoLocalRoadJunctionMember("road-b", secondPointIndex)
        ),
        source = QueueGoLocalRoadSource.FIELD_SURVEY,
        sourceReference = "field-survey:$id",
        verifiedAtEpochMillis = 1_700_000_000_000L
    )

    private fun feed(
        schemaVersion: Int = 2,
        junctions: List<QueueGoVerifiedLocalRoadJunction> = listOf(junction())
    ) = QueueGoLocalRoadFeed(
        schemaVersion = schemaVersion,
        revision = 20,
        generatedAtEpochMillis = 1_700_000_100_000L,
        records = records(),
        junctions = junctions
    )

    @Test(expected = IllegalArgumentException::class)
    fun schemaV1CannotCarryJunctionEvidence() {
        validateQueueGoLocalRoadFeed(feed(schemaVersion = 1))
    }

    @Test
    fun verifiedNearbyJunctionConnectsCoordinatesThatNeverAutoSnap() {
        val validated = validateQueueGoLocalRoadFeed(feed())
        val withoutJunction = buildQueueGoLocalRoadGraph(validated.roads)
        val withJunction = buildQueueGoLocalRoadGraph(
            roads = validated.roads,
            junctions = validated.junctions
        )

        val rawA = queueGoExactNodeId(junctionA)
        val rawB = queueGoExactNodeId(junctionB)
        assertNotEquals(rawA, rawB)
        assertNotEquals(
            withoutJunction.exactPointNodeIds[rawA],
            withoutJunction.exactPointNodeIds[rawB]
        )
        assertEquals(
            withJunction.exactPointNodeIds[rawA],
            withJunction.exactPointNodeIds[rawB]
        )

        val blocked = findQueueGoLocalRoadPath(
            withoutJunction,
            QueueGoLocalRoadPathRequest(
                startNodeId = requireNotNull(
                    withoutJunction.exactPointNodeIds[queueGoExactNodeId(start)]
                ),
                endNodeId = requireNotNull(
                    withoutJunction.exactPointNodeIds[queueGoExactNodeId(end)]
                ),
                vehicle = QueueGoRoutingVehicle.MOTORCYCLE
            )
        )
        val allowed = findQueueGoLocalRoadPath(
            withJunction,
            QueueGoLocalRoadPathRequest(
                startNodeId = requireNotNull(
                    withJunction.exactPointNodeIds[queueGoExactNodeId(start)]
                ),
                endNodeId = requireNotNull(
                    withJunction.exactPointNodeIds[queueGoExactNodeId(end)]
                ),
                vehicle = QueueGoRoutingVehicle.MOTORCYCLE
            )
        )

        assertEquals(QueueGoLocalRoadPathStatus.NO_PATH, blocked.status)
        assertEquals(QueueGoLocalRoadPathStatus.FOUND, allowed.status)
        assertEquals(listOf("road-a", "road-b"), allowed.edges.map { it.roadId })
    }

    @Test(expected = IllegalArgumentException::class)
    fun junctionMembersTooFarApartFailClosed() {
        val farRoadB = QueueGoLocalRoadFeedRecord(
            road = road(
                "road-b",
                listOf(
                    QueueGoLocalRoadPoint(15.0010, 103.0020),
                    end
                )
            ),
            sourceReference = "field-survey:road-b",
            verifiedAtEpochMillis = 1_700_000_000_000L
        )
        validateQueueGoLocalRoadFeed(
            QueueGoLocalRoadFeed(
                schemaVersion = 2,
                revision = 21,
                generatedAtEpochMillis = 1_700_000_100_000L,
                records = listOf(records().first(), farRoadB),
                junctions = listOf(junction())
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun oneRoadPointCannotBelongToTwoJunctions() {
        validateQueueGoLocalRoadFeed(
            feed(
                junctions = listOf(
                    junction("junction:first"),
                    junction("junction:second")
                )
            )
        )
    }

    @Test
    fun signedSnapshotCarriesJunctionIntoTrustedPathfinder() = runBlocking {
        val directory = Files.createTempDirectory("qg-junction").toFile()
        try {
            val payload = "signed-junction-feed".toByteArray()
            val envelope = QueueGoLocalRoadSignedEnvelope(
                keyId = "junction-key",
                payload = payload,
                payloadSha256Hex = queueGoSha256Hex(payload),
                signature = byteArrayOf(2, 4, 6)
            )
            val delivery = QueueGoLocalRoadSignedDelivery(
                transport = object : QueueGoLocalRoadSnapshotTransport {
                    override suspend fun fetch() = envelope
                },
                decoder = object : QueueGoLocalRoadFeedDecoder {
                    override fun decode(payload: ByteArray) = feed()
                },
                verifier = QueueGoLocalRoadSignatureVerifier { keyId, verifiedPayload, signature ->
                    keyId == "junction-key" &&
                        verifiedPayload.contentEquals(payload) &&
                        signature.contentEquals(byteArrayOf(2, 4, 6))
                },
                cache = QueueGoLocalRoadSnapshotCache(directory)
            )

            val snapshot = requireNotNull(delivery.refreshTrustedSnapshot())
            assertEquals(1, snapshot.junctionCount)
            val session = snapshot.buildGraphSession()

            val junctionNodeA = requireNotNull(session.exactNodeId(junctionA))
            val junctionNodeB = requireNotNull(session.exactNodeId(junctionB))
            assertEquals(junctionNodeA, junctionNodeB)

            val result = session.findPath(
                QueueGoLocalRoadPathRequest(
                    startNodeId = requireNotNull(session.exactNodeId(start)),
                    endNodeId = requireNotNull(session.exactNodeId(end)),
                    vehicle = QueueGoRoutingVehicle.MOTORCYCLE
                )
            )
            assertEquals(QueueGoLocalRoadPathStatus.FOUND, result.status)
        } finally {
            directory.deleteRecursively()
        }
    }
}
