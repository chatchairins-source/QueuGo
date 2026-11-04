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

    @Test
    fun delayedFusedCallbacksCannotOverwriteNavigationWithExpiredOrFuturePosition() {
        val now = 600_000_000_000L
        assertTrue(riderNavigationFixEligible(now, now - 5_000_000_000L, 15.0, 103.0, 8f))
        assertTrue(riderNavigationFixEligible(now, now - 30_000_000_000L, 15.0, 103.0, 8f))
        assertEquals(false, riderNavigationFixEligible(now, now - 31_000_000_000L, 15.0, 103.0, 8f))
        assertEquals(false, riderNavigationFixEligible(now, now + 1_000_000_000L, 15.0, 103.0, 8f))
        assertEquals(false, riderNavigationFixEligible(now, 0L, 15.0, 103.0, 8f))
        assertEquals(false, riderNavigationFixEligible(10_000_000_000L, now, 15.0, 103.0, 8f))
    }

    @Test
    fun navigationCallbackRejectsInvalidCoordinatesAndUnreliableAccuracy() {
        val now = 600_000_000_000L
        val recent = now - 2_000_000_000L
        assertEquals(false, riderNavigationFixEligible(now, recent, Double.NaN, 103.0, 8f))
        assertEquals(false, riderNavigationFixEligible(now, recent, 15.0, 181.0, 8f))
        assertEquals(false, riderNavigationFixEligible(now, recent, 15.0, 103.0, 81f))
        assertEquals(false, riderNavigationFixEligible(now, recent, 15.0, 103.0, Float.NaN))
        assertEquals(false, riderNavigationFixEligible(now, recent, 15.0, 103.0, -1f))
        // Android devices without an accuracy estimate retain their previous behavior.
        assertTrue(riderNavigationFixEligible(now, recent, 15.0, 103.0, null))
    }

    @Test
    fun dispatchGpsPermissionAllowsFineOrCoarseButNeverNeither() {
        assertTrue(riderNavigationPermissionGranted(fineGranted = true, coarseGranted = true))
        assertTrue(riderNavigationPermissionGranted(fineGranted = true, coarseGranted = false))
        assertTrue(riderNavigationPermissionGranted(fineGranted = false, coarseGranted = true))
        assertEquals(false, riderNavigationPermissionGranted(fineGranted = false, coarseGranted = false))
    }
}
