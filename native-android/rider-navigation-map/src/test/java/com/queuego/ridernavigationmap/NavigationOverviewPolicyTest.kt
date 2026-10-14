package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationOverviewPolicyTest {
    @Test
    fun overviewIncludesRiderRemainingStopsAndRouteGeometry() {
        val state = RiderNavigationMapState(
            rider = NavigationMapFix(
                point = NavigationMapPoint(14.9900, 103.1000)
            ),
            stops = listOf(
                NavigationMapStop(
                    id = "pickup",
                    sequence = 1,
                    kind = NavigationStopKind.PICKUP,
                    point = NavigationMapPoint(15.0000, 103.1100)
                ),
                NavigationMapStop(
                    id = "drop",
                    sequence = 2,
                    kind = NavigationStopKind.DROPOFF,
                    point = NavigationMapPoint(15.0200, 103.1400)
                )
            ),
            routes = listOf(
                NavigationRouteSegment(
                    id = "route",
                    toStopId = "pickup",
                    points = listOf(
                        NavigationMapPoint(14.9900, 103.1000),
                        NavigationMapPoint(15.0100, 103.1500)
                    ),
                    style = NavigationRouteStyle.ACTIVE
                )
            ),
            activeStopId = "pickup"
        )

        val bounds = navigationOverviewBounds(state)

        assertNotNull(bounds)
        assertEquals(14.9900, bounds!!.south, 0.000001)
        assertEquals(15.0200, bounds.north, 0.000001)
        assertEquals(103.1000, bounds.west, 0.000001)
        assertEquals(103.1500, bounds.east, 0.000001)
        assertTrue(bounds.spanMeters > 4_000.0)
        assertTrue(navigationOverviewZoom(bounds) in 10..17)
    }

    @Test
    fun completedStopsDoNotExpandOverview() {
        val state = RiderNavigationMapState(
            stops = listOf(
                NavigationMapStop(
                    id = "done",
                    sequence = 1,
                    kind = NavigationStopKind.PICKUP,
                    point = NavigationMapPoint(10.0, 100.0),
                    completed = true
                ),
                NavigationMapStop(
                    id = "next",
                    sequence = 2,
                    kind = NavigationStopKind.DROPOFF,
                    point = NavigationMapPoint(15.0, 103.0)
                )
            )
        )

        val bounds = navigationOverviewBounds(state)

        assertNotNull(bounds)
        assertEquals(15.0, bounds!!.center.latitude, 0.000001)
        assertEquals(103.0, bounds.center.longitude, 0.000001)
    }
    @Test
    fun bottomJobCardShiftsOverviewCenterUpAndZoomsOut() {
        val bounds = NavigationOverviewBounds(
            south = 15.0000,
            west = 103.0000,
            north = 15.0200,
            east = 103.0200,
            center = NavigationMapPoint(15.0100, 103.0100),
            spanMeters = 2_500.0
        )

        val full = navigationOverviewViewportDecision(
            bounds = bounds,
            insets = NavigationViewportInsets(),
            viewportWidthPx = 1080,
            viewportHeightPx = 2200
        )
        val occluded = navigationOverviewViewportDecision(
            bounds = bounds,
            insets = NavigationViewportInsets(topPx = 72, bottomPx = 1000),
            viewportWidthPx = 1080,
            viewportHeightPx = 2200
        )

        assertTrue(occluded.center.latitude < full.center.latitude)
        assertTrue(occluded.zoom < full.zoom)
    }

    @Test
    fun asymmetricSidePanelShiftsOverviewCenterAwayFromOcclusion() {
        val bounds = NavigationOverviewBounds(
            south = 15.0000,
            west = 103.0000,
            north = 15.0200,
            east = 103.0400,
            center = NavigationMapPoint(15.0100, 103.0200),
            spanMeters = 4_500.0
        )

        val decision = navigationOverviewViewportDecision(
            bounds = bounds,
            insets = NavigationViewportInsets(rightPx = 400),
            viewportWidthPx = 1200,
            viewportHeightPx = 2000
        )

        assertTrue(decision.center.longitude > bounds.center.longitude)
    }

}
