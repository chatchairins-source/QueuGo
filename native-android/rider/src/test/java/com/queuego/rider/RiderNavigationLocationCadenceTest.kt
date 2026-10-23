package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderNavigationLocationCadenceTest {
    @Test
    fun activeNavigationUsesThreeSecondsUntilWithinTwoHundredMeters() {
        assertEquals(3_000L, riderNavigationLocationPushIntervalMs(null))
        assertEquals(3_000L, riderNavigationLocationPushIntervalMs(200.01))
        assertEquals(1_000L, riderNavigationLocationPushIntervalMs(200.0))
        assertEquals(1_000L, riderNavigationLocationPushIntervalMs(0.0))
    }

    @Test
    fun distanceHelperKeepsNearDestinationBoundaryGroundedInMeters() {
        val aboutOneHundredElevenMeters = riderNavigationDistanceMeters(
            latitude = 15.0,
            longitude = 103.0,
            targetLatitude = 15.001,
            targetLongitude = 103.0
        )
        assertTrue(aboutOneHundredElevenMeters != null)
        assertTrue(aboutOneHundredElevenMeters!! in 110.0..112.0)
        assertEquals(1_000L, riderNavigationLocationPushIntervalMs(aboutOneHundredElevenMeters))
    }

    @Test
    fun invalidTargetFallsBackToNormalThreeSecondCadence() {
        assertEquals(
            null,
            riderNavigationDistanceMeters(
                latitude = 15.0,
                longitude = 103.0,
                targetLatitude = null,
                targetLongitude = null
            )
        )
        assertEquals(3_000L, riderNavigationLocationPushIntervalMs(Double.NaN))
    }
}
