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

    @Test
    fun poorBearingAccuracyFallsBackToMatchedRoadDirection() {
        val route = NavigationRouteSegment(
            id = "active",
            toStopId = "shop",
            points = listOf(
                NavigationMapPoint(15.0000, 103.0000),
                NavigationMapPoint(15.0000, 103.0100)
            ),
            style = NavigationRouteStyle.ACTIVE
        )
        val state = RiderNavigationMapState(
            rider = NavigationMapFix(
                point = NavigationMapPoint(15.00002, 103.0040),
                bearingDegrees = 270f,
                accuracyMeters = 8f,
                speedMetersPerSecond = 8f,
                bearingAccuracyDegrees = 85f
            ),
            stops = listOf(
                NavigationMapStop(
                    id = "shop",
                    sequence = 1,
                    kind = NavigationStopKind.PICKUP,
                    point = NavigationMapPoint(15.0000, 103.0100)
                )
            ),
            routes = listOf(route),
            activeStopId = "shop"
        )

        val decision = navigationCameraDecision(state, previousBearingDegrees = null)

        assertTrue(decision!!.bearingDegrees!! in 70f..110f)
    }

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

    @Test fun followCameraBeginsTurningBeforeARealCorner() {
        val cornerRoute = NavigationRouteSegment(
            id = "corner",
            toStopId = "shop",
            points = listOf(
                NavigationMapPoint(13.0000, 100.0000),
                NavigationMapPoint(13.0000, 100.0004),
                NavigationMapPoint(13.0004, 100.0004)
            ),
            style = NavigationRouteStyle.ACTIVE
        )
        val state = RiderNavigationMapState(
            rider = NavigationMapFix(
                point = NavigationMapPoint(13.0000, 100.00030),
                bearingDegrees = 90f,
                accuracyMeters = 5f,
                speedMetersPerSecond = 8f,
                bearingAccuracyDegrees = 4f
            ),
            stops = listOf(
                NavigationMapStop(
                    id = "shop",
                    sequence = 1,
                    kind = NavigationStopKind.PICKUP,
                    point = cornerRoute.points.last()
                )
            ),
            routes = listOf(cornerRoute),
            activeStopId = "shop",
            followZoom = 17
        )

        val decision = navigationCameraDecision(state, previousBearingDegrees = 90f)

        assertNotNull(decision)
        assertTrue(decision!!.bearingDegrees!! < 89f)
        assertTrue(decision.bearingDegrees!! > 60f)
        assertTrue(decision.center.latitude > state.rider!!.point.latitude)
    }

    @Test fun fasterRidingLooksFartherAheadForCameraHeading() {
        assertTrue(
            navigationBearingLookAheadMeters(20f, 2_000.0) >
                navigationBearingLookAheadMeters(4f, 2_000.0)
        )
    }

    @Test fun hairpinLookAheadCannotWhipTheCameraAroundInOneDecision() {
        val bounded = navigationBoundAnticipatedBearing(
            currentBearing = 90f,
            futureBearing = 270f
        )
        val delta = kotlin.math.abs(((bounded - 90f + 540f) % 360f) - 180f)
        assertTrue(delta <= 70.001f)
    }

    @Test fun anticipatedBearingBoundHandlesNorthWrap() {
        val bounded = navigationBoundAnticipatedBearing(
            currentBearing = 350f,
            futureBearing = 20f
        )
        assertTrue(bounded < 21f || bounded > 349f)
    }

    @Test fun matchedRoadWinsOverOppositeGpsCourseAtDrivingSpeed() {
        val eastbound = NavigationRouteSegment(
            id = "eastbound",
            toStopId = "shop",
            points = listOf(
                NavigationMapPoint(15.0000, 103.0000),
                NavigationMapPoint(15.0000, 103.0100)
            ),
            style = NavigationRouteStyle.ACTIVE
        )
        val state = RiderNavigationMapState(
            rider = NavigationMapFix(
                point = NavigationMapPoint(15.00001, 103.0040),
                bearingDegrees = 270f,
                accuracyMeters = 5f,
                speedMetersPerSecond = 10f,
                bearingAccuracyDegrees = 4f
            ),
            stops = listOf(
                NavigationMapStop(
                    id = "shop",
                    sequence = 1,
                    kind = NavigationStopKind.PICKUP,
                    point = NavigationMapPoint(15.0000, 103.0100)
                )
            ),
            routes = listOf(eastbound),
            activeStopId = "shop"
        )

        val decision = navigationCameraDecision(state, previousBearingDegrees = 90f)

        assertTrue(decision!!.bearingDegrees!! in 85f..95f)
    }

    @Test fun oppositeRouteMatchCannotFlipCameraWhenMotionAndPreviousCourseAgree() {
        val eastbound = NavigationRouteSegment(
            id = "eastbound-wrong-carriageway",
            toStopId = "shop",
            points = listOf(
                NavigationMapPoint(15.0000, 103.0000),
                NavigationMapPoint(15.0000, 103.0100)
            ),
            style = NavigationRouteStyle.ACTIVE
        )
        val state = RiderNavigationMapState(
            rider = NavigationMapFix(
                point = NavigationMapPoint(15.00001, 103.0040),
                bearingDegrees = 270f,
                accuracyMeters = 5f,
                speedMetersPerSecond = 10f,
                bearingAccuracyDegrees = 4f
            ),
            stops = listOf(
                NavigationMapStop(
                    id = "shop",
                    sequence = 1,
                    kind = NavigationStopKind.PICKUP,
                    point = NavigationMapPoint(15.0000, 103.0100)
                )
            ),
            routes = listOf(eastbound),
            activeStopId = "shop"
        )

        val decision = navigationCameraDecision(state, previousBearingDegrees = 270f)

        assertTrue(decision!!.bearingDegrees!! in 250f..290f)
        assertTrue(decision.center.longitude < state.rider!!.point.longitude)
    }

    @Test fun highwaySpeedUsesStrongForwardPerspective() {
        assertEquals(
            50f,
            navigationTilt(
                speedMetersPerSecond = 20f,
                remainingRouteMeters = 2_000.0,
                accuracyMeters = 5f
            ),
            0.001f
        )
    }

    @Test fun approachingDestinationReducesPerspectiveForPrecision() {
        assertEquals(
            28f,
            navigationTilt(
                speedMetersPerSecond = 16f,
                remainingRouteMeters = 45.0,
                accuracyMeters = 4f
            ),
            0.001f
        )
    }

    @Test fun tiltTransitionsAreBoundedInsteadOfJumping() {
        assertEquals(38f, smoothNavigationTilt(34f, 50f), 0.001f)
        assertEquals(30f, smoothNavigationTilt(34f, 24f), 0.001f)
        assertTrue(navigationTiltChangedEnough(34f, 38f))
        assertTrue(!navigationTiltChangedEnough(34f, 34.4f))
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

    @Test fun idleMapReturnsToNormalNorthUpTopDownView() {
        val state = RiderNavigationMapState(
            rider = NavigationMapFix(
                point = NavigationMapPoint(13.0, 100.0),
                bearingDegrees = 210f,
                speedMetersPerSecond = 0.5f
            ),
            followZoom = 17
        )
        val decision = navigationCameraDecision(state, previousBearingDegrees = 92f)!!
        assertEquals(0f, decision.bearingDegrees!!, 0.001f)
        assertEquals(0f, decision.tiltDegrees, 0.001f)
        assertEquals(17, decision.zoom)
        assertEquals(state.rider!!.point, decision.center)
    }

    @Test fun explicitIdlePerspectiveTestModeKeepsHeadingAndTilt() {
        val state = RiderNavigationMapState(
            rider = NavigationMapFix(
                point = NavigationMapPoint(13.0, 100.0),
                bearingDegrees = 90f,
                accuracyMeters = 5f,
                speedMetersPerSecond = 8f
            ),
            followRider = true,
            perspectiveCameraEnabled = true,
            followZoom = 17
        )
        val decision = navigationCameraDecision(state, previousBearingDegrees = 90f)!!
        assertTrue(decision.bearingDegrees!! in 89f..91f)
        assertTrue(decision.tiltDegrees >= 48f)
        assertTrue(decision.center.longitude > state.rider!!.point.longitude)
        assertTrue(
            navigationHaversineMeters(state.rider!!.point, decision.center) in 45.0..55.0
        )
    }

    @Test fun freeDriveLookAheadGrowsWithSpeedWithoutHugeJumps() {
        assertTrue(navigationFreeDriveLookAheadMeters(12f) > navigationFreeDriveLookAheadMeters(4f))
        assertTrue(navigationFreeDriveLookAheadMeters(4f) > navigationFreeDriveLookAheadMeters(0.5f))
        assertTrue(navigationFreeDriveLookAheadMeters(25f) <= 80.0)
    }

    @Test fun northCrossingTakesTwoDegreeTurn() {
        assertEquals(0f, smoothNavigationBearing(359f, 1f, 0.5f)!!, 0.001f)
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

    @Test fun subDegreeBearingNoiseStaysInsideDeadband() {
        assertEquals(
            90f,
            smoothNavigationBearing(previous = 90f, target = 90.3f, factor = 0.5f)!!,
            0.001f
        )
    }

    @Test fun nativeFollowCameraTurnsMoreAggressivelyAsSpeedRises() {
        assertTrue(navigationBearingSmoothingFactor(20f) >= 0.65f)
        assertTrue(navigationBearingSmoothingFactor(20f) > navigationBearingSmoothingFactor(1f))
        assertTrue(navigationBearingSmoothingFactor(8f) in 0.50f..0.54f)
        assertTrue(navigationTurnAnticipationFactor(20f) > navigationTurnAnticipationFactor(3f))
    }

    @Test fun largerTurnsReceiveLongerRotationAnimation() {
        val small = navigationRotationDurationMillis(
            currentBearing = 90f,
            targetBearing = 95f,
            cadenceMillis = 500L
        )
        val corner = navigationRotationDurationMillis(
            currentBearing = 90f,
            targetBearing = 180f,
            cadenceMillis = 500L
        )
        assertTrue(corner > small)
        assertTrue(corner <= 900L)
        assertTrue(small >= 420L)
    }

    @Test fun movingCameraThresholdKeepsPuckFromWalkingAcrossScreen() {
        assertTrue(navigationCameraMovementThresholdMeters(10f) <= 0.5)
        assertTrue(navigationCameraMovementThresholdMeters(3f) <= 1.0)
        assertTrue(navigationCameraMovementThresholdMeters(0.5f) > 2.0)
    }

    @Test fun highSpeedSameTurnFinishesFasterThanLowSpeed() {
        val fast = navigationRotationDurationMillis(
            currentBearing = 0f,
            targetBearing = 90f,
            cadenceMillis = 500L,
            speedMetersPerSecond = 20f
        )
        val slow = navigationRotationDurationMillis(
            currentBearing = 0f,
            targetBearing = 90f,
            cadenceMillis = 500L,
            speedMetersPerSecond = 3f
        )
        assertTrue(fast < slow)
        assertTrue(fast <= 460L)
    }

    @Test fun rotationDurationUsesShortestTurnAcrossNorth() {
        val acrossNorth = navigationRotationDurationMillis(
            currentBearing = 355f,
            targetBearing = 5f,
            cadenceMillis = 500L
        )
        val longCorner = navigationRotationDurationMillis(
            currentBearing = 355f,
            targetBearing = 175f,
            cadenceMillis = 500L
        )
        assertTrue(acrossNorth < longCorner)
        assertEquals(10f, navigationShortestBearingDelta(355f, 5f), 0.001f)
    }

    @Test fun cameraEaseInOutIsContinuousAndCentered() {
        assertEquals(0f, navigationCameraEaseInOut(0f), 0.001f)
        assertEquals(0.5f, navigationCameraEaseInOut(0.5f), 0.001f)
        assertEquals(1f, navigationCameraEaseInOut(1f), 0.001f)
        assertTrue(navigationCameraEaseInOut(0.25f) < 0.25f)
        assertTrue(navigationCameraEaseInOut(0.75f) > 0.75f)
        assertTrue(navigationCameraEaseInOut(0.05f) > 0.01f)
    }
}
