package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationMotionPolicyTest {
    @Test fun markerAnimationNeverOutrunsGpsCadence() {
        val previous = NavigationMapFix(
            point = NavigationMapPoint(13.0, 100.0),
            speedMetersPerSecond = 8f,
            elapsedRealtimeNanos = 1_000_000_000L
        )
        val next = previous.copy(
            point = NavigationMapPoint(13.0001, 100.0001),
            elapsedRealtimeNanos = 2_000_000_000L
        )
        val seconds = riderMarkerAnimationSeconds(previous, next)
        assertTrue(seconds < 1.0)
        assertTrue(seconds <= 0.85)
    }

    @Test fun halfSecondGpsCadenceUsesShorterAnimationInsteadOfOverlap() {
        val previous = NavigationMapFix(
            point = NavigationMapPoint(13.0, 100.0),
            speedMetersPerSecond = 8f,
            elapsedRealtimeNanos = 1_000_000_000L
        )
        val next = previous.copy(
            point = NavigationMapPoint(13.00005, 100.00005),
            elapsedRealtimeNanos = 1_500_000_000L
        )
        assertTrue(riderMarkerAnimationSeconds(previous, next) < 0.5)
    }

    @Test fun standstillGpsJitterDoesNotMoveMarker() {
        val previous = NavigationMapFix(
            point = NavigationMapPoint(13.0, 100.0),
            speedMetersPerSecond = 0.2f
        )
        val tinyJitter = previous.copy(
            point = NavigationMapPoint(13.000005, 100.000005)
        )
        assertFalse(shouldMoveRiderMarker(previous, tinyJitter))
    }

    @Test fun realDrivingMovementStillUpdatesMarker() {
        val previous = NavigationMapFix(
            point = NavigationMapPoint(13.0, 100.0),
            speedMetersPerSecond = 8f
        )
        val moved = previous.copy(
            point = NavigationMapPoint(13.00003, 100.00003)
        )
        assertTrue(shouldMoveRiderMarker(previous, moved))
        assertEquals(0.6, riderMarkerMoveThresholdMeters(8f), 0.001)
    }
}
