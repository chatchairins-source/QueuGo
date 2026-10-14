package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationHybridMatcherTest {
    private val parallelReturnRoute = NavigationRouteSegment(
        id = "route",
        toStopId = "stop",
        points = listOf(
            NavigationMapPoint(13.0000, 100.0000),
            NavigationMapPoint(13.0000, 100.0100),
            NavigationMapPoint(13.0001, 100.0100),
            NavigationMapPoint(13.0001, 100.0000)
        )
    )

    @Test
    fun headingDisambiguatesCloseOppositeDirectionSegments() {
        val matcher = NavigationHybridMatcher()
        val result = matcher.match(
            parallelReturnRoute,
            NavigationMapFix(
                point = NavigationMapPoint(13.00005, 100.0050),
                bearingDegrees = 90f,
                accuracyMeters = 8f,
                speedMetersPerSecond = 8f,
                elapsedRealtimeNanos = 1_000_000_000L
            )
        )

        assertNotNull(result)
        assertTrue(result!!.routeMatch.segmentBearingDegrees!! in 70f..110f)
        assertEquals(NavigationMatchConfidence.HIGH, result.confidence)
    }

    @Test
    fun continuityAvoidsJumpingBackwardAtAmbiguousGeometry() {
        val matcher = NavigationHybridMatcher()
        val first = matcher.match(
            parallelReturnRoute,
            NavigationMapFix(
                point = NavigationMapPoint(13.00010, 100.0060),
                bearingDegrees = 270f,
                accuracyMeters = 8f,
                speedMetersPerSecond = 8f,
                elapsedRealtimeNanos = 1_000_000_000L
            )
        )
        val second = matcher.match(
            parallelReturnRoute,
            NavigationMapFix(
                point = NavigationMapPoint(13.00005, 100.0059),
                bearingDegrees = null,
                accuracyMeters = 8f,
                speedMetersPerSecond = 1f,
                elapsedRealtimeNanos = 2_000_000_000L
            )
        )

        assertNotNull(first)
        assertNotNull(second)
        assertTrue(second!!.routeMatch.progressMeters >= first!!.routeMatch.progressMeters - 35.0)
    }
}
