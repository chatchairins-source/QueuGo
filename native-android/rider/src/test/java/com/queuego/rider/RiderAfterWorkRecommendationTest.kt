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
            demandZones = listOf(RiderDemandZone(14.99, 103.10, 3, 24)),
            originLatitude = 15.0,
            originLongitude = 103.0
        )

        assertNull(result)
    }

    @Test fun offlineRiderGetsNoWorkRecommendation() {
        assertNull(
            riderAfterWorkRecommendation(
                online = false,
                stopAfterWorkPending = false,
                missions = listOf(mission()),
                demandZones = emptyList(),
                originLatitude = 15.0,
                originLongitude = 103.0
            )
        )
    }

    @Test fun activeMissionWinsOverNearbyDemand() {
        val result = riderAfterWorkRecommendation(
            online = true,
            stopAfterWorkPending = false,
            missions = listOf(mission()),
            demandZones = listOf(RiderDemandZone(15.005, 103.005, 3, 24)),
            originLatitude = 15.0,
            originLongitude = 103.0
        )

        requireNotNull(result)
        assertEquals(RiderAfterWorkRecommendationTarget.EARNINGS, result.target)
        assertTrue(result.detail.contains("2/5 เที่ยว"))
        assertEquals("ดูภารกิจ", result.actionLabel)
        assertNull(result.latitude)
        assertNull(result.longitude)
    }

    @Test fun highestDemandLevelWinsBeforeDistanceWithinRepositionRadius() {
        val result = riderAfterWorkRecommendation(
            online = true,
            stopAfterWorkPending = false,
            missions = emptyList(),
            demandZones = listOf(
                RiderDemandZone(15.002, 103.002, 2, 24),
                RiderDemandZone(15.020, 103.020, 3, 12)
            ),
            originLatitude = 15.0,
            originLongitude = 103.0
        )

        requireNotNull(result)
        assertEquals(RiderAfterWorkRecommendationTarget.DEMAND_NAVIGATION, result.target)
        assertEquals("พื้นที่คึกคักมาก", result.title)
        assertEquals(15.020, result.latitude!!, 0.000001)
        assertEquals(103.020, result.longitude!!, 0.000001)
        assertEquals("นำทางไปพื้นที่งาน", result.actionLabel)
        assertTrue(result.detail.contains("ข้อมูลพื้นที่งาน 12 ชม."))
    }

    @Test fun nearestZoneWinsWhenDemandLevelMatches() {
        val result = riderAfterWorkRecommendation(
            online = true,
            stopAfterWorkPending = false,
            missions = emptyList(),
            demandZones = listOf(
                RiderDemandZone(15.050, 103.050, 3, 24),
                RiderDemandZone(15.005, 103.005, 3, 24)
            ),
            originLatitude = 15.0,
            originLongitude = 103.0
        )

        requireNotNull(result)
        assertEquals(RiderAfterWorkRecommendationTarget.DEMAND_NAVIGATION, result.target)
        assertEquals(15.005, result.latitude!!, 0.000001)
        assertEquals(103.005, result.longitude!!, 0.000001)
        assertTrue(result.detail.contains("ห่างประมาณ"))
    }

    @Test fun farDemandFallsBackToExistingHomeHeatmapInsteadOfLongReposition() {
        val result = riderAfterWorkRecommendation(
            online = true,
            stopAfterWorkPending = false,
            missions = emptyList(),
            demandZones = listOf(RiderDemandZone(15.25, 103.25, 3, 24)),
            originLatitude = 15.0,
            originLongitude = 103.0
        )

        requireNotNull(result)
        assertEquals(RiderAfterWorkRecommendationTarget.HOME, result.target)
        assertNull(result.latitude)
        assertNull(result.longitude)
        assertEquals("ดูบนแผนที่", result.actionLabel)
    }

    @Test fun customRepositionRadiusIsFailClosed() {
        val result = riderAfterWorkRecommendation(
            online = true,
            stopAfterWorkPending = false,
            missions = emptyList(),
            demandZones = listOf(RiderDemandZone(15.020, 103.020, 3, 24)),
            originLatitude = 15.0,
            originLongitude = 103.0,
            maxRepositionKm = 1.0
        )

        requireNotNull(result)
        assertEquals(RiderAfterWorkRecommendationTarget.HOME, result.target)
        assertNull(result.latitude)
        assertNull(result.longitude)
    }

    @Test fun missingDeliveryCoordinatesKeepsSafeHomeHeatmapFallback() {
        val result = riderAfterWorkRecommendation(
            online = true,
            stopAfterWorkPending = false,
            missions = emptyList(),
            demandZones = listOf(RiderDemandZone(15.01, 103.01, 3, 24)),
            originLatitude = null,
            originLongitude = null
        )

        requireNotNull(result)
        assertEquals(RiderAfterWorkRecommendationTarget.HOME, result.target)
    }

    @Test fun emptyRealDataFallsBackToNormalSuccessDismissal() {
        assertNull(
            riderAfterWorkRecommendation(
                online = true,
                stopAfterWorkPending = false,
                missions = emptyList(),
                demandZones = emptyList(),
                originLatitude = 15.0,
                originLongitude = 103.0
            )
        )
    }

    @Test fun distanceLabelStaysCompact() {
        assertTrue(riderAfterWorkDistanceLabel(0.42).contains("ม."))
        assertTrue(riderAfterWorkDistanceLabel(2.34).contains("2.3"))
    }
}
