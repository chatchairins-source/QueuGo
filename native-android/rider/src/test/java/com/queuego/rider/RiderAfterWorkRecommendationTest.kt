package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderAfterWorkRecommendationTest {
    private fun mission(
        state: String = "active",
        completed: Boolean = false,
        progress: Int = 2
    ) = RiderMission(
        id = "mission-1",
        title = "ครบ 5 เที่ยว",
        description = null,
        targetJobs = 5,
        rewardNote = "โบนัสจริงจาก QueueGo",
        startsAt = null,
        endsAt = null,
        state = state,
        progressJobs = progress,
        completed = completed
    )

    @Test fun stopAfterWorkNeverSuggestsMoreWork() {
        val result = riderAfterWorkRecommendation(
            online = true,
            stopAfterWorkPending = true,
            missions = listOf(mission()),
            demandZones = listOf(RiderDemandZone(14.99, 103.10, 3, 24))
        )

        assertNull(result)
    }

    @Test fun offlineRiderGetsNoWorkRecommendation() {
        assertNull(
            riderAfterWorkRecommendation(
                online = false,
                stopAfterWorkPending = false,
                missions = listOf(mission()),
                demandZones = emptyList()
            )
        )
    }

    @Test fun activeMissionWinsOverGenericDemand() {
        val result = riderAfterWorkRecommendation(
            online = true,
            stopAfterWorkPending = false,
            missions = listOf(mission()),
            demandZones = listOf(RiderDemandZone(14.99, 103.10, 3, 24))
        )

        requireNotNull(result)
        assertEquals(RiderAfterWorkRecommendationTarget.EARNINGS, result.target)
        assertTrue(result.detail.contains("2/5 เที่ยว"))
        assertEquals("ดูภารกิจ", result.actionLabel)
    }

    @Test fun realDemandIsSuggestedOnlyWhenNoActiveMissionExists() {
        val result = riderAfterWorkRecommendation(
            online = true,
            stopAfterWorkPending = false,
            missions = listOf(mission(state = "completed", completed = true, progress = 5)),
            demandZones = listOf(
                RiderDemandZone(14.99, 103.10, 2, 24),
                RiderDemandZone(15.01, 103.11, 3, 12)
            )
        )

        requireNotNull(result)
        assertEquals(RiderAfterWorkRecommendationTarget.HOME, result.target)
        assertTrue(result.detail.contains("2 จุด"))
        assertTrue(result.detail.contains("ระดับสูงสุด 3/3"))
        assertTrue(result.detail.contains("ช่วง 12 ชม."))
    }

    @Test fun emptyRealDataFallsBackToNormalSuccessDismissal() {
        assertNull(
            riderAfterWorkRecommendation(
                online = true,
                stopAfterWorkPending = false,
                missions = emptyList(),
                demandZones = emptyList()
            )
        )
    }
}
