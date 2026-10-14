package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTraceReplayTest {
    private val straightRoute = NavigationRouteSegment(
        id = "straight",
        toStopId = "drop",
        points = listOf(
            NavigationMapPoint(15.0000, 103.0000),
            NavigationMapPoint(15.0000, 103.0200)
        ),
        style = NavigationRouteStyle.ACTIVE
    )

    @Test
    fun stationaryTraceDoesNotMakePuckDance() {
        val pipeline = NavigationSignalPipeline()
        val trace = listOf(
            NavigationMapFix(
                NavigationMapPoint(15.000000, 103.005000),
                bearingDegrees = 90f,
                accuracyMeters = 8f,
                speedMetersPerSecond = 0.2f,
                elapsedRealtimeNanos = 1_000_000_000L
            ),
            NavigationMapFix(
                NavigationMapPoint(15.000006, 103.005002),
                bearingDegrees = 140f,
                accuracyMeters = 8f,
                speedMetersPerSecond = 0.2f,
                elapsedRealtimeNanos = 2_000_000_000L
            ),
            NavigationMapFix(
                NavigationMapPoint(14.999995, 103.004998),
                bearingDegrees = 260f,
                accuracyMeters = 9f,
                speedMetersPerSecond = 0.1f,
                elapsedRealtimeNanos = 3_000_000_000L
            ),
            NavigationMapFix(
                NavigationMapPoint(15.000004, 103.005003),
                bearingDegrees = 20f,
                accuracyMeters = 8f,
                speedMetersPerSecond = 0.0f,
                elapsedRealtimeNanos = 4_000_000_000L
            )
        )

        val rendered = trace.map { pipeline.update(it, straightRoute).displayFix!! }
        val origin = rendered.first().point
        val maxDrift = rendered.maxOf { navigationHaversineMeters(origin, it.point) }

        assertTrue(maxDrift < 1.0)
    }

    @Test
    fun oneBadTeleportIsHeldAndNormalTraceRecovers() {
        val pipeline = NavigationSignalPipeline()
        val first = pipeline.update(
            NavigationMapFix(
                NavigationMapPoint(15.0000, 103.0020),
                bearingDegrees = 90f,
                accuracyMeters = 8f,
                speedMetersPerSecond = 6f,
                elapsedRealtimeNanos = 1_000_000_000L
            ),
            straightRoute
        )
        val normal = pipeline.update(
            NavigationMapFix(
                NavigationMapPoint(15.0000, 103.0021),
                bearingDegrees = 90f,
                accuracyMeters = 8f,
                speedMetersPerSecond = 6f,
                elapsedRealtimeNanos = 2_000_000_000L
            ),
            straightRoute
        )
        val teleport = pipeline.update(
            NavigationMapFix(
                NavigationMapPoint(15.0200, 103.0021),
                bearingDegrees = 90f,
                accuracyMeters = 8f,
                speedMetersPerSecond = 6f,
                elapsedRealtimeNanos = 3_000_000_000L
            ),
            straightRoute
        )
        val recovered = pipeline.update(
            NavigationMapFix(
                NavigationMapPoint(15.0000, 103.0022),
                bearingDegrees = 90f,
                accuracyMeters = 8f,
                speedMetersPerSecond = 6f,
                elapsedRealtimeNanos = 4_000_000_000L
            ),
            straightRoute
        )

        assertTrue(first.accepted)
        assertTrue(normal.accepted)
        assertFalse(teleport.accepted)
        assertEquals(normal.displayFix, teleport.displayFix)
        assertTrue(recovered.accepted)
        assertTrue(recovered.displayFix!!.point.longitude > normal.displayFix!!.point.longitude)
    }

    @Test
    fun headingAndContinuityKeepParallelReturnRoadFromStealingMatch() {
        val route = NavigationRouteSegment(
            id = "parallel",
            toStopId = "drop",
            points = listOf(
                NavigationMapPoint(15.0000, 103.0000),
                NavigationMapPoint(15.0000, 103.0100),
                NavigationMapPoint(15.0001, 103.0100),
                NavigationMapPoint(15.0001, 103.0000)
            ),
            style = NavigationRouteStyle.ACTIVE
        )
        val matcher = NavigationHybridMatcher()

        val first = matcher.match(
            route,
            NavigationMapFix(
                NavigationMapPoint(15.00004, 103.0040),
                bearingDegrees = 90f,
                accuracyMeters = 8f,
                speedMetersPerSecond = 8f,
                elapsedRealtimeNanos = 1_000_000_000L
            )
        )!!
        val second = matcher.match(
            route,
            NavigationMapFix(
                NavigationMapPoint(15.00005, 103.0050),
                bearingDegrees = 90f,
                accuracyMeters = 8f,
                speedMetersPerSecond = 8f,
                elapsedRealtimeNanos = 2_000_000_000L
            )
        )!!

        assertTrue(first.routeMatch.segmentBearingDegrees!! in 70f..110f)
        assertTrue(second.routeMatch.segmentBearingDegrees!! in 70f..110f)
        assertTrue(second.routeMatch.progressMeters > first.routeMatch.progressMeters)
    }

    @Test
    fun weakGpsBurstFreezesThenGoodGpsMovesAgain() {
        val pipeline = NavigationSignalPipeline()
        val good = pipeline.update(
            NavigationMapFix(
                NavigationMapPoint(15.0000, 103.0040),
                bearingDegrees = 90f,
                accuracyMeters = 10f,
                speedMetersPerSecond = 3f,
                elapsedRealtimeNanos = 1_000_000_000L
            ),
            straightRoute
        )
        val weak = pipeline.update(
            NavigationMapFix(
                NavigationMapPoint(15.0002, 103.0040),
                bearingDegrees = 40f,
                accuracyMeters = 75f,
                speedMetersPerSecond = 0.5f,
                elapsedRealtimeNanos = 2_000_000_000L
            ),
            straightRoute
        )
        val goodAgain = pipeline.update(
            NavigationMapFix(
                NavigationMapPoint(15.0000, 103.0042),
                bearingDegrees = 90f,
                accuracyMeters = 10f,
                speedMetersPerSecond = 3f,
                elapsedRealtimeNanos = 3_000_000_000L
            ),
            straightRoute
        )

        assertEquals(good.displayFix, weak.displayFix)
        assertEquals(NavigationPuckState.WEAK_GPS, weak.puckState)
        assertTrue(goodAgain.accepted)
        assertTrue(goodAgain.displayFix!!.point.longitude > good.displayFix!!.point.longitude)
    }
}
