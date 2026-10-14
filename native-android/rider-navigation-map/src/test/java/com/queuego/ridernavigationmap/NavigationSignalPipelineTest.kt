package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSignalPipelineTest {
    private val route = NavigationRouteSegment(
        id = "active",
        toStopId = "shop",
        points = listOf(
            NavigationMapPoint(13.0000, 100.0000),
            NavigationMapPoint(13.0000, 100.0100)
        ),
        style = NavigationRouteStyle.ACTIVE
    )

    @Test
    fun goodNearbyFixUsesMatchedRoadForDisplayOnly() {
        val pipeline = NavigationSignalPipeline()
        val state = pipeline.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.00005, 100.0040),
                bearingDegrees = 90f,
                accuracyMeters = 8f,
                speedMetersPerSecond = 7f,
                elapsedRealtimeNanos = 1_000_000_000L
            ),
            route
        )

        assertTrue(state.accepted)
        assertNotNull(state.displayFix)
        assertNotNull(state.routeMatch)
        assertEquals(NavigationPuckState.ACTIVE, state.puckState)
        assertTrue(kotlin.math.abs(state.displayFix!!.point.latitude - 13.0000) < 0.00001)
        assertTrue(kotlin.math.abs(state.rawFix.point.latitude - 13.00005) < 0.00001)
    }

    @Test
    fun rejectedTeleportHoldsLastDisplayFixWithoutReplacingRawFix() {
        val pipeline = NavigationSignalPipeline()
        val first = pipeline.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.0000, 100.0010),
                accuracyMeters = 8f,
                speedMetersPerSecond = 5f,
                elapsedRealtimeNanos = 1_000_000_000L
            ),
            route
        )
        val secondRaw = NavigationMapFix(
            point = NavigationMapPoint(13.0200, 100.0010),
            accuracyMeters = 8f,
            speedMetersPerSecond = 5f,
            elapsedRealtimeNanos = 2_000_000_000L
        )
        val second = pipeline.update(secondRaw, route)

        assertFalse(second.accepted)
        assertEquals(NavigationPuckState.HELD, second.puckState)
        assertEquals(first.displayFix, second.displayFix)
        assertEquals(secondRaw, second.rawFix)
    }
}
