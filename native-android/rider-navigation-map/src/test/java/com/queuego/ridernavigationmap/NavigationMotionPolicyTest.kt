package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationMotionPolicyTest {
    @Test fun oneSecondGpsCadenceSlightlyOverlapsForContinuousMotion() {
        val previous = NavigationMapFix(
            point = NavigationMapPoint(13.0, 100.0),
            speedMetersPerSecond = 8f,
            elapsedRealtimeNanos = 1_000_000_000L
        )
        val next = previous.copy(
            point = NavigationMapPoint(13.0001, 100.0001),
            elapsedRealtimeNanos = 2_000_000_000L
        )
        val millis = riderMarkerAnimationMillis(previous, next)
        assertTrue(millis > 1_000L)
        assertTrue(millis <= 1_200L)
    }

    @Test fun halfSecondGpsCadenceAlsoOverlapsWithoutRunaway() {
        val previous = NavigationMapFix(
            point = NavigationMapPoint(13.0, 100.0),
            speedMetersPerSecond = 8f,
            elapsedRealtimeNanos = 1_000_000_000L
        )
        val next = previous.copy(
            point = NavigationMapPoint(13.00005, 100.00005),
            elapsedRealtimeNanos = 1_500_000_000L
        )
        val millis = riderMarkerAnimationMillis(previous, next)
        assertTrue(millis > 500L)
        assertTrue(millis <= 600L)
    }

    @Test fun markerAnimationUsesTheSame260To1250MillisecondClampAsWeb() {
        val previous = NavigationMapFix(point = NavigationMapPoint(13.0, 100.0),
            elapsedRealtimeNanos = 1_000_000_000L)
        val next = previous.copy(elapsedRealtimeNanos = 1_100_000_000L)
        val millis = riderMarkerAnimationMillis(previous, next)
        assertEquals(260L, millis)
        val slowPrevious = previous.copy(elapsedRealtimeNanos = 1_000_000_000L)
        val slowNext = slowPrevious.copy(elapsedRealtimeNanos = 3_000_000_000L)
        assertEquals(1_250L, riderMarkerAnimationMillis(slowPrevious, slowNext))
    }

    @Test fun largeCorrectionSnapsWhileOrdinaryMotionAnimates() {
        val start = NavigationMapPoint(13.0, 100.0)
        assertFalse(shouldAnimateRiderMarker(start, NavigationMapPoint(13.002, 100.0)))
        assertTrue(shouldAnimateRiderMarker(start, NavigationMapPoint(13.00005, 100.0)))
        val fix = NavigationMapFix(point = start)
        assertEquals(1.05, riderMarkerAnimationSeconds(null, fix), 0.001)
    }

    @Test fun everyChangedGpsFixMovesTheMarkerAsOnWeb() {
        val previous = NavigationMapFix(
            point = NavigationMapPoint(13.0, 100.0),
            speedMetersPerSecond = 0.2f
        )
        val tinyJitter = previous.copy(
            point = NavigationMapPoint(13.000005, 100.000005)
        )
        assertTrue(shouldMoveRiderMarker(previous, tinyJitter))
        assertFalse(shouldMoveRiderMarker(previous, previous.copy()))
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
        assertEquals(0.01, riderMarkerMoveThresholdMeters(8f), 0.001)
    }
}
