package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationCameraPolicyTest {
    private val route = NavigationRouteSegment(
        id = "leg-1",
        toStopId = "shop",
        points = listOf(
            NavigationMapPoint(13.0000, 100.0000),
            NavigationMapPoint(13.0000, 100.0100),
            NavigationMapPoint(13.0100, 100.0100)
        ),
        style = NavigationRouteStyle.ACTIVE
    )
    private val stop = NavigationMapStop(
        id = "shop",
        sequence = 1,
        kind = NavigationStopKind.PICKUP,
        point = route.points.last()
    )

    @Test fun highwaySpeedZoomsOutAndLooksAhead() {
        val state = RiderNavigationMapState(
            rider = NavigationMapFix(
                point = NavigationMapPoint(13.0001, 100.0040),
                bearingDegrees = 90f,
                accuracyMeters = 5f,
                speedMetersPerSecond = 20f
            ),
            stops = listOf(stop),
            routes = listOf(route),
            activeStopId = "shop",
            followZoom = 17
        )
        val decision = navigationCameraDecision(state, previousBearingDegrees = 88f)
        assertNotNull(decision)
        assertEquals(15, decision!!.zoom)
        assertTrue(decision.center.longitude > state.rider!!.point.longitude)
        assertTrue(decision.bearingDegrees!! in 88f..91f)
    }

    @Test fun approachingDestinationZoomsInEvenWhenMovingFast() {
        assertEquals(
            19,
            navigationZoom(
                baseZoom = 17,
                speedMetersPerSecond = 16f,
                remainingRouteMeters = 45.0,
                accuracyMeters = 4f
            )
        )
    }

    @Test fun slowNoisyGpsPrefersMatchedRoadDirection() {
        val state = RiderNavigationMapState(
            rider = NavigationMapFix(
                point = NavigationMapPoint(13.0001, 100.0040),
                bearingDegrees = 210f,
                accuracyMeters = 6f,
                speedMetersPerSecond = 0.7f
            ),
            stops = listOf(stop),
            routes = listOf(route),
            activeStopId = "shop"
        )
        val decision = navigationCameraDecision(state, previousBearingDegrees = 92f)
        assertNotNull(decision)
        assertTrue(decision!!.bearingDegrees!! in 89f..93f)
    }

    @Test fun bearingSmoothingUsesShortestTurnAcrossNorth() {
        val smoothed = smoothNavigationBearing(previous = 355f, target = 5f, factor = 0.5f)
        assertNotNull(smoothed)
        assertTrue(smoothed!! < 1f || smoothed > 359f)
    }

    @Test fun microBearingChangesDoNotWobbleTheCamera() {
        assertTrue(!navigationBearingChangedEnough(90f, 91.4f))
        assertTrue(navigationBearingChangedEnough(90f, 93f))
    }

    @Test fun cameraMoveThresholdSuppressesStandstillJitter() {
        assertTrue(navigationCameraMovementThresholdMeters(0.5f) >
            navigationCameraMovementThresholdMeters(8f))
    }

    @Test fun poorGpsAccuracyAvoidsOverZooming() {
        assertEquals(
            18,
            navigationZoom(
                baseZoom = 17,
                speedMetersPerSecond = 0.5f,
                remainingRouteMeters = 40.0,
                accuracyMeters = 90f
            )
        )
    }
}
