package com.queuego.ridermaplibreosm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueGoLocalRoadCoverageAuditTest {
    private fun p(lat: Double, lon: Double) = QueueGoLocalRoadPoint(lat, lon)

    private val base = QueueGoCoverageRoadGeometry(
        id = "osm:base",
        points = listOf(
            p(15.0000, 103.0000),
            p(15.0000, 103.0040)
        )
    )

    private fun evidence(
        ref: String,
        points: List<QueueGoLocalRoadPoint>,
        traversals: Int = 4,
        accuracy: Double = 8.0
    ) = QueueGoAggregateTraceEvidence(
        aggregateReference = "queuego-trace:$ref",
        points = points,
        traversalCount = traversals,
        medianAccuracyMeters = accuracy,
        generatedAtEpochMillis = 1_700_000_100_000L
    )

    @Test
    fun wellMappedRoadDoesNotBecomeCandidate() {
        val trace = evidence(
            "mapped",
            listOf(
                p(15.00002, 103.0002),
                p(15.00003, 103.0015),
                p(15.00002, 103.0030)
            )
        )

        assertTrue(auditQueueGoLocalRoadCoverage(listOf(base), listOf(trace)).isEmpty())
    }

    @Test
    fun repeatedAccurateUnmappedAlleyBecomesReviewCandidateOnly() {
        val trace = evidence(
            "alley-1",
            listOf(
                p(15.0000, 103.0010),
                p(15.00035, 103.0012),
                p(15.00070, 103.0014),
                p(15.00105, 103.0016)
            )
        )

        val candidates = auditQueueGoLocalRoadCoverage(listOf(base), listOf(trace))

        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals("queuego-trace:alley-1", candidate.sourceReference)
        assertTrue(candidate.lengthMeters >= 30.0)
        assertTrue(candidate.uncoveredFraction >= 0.65)
        assertEquals(
            QueueGoCoverageEndpointHint.NEAR_EXISTING_ROAD,
            candidate.startHint
        )
        assertEquals(
            QueueGoCoverageEndpointHint.NONE,
            candidate.endHint
        )
    }

    @Test
    fun weakOrSingleTraversalEvidenceIsRejected() {
        val geometry = listOf(
            p(15.0010, 103.0010),
            p(15.0020, 103.0020)
        )
        val single = evidence("single", geometry, traversals = 1)
        val inaccurate = evidence("weak", geometry, traversals = 5, accuracy = 45.0)

        assertTrue(
            auditQueueGoLocalRoadCoverage(
                listOf(base),
                listOf(single, inaccurate)
            ).isEmpty()
        )
    }

    @Test
    fun noBaseRoadCanFlagStrongAggregateEvidenceWithoutAutoPublishing() {
        val trace = evidence(
            "rural-gap",
            listOf(
                p(15.0100, 103.0100),
                p(15.0110, 103.0110)
            )
        )

        val candidate = auditQueueGoLocalRoadCoverage(emptyList(), listOf(trace)).single()

        assertEquals(1.0, candidate.uncoveredFraction, 0.000001)
        assertEquals(QueueGoCoverageEndpointHint.NONE, candidate.startHint)
        assertEquals(QueueGoCoverageEndpointHint.NONE, candidate.endHint)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rawOrExternalTraceReferenceIsRejected() {
        QueueGoAggregateTraceEvidence(
            aggregateReference = "rider:raw-device-trace",
            points = listOf(
                p(15.0100, 103.0100),
                p(15.0110, 103.0110)
            ),
            traversalCount = 4,
            medianAccuracyMeters = 8.0,
            generatedAtEpochMillis = 1_700_000_100_000L
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateAggregateEvidenceFailsClosed() {
        val trace = evidence(
            "same",
            listOf(
                p(15.0100, 103.0100),
                p(15.0110, 103.0110)
            )
        )
        auditQueueGoLocalRoadCoverage(emptyList(), listOf(trace, trace))
    }

    @Test
    fun pointToSegmentDistanceIsSmallForNearRoadAndLargeForGap() {
        val near = queueGoPointToSegmentDistanceMeters(
            p(15.00005, 103.0020),
            base.points[0],
            base.points[1]
        )
        val far = queueGoPointToSegmentDistanceMeters(
            p(15.0010, 103.0020),
            base.points[0],
            base.points[1]
        )

        assertTrue(near < 10.0)
        assertTrue(far > 80.0)
    }
}
