package com.queuego.ridernavigationmap

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRouteMatcherTest {
    @Test fun mapMatchingSnapsRiderToActiveRoadGeometry() {
        val segment = NavigationRouteSegment(
            id = "active-leg",
            toStopId = "shop-1",
            points = listOf(
                NavigationMapPoint(13.0000, 100.0000),
                NavigationMapPoint(13.0000, 100.0100),
                NavigationMapPoint(13.0100, 100.0100)
            ),
            style = NavigationRouteStyle.ACTIVE
        )
        val match = matchNavigationRoute(segment, NavigationMapPoint(13.0002, 100.0050))
        assertNotNull(match)
        assertTrue(match!!.offRouteMeters < 30.0)
        assertTrue(match.progressMeters > 400.0)
        assertTrue(match.progressMeters < 700.0)
    }
    @Test fun movingRiderStaysOnSameDirectionCarriagewayEvenWhenOppositeIsCloser() {
        val segment = NavigationRouteSegment(
            id = "divided-road",
            toStopId = "shop",
            points = listOf(
                NavigationMapPoint(15.00010, 103.0000),
                NavigationMapPoint(15.00010, 103.0100),
                NavigationMapPoint(15.00000, 103.0100),
                NavigationMapPoint(15.00000, 103.0000)
            ),
            style = NavigationRouteStyle.ACTIVE
        )

        val match = matchNavigationRoute(
            segment = segment,
            point = NavigationMapPoint(15.00002, 103.0060),
            bearingDegrees = 90f,
            speedMetersPerSecond = 9f,
            previousProgressMeters = 600.0
        )

        assertNotNull(match)
        assertTrue(match!!.segmentBearingDegrees!! in 70f..110f)
        assertTrue(match.snapped.latitude > 15.00005)
    }

}
