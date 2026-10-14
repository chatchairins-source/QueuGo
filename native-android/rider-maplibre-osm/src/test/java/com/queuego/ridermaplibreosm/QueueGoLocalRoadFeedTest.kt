package com.queuego.ridermaplibreosm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class QueueGoLocalRoadFeedTest {
    private fun road(
        id: String,
        verified: Boolean = true,
        source: QueueGoLocalRoadSource = QueueGoLocalRoadSource.FIELD_SURVEY
    ) = QueueGoLocalRoad(
        id = id,
        points = listOf(
            QueueGoLocalRoadPoint(15.0000, 103.0000),
            QueueGoLocalRoadPoint(15.0005, 103.0007)
        ),
        source = source,
        verified = verified
    )

    private fun record(
        id: String,
        verified: Boolean = true
    ) = QueueGoLocalRoadFeedRecord(
        road = road(id, verified),
        sourceReference = "field-survey:" + id,
        verifiedAtEpochMillis = if (verified) 1_700_000_000_000L else null
    )

    @Test
    fun newerRevisionIsEligibleToApply() {
        val validated = validateQueueGoLocalRoadFeed(
            QueueGoLocalRoadFeed(
                revision = 8,
                generatedAtEpochMillis = 1_700_000_100_000L,
                records = listOf(record("road-a"))
            ),
            currentRevision = 7
        )

        assertEquals(QueueGoLocalRoadFeedDecision.APPLY, validated.decision)
        assertEquals(2, validated.totalPointCount)
        assertEquals(listOf("road-a"), validated.roadsForApply()!!.map { it.id })
    }

    @Test
    fun equalRevisionIsIdempotentAndOldRevisionIsRejected() {
        val feed = QueueGoLocalRoadFeed(
            revision = 8,
            generatedAtEpochMillis = 1_700_000_100_000L,
            records = listOf(record("road-a"))
        )

        val unchanged = validateQueueGoLocalRoadFeed(feed, currentRevision = 8)
        val stale = validateQueueGoLocalRoadFeed(feed.copy(revision = 7), currentRevision = 8)

        assertEquals(QueueGoLocalRoadFeedDecision.UNCHANGED, unchanged.decision)
        assertNull(unchanged.roadsForApply())
        assertEquals(QueueGoLocalRoadFeedDecision.REJECT_STALE, stale.decision)
        assertNull(stale.roadsForApply())
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateRoadIdsFailClosed() {
        validateQueueGoLocalRoadFeed(
            QueueGoLocalRoadFeed(
                revision = 1,
                generatedAtEpochMillis = 1_700_000_100_000L,
                records = listOf(record("same"), record("same"))
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun verifiedRoadRequiresVerificationTimestamp() {
        validateQueueGoLocalRoadFeed(
            QueueGoLocalRoadFeed(
                revision = 1,
                generatedAtEpochMillis = 1_700_000_100_000L,
                records = listOf(
                    QueueGoLocalRoadFeedRecord(
                        road = road("road-a"),
                        sourceReference = "field-survey:road-a",
                        verifiedAtEpochMillis = null
                    )
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun competitorStyleReferenceCannotPassFieldSurveyProvenance() {
        validateQueueGoLocalRoadFeed(
            QueueGoLocalRoadFeed(
                revision = 3,
                generatedAtEpochMillis = 1_700_000_100_000L,
                records = listOf(
                    QueueGoLocalRoadFeedRecord(
                        road = road("road-a"),
                        sourceReference = "competitor:road-a",
                        verifiedAtEpochMillis = 1_700_000_000_000L
                    )
                )
            )
        )
    }

    @Test
    fun unverifiedCandidateCanBeCarriedButRendererStillFiltersIt() {
        val candidate = record("candidate", verified = false)
        val validated = validateQueueGoLocalRoadFeed(
            QueueGoLocalRoadFeed(
                revision = 2,
                generatedAtEpochMillis = 1_700_000_100_000L,
                records = listOf(candidate)
            )
        )

        assertEquals(QueueGoLocalRoadFeedDecision.APPLY, validated.decision)
        assertSame(candidate.road, validated.roads.single())
        assertEquals(
            0,
            queueGoVerifiedLocalRoadFeatures(validated.roads).features().orEmpty().size
        )
    }
}
