package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderAfterJobRecommendationTest {
    @Test fun highestDemandLevelWinsBeforeDistance() {
        val result = riderAfterJobRecommendation(
            zones = listOf(
                RiderDemandZone(15.001, 103.001, level = 2, windowHours = 24),
                RiderDemandZone(15.020, 103.020, level = 3, windowHours = 24)
            ),
            originLatitude = 15.0,
            originLongitude = 103.0
        )

        assertEquals(3, result?.zone?.level)
    }

    @Test fun nearestZoneWinsWithinSameDemandLevel() {
        val result = riderAfterJobRecommendation(
            zones = listOf(
                RiderDemandZone(15.050, 103.050, level = 3, windowHours = 24),
                RiderDemandZone(15.005, 103.005, level = 3, windowHours = 24)
            ),
            originLatitude = 15.0,
            originLongitude = 103.0
        )

        assertEquals(15.005, result!!.zone.latitude, 0.000001)
        assertTrue(result.distanceKm < 2.0)
    }

    @Test fun zonesOutsidePilotRepositionRadiusAreNotSuggested() {
        val result = riderAfterJobRecommendation(
            zones = listOf(RiderDemandZone(15.2, 103.2, level = 3, windowHours = 24)),
            originLatitude = 15.0,
            originLongitude = 103.0
        )

        assertNull(result)
    }

    @Test fun missingOrInvalidDeliveryPointDoesNotInventRecommendation() {
        val zones = listOf(RiderDemandZone(15.01, 103.01, level = 3, windowHours = 24))

        assertNull(riderAfterJobRecommendation(zones, null, 103.0))
        assertNull(riderAfterJobRecommendation(zones, 15.0, Double.NaN))
    }

    @Test fun distanceLabelStaysCompact() {
        assertTrue(riderAfterJobDistanceLabel(0.42).contains("ม."))
        assertTrue(riderAfterJobDistanceLabel(2.34).contains("2.3"))
    }
}
