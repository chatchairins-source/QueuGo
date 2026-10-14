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
    fun displayAdapterPreservesAuthoritativeStopsRoutesAndActiveStop() {
        val pipeline = NavigationSignalPipeline()
        val raw = NavigationMapFix(
            point = NavigationMapPoint(13.00005, 100.0040),
            bearingDegrees = 90f,
            accuracyMeters = 8f,
            speedMetersPerSecond = 7f,
            elapsedRealtimeNanos = 1_000_000_000L
        )
        val stop = NavigationMapStop(
            id = "shop",
            sequence = 1,
            kind = NavigationStopKind.PICKUP,
            point = NavigationMapPoint(13.0000, 100.0100)
        )
        val authoritative = RiderNavigationMapState(
            rider = raw,
            stops = listOf(stop),
            routes = listOf(route),
            activeStopId = "shop",
            followRider = true,
            followZoom = 17
        )

        val display = pipeline.applyToDisplayState(authoritative)

        assertEquals(authoritative.stops, display.stops)
        assertEquals(authoritative.routes, display.routes)
        assertEquals(authoritative.activeStopId, display.activeStopId)
        assertEquals(authoritative.followRider, display.followRider)
        assertEquals(authoritative.followZoom, display.followZoom)
        assertEquals(raw.accuracyMeters, display.rider?.accuracyMeters)
        assertTrue(display.rider != raw)
        assertTrue(kotlin.math.abs(display.rider!!.point.latitude - 13.0000) < 0.00001)
    }

    @Test
    fun lowConfidenceMatchHoldsLastDisplayForTwoSamplesBeforeRelease() {
        val shortRoute = NavigationRouteSegment(
            id = "short",
            toStopId = "shop",
            points = listOf(
                NavigationMapPoint(13.0000, 100.0000),
                NavigationMapPoint(13.0000, 100.0040)
            ),
            style = NavigationRouteStyle.ACTIVE
        )
        val pipeline = NavigationSignalPipeline()
        val first = pipeline.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.0000, 100.0010),
                bearingDegrees = 90f,
                accuracyMeters = 8f,
                speedMetersPerSecond = 6f,
                elapsedRealtimeNanos = 1_000_000_000L
            ),
            shortRoute
        )
        val ambiguous1 = pipeline.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.00022, 100.00105),
                bearingDegrees = 270f,
                accuracyMeters = 18f,
                speedMetersPerSecond = 6f,
                elapsedRealtimeNanos = 2_000_000_000L
            ),
            shortRoute
        )
        val ambiguous2 = pipeline.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.00023, 100.00108),
                bearingDegrees = 270f,
                accuracyMeters = 18f,
                speedMetersPerSecond = 6f,
                elapsedRealtimeNanos = 3_000_000_000L
            ),
            shortRoute
        )
        val ambiguous3 = pipeline.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.00024, 100.00110),
                bearingDegrees = 270f,
                accuracyMeters = 18f,
                speedMetersPerSecond = 6f,
                elapsedRealtimeNanos = 4_000_000_000L
            ),
            shortRoute
        )

        assertEquals(first.displayFix, ambiguous1.displayFix)
        assertEquals(first.displayFix, ambiguous2.displayFix)
        assertTrue(ambiguous3.displayFix != first.displayFix)
    }

    @Test
    fun weakGpsJitterFreezesAtLastGoodDisplayFix() {
        val pipeline = NavigationSignalPipeline()
        val first = pipeline.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.0000, 100.0010),
                bearingDegrees = 90f,
                accuracyMeters = 12f,
                speedMetersPerSecond = 2f,
                elapsedRealtimeNanos = 1_000_000_000L
            ),
            route
        )
        val weak = pipeline.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.00020, 100.0010),
                bearingDegrees = 90f,
                accuracyMeters = 75f,
                speedMetersPerSecond = 0.5f,
                elapsedRealtimeNanos = 2_000_000_000L
            ),
            route
        )

        assertTrue(weak.accepted)
        assertEquals(NavigationPuckState.WEAK_GPS, weak.puckState)
        assertEquals(first.displayFix, weak.displayFix)
        assertTrue(weak.rawFix.point != weak.displayFix?.point)
    }

    @Test
    fun equivalentBackendRefreshDoesNotReprocessTheSameGpsFix() {
        val pipeline = NavigationSignalPipeline()
        val raw = NavigationMapFix(
            point = NavigationMapPoint(13.00005, 100.0040),
            bearingDegrees = 90f,
            accuracyMeters = 8f,
            speedMetersPerSecond = 7f,
            elapsedRealtimeNanos = 1_000_000_000L
        )

        val first = pipeline.update(raw, route)
        val duplicate = pipeline.update(raw.copy(), route)
        val metrics = pipeline.metrics()

        assertEquals(first, duplicate)
        assertEquals(1L, metrics.sampleCount)
        assertEquals(1L, metrics.acceptedCount)
        assertEquals(0L, metrics.rejectedCount)
        assertEquals(null, metrics.lastRejectionReason)
    }

    @Test
    fun shadowMetricsTrackRejectedTeleportWithoutMutatingRawFix() {
        val pipeline = NavigationSignalPipeline()
        pipeline.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.0000, 100.0010),
                accuracyMeters = 8f,
                speedMetersPerSecond = 5f,
                elapsedRealtimeNanos = 1_000_000_000L
            ),
            route
        )
        val teleport = NavigationMapFix(
            point = NavigationMapPoint(13.0200, 100.0010),
            accuracyMeters = 8f,
            speedMetersPerSecond = 5f,
            elapsedRealtimeNanos = 2_000_000_000L
        )

        val signal = pipeline.update(teleport, route)
        val metrics = pipeline.metrics()

        assertFalse(signal.accepted)
        assertEquals(2L, metrics.sampleCount)
        assertEquals(1L, metrics.acceptedCount)
        assertEquals(1L, metrics.rejectedCount)
        assertEquals(1L, metrics.heldPuckCount)
        assertEquals("implausible-teleport", metrics.lastRejectionReason)
        assertTrue((metrics.lastCandidateDeltaMeters ?: 0.0) > 1_000.0)
        assertEquals(teleport, signal.rawFix)
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
