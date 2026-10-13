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
}
