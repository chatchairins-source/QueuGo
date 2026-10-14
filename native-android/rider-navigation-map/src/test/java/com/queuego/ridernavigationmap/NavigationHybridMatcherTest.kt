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
    fun continuityPrefersLaterLoopCrossingInsteadOfJumpingBackToRouteStart() {
        val loopRoute = NavigationRouteSegment(
            id = "loop",
            toStopId = "stop",
            points = listOf(
                NavigationMapPoint(15.0000, 103.0000),
                NavigationMapPoint(15.0000, 103.0100),
                NavigationMapPoint(15.0100, 103.0100),
                NavigationMapPoint(15.0100, 103.0000),
                NavigationMapPoint(15.0000, 103.0000)
            )
        )
        val matcher = NavigationHybridMatcher()

        val beforeCrossing = matcher.match(
            loopRoute,
            NavigationMapFix(
                point = NavigationMapPoint(15.0030, 103.0000),
                bearingDegrees = 180f,
                accuracyMeters = 8f,
                speedMetersPerSecond = 7f,
                elapsedRealtimeNanos = 1_000_000_000L
            )
        )
        val atRepeatedPoint = matcher.match(
            loopRoute,
            NavigationMapFix(
                point = NavigationMapPoint(15.0000, 103.0000),
                bearingDegrees = null,
                accuracyMeters = 8f,
                speedMetersPerSecond = 1f,
                elapsedRealtimeNanos = 2_000_000_000L
            )
        )

        assertNotNull(beforeCrossing)
        assertNotNull(atRepeatedPoint)
        assertTrue(
            "Matcher jumped from the late loop crossing back to route start",
            atRepeatedPoint!!.routeMatch.progressMeters >= beforeCrossing!!.routeMatch.progressMeters
        )
        assertTrue(
            "Repeated endpoint should resolve near the end of the route",
            atRepeatedPoint.routeMatch.geometryDistanceMeters -
                atRepeatedPoint.routeMatch.progressMeters < 5.0
        )
    }

    @Test
    fun poorBearingAccuracyDoesNotPenalizeTheGeometricallyCorrectSegment() {
        val matcher = NavigationHybridMatcher()
        val result = matcher.match(
            parallelReturnRoute,
            NavigationMapFix(
                point = NavigationMapPoint(13.00001, 100.0040),
                bearingDegrees = 270f,
                accuracyMeters = 6f,
                speedMetersPerSecond = 8f,
                elapsedRealtimeNanos = 1_000_000_000L,
                bearingAccuracyDegrees = 90f
            )
        )

        assertNotNull(result)
        assertTrue(result!!.routeMatch.segmentBearingDegrees!! in 70f..110f)
        assertEquals(null, result.headingDeltaDegrees)
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
