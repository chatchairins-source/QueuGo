package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLocationFilterTest {
    @Test
    fun holdsTinyStationaryJitterAtPreviousPoint() {
        val filter = NavigationLocationFilter()
        val first = NavigationMapFix(
            point = NavigationMapPoint(13.000000, 100.000000),
            accuracyMeters = 10f,
            speedMetersPerSecond = 0.2f,
            elapsedRealtimeNanos = 1_000_000_000L
        )
        val second = NavigationMapFix(
            point = NavigationMapPoint(13.000008, 100.000000),
            accuracyMeters = 10f,
            speedMetersPerSecond = 0.2f,
            elapsedRealtimeNanos = 2_000_000_000L
        )

        filter.filter(first)
        val result = filter.filter(second)

        assertFalse(result.rejected)
        assertEquals(first.point, result.fix?.point)
        assertEquals(NavigationFixQuality.GOOD, result.quality)
    }

    @Test
    fun rejectsImplausibleTeleport() {
        val filter = NavigationLocationFilter()
        filter.filter(
            NavigationMapFix(
                point = NavigationMapPoint(13.0000, 100.0000),
                accuracyMeters = 8f,
                speedMetersPerSecond = 4f,
                elapsedRealtimeNanos = 1_000_000_000L
            )
        )

        val result = filter.filter(
            NavigationMapFix(
                point = NavigationMapPoint(13.0200, 100.0000),
                accuracyMeters = 8f,
                speedMetersPerSecond = 4f,
                elapsedRealtimeNanos = 2_000_000_000L
            )
        )

        assertTrue(result.rejected)
        assertEquals("implausible-teleport", result.reason)
    }

    @Test
    fun acceptsNormalForwardMovement() {
        val filter = NavigationLocationFilter()
        filter.filter(
            NavigationMapFix(
                point = NavigationMapPoint(13.0000, 100.0000),
                accuracyMeters = 12f,
                speedMetersPerSecond = 8f,
                elapsedRealtimeNanos = 1_000_000_000L
            )
        )

        val result = filter.filter(
            NavigationMapFix(
                point = NavigationMapPoint(13.0000, 100.0001),
                accuracyMeters = 12f,
                speedMetersPerSecond = 8f,
                elapsedRealtimeNanos = 2_000_000_000L
            )
        )

        assertFalse(result.rejected)
        assertTrue(result.fix != null)
        assertTrue(result.fix!!.point.longitude > 100.0000)
    }
}
