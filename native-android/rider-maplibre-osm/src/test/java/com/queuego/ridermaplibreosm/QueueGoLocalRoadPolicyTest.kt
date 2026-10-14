package com.queuego.ridermaplibreosm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueGoLocalRoadPolicyTest {
    private val points = listOf(
        QueueGoLocalRoadPoint(14.9800, 103.1000),
        QueueGoLocalRoadPoint(14.9810, 103.1010)
    )

    @Test
    fun candidateTraceIsVisibleButNeverRouteEligible() {
        val segment = QueueGoLocalRoadSegment(
            id = "trace:aggregate:1",
            points = points,
            roadClass = QueueGoLocalRoadClass.ALLEY,
            source = QueueGoLocalRoadSource.RIDER_TRACE_AGGREGATE,
            verification = QueueGoLocalRoadVerification.CANDIDATE,
            access = QueueGoLocalRoadAccess.MOTORCYCLE,
            sourceReference = "trace-batch:sha256:abc"
        )

        assertTrue(segment.renderable)
        assertFalse(segment.eligibleForMotorcycleRouting)
        assertTrue(QueueGoLocalRoadPolicy.visibleSegments(listOf(segment)).contains(segment))
        assertTrue(QueueGoLocalRoadPolicy.motorcycleRoutingSegments(listOf(segment)).isEmpty())
    }

    @Test
    fun verifiedOpenRoadCanEnterMotorcycleRoutingPool() {
        val segment = QueueGoLocalRoadSegment(
            id = "survey:road:1",
            points = points,
            roadClass = QueueGoLocalRoadClass.SERVICE,
            source = QueueGoLocalRoadSource.FIELD_SURVEY,
            verification = QueueGoLocalRoadVerification.VERIFIED,
            access = QueueGoLocalRoadAccess.DELIVERY,
            sourceReference = "survey:2026-10-14:segment-1",
            verifiedAtEpochMs = 1_797_000_000_000L
        )

        assertTrue(segment.eligibleForMotorcycleRouting)
        assertTrue(
            QueueGoLocalRoadPolicy.motorcycleRoutingSegments(listOf(segment)).contains(segment)
        )
    }

    @Test
    fun verifiedPrivateRoadStaysDisplayOnly() {
        val segment = QueueGoLocalRoadSegment(
            id = "merchant:private:1",
            points = points,
            roadClass = QueueGoLocalRoadClass.ALLEY,
            source = QueueGoLocalRoadSource.MERCHANT_VERIFIED,
            verification = QueueGoLocalRoadVerification.VERIFIED,
            access = QueueGoLocalRoadAccess.DELIVERY,
            privateAccess = true,
            sourceReference = "merchant-entrance:verified",
            verifiedAtEpochMs = 1_797_000_000_000L
        )

        assertTrue(segment.renderable)
        assertFalse(segment.eligibleForMotorcycleRouting)
    }

    @Test(expected = IllegalArgumentException::class)
    fun verifiedRoadRequiresVerificationTimestamp() {
        QueueGoLocalRoadSegment(
            id = "invalid:verified",
            points = points,
            roadClass = QueueGoLocalRoadClass.RESIDENTIAL,
            source = QueueGoLocalRoadSource.OPENSTREETMAP,
            verification = QueueGoLocalRoadVerification.VERIFIED,
            access = QueueGoLocalRoadAccess.MOTOR_VEHICLE,
            sourceReference = "osm:way:123"
        ).validateForImport()
    }
}
