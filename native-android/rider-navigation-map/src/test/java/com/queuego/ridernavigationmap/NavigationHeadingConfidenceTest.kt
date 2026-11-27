package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationHeadingConfidenceTest {
    @Test
    fun accurateDrivingBearingIsHighConfidence() {
        val fix = NavigationMapFix(
            point = NavigationMapPoint(15.0, 103.0),
            bearingDegrees = 92f,
            speedMetersPerSecond = 8f,
            bearingAccuracyDegrees = 8f,
            speedAccuracyMetersPerSecond = 0.8f
        )

        val estimate = navigationHeadingEstimate(fix)

        assertEquals(NavigationHeadingConfidence.HIGH, estimate.confidence)
        assertEquals(92f, navigationUsableGpsBearing(fix))
    }

    @Test
    fun poorBearingAccuracyIsNotUsedForRotationOrMatching() {
        val fix = NavigationMapFix(
            point = NavigationMapPoint(15.0, 103.0),
            bearingDegrees = 270f,
            speedMetersPerSecond = 8f,
            bearingAccuracyDegrees = 80f,
            speedAccuracyMetersPerSecond = 1f
        )

        val estimate = navigationHeadingEstimate(fix)

        assertEquals(NavigationHeadingConfidence.LOW, estimate.confidence)
        assertNull(navigationUsableGpsBearing(fix))
    }

    @Test
    fun missingAccuracyKeepsLegacyDrivingBearingUsable() {
        val fix = NavigationMapFix(
            point = NavigationMapPoint(15.0, 103.0),
            bearingDegrees = 45f,
            speedMetersPerSecond = 6f
        )

        val estimate = navigationHeadingEstimate(fix)

        assertEquals(NavigationHeadingConfidence.MEDIUM, estimate.confidence)
        assertEquals(45f, navigationUsableGpsBearing(fix))
    }

    @Test
    fun uncertainSpeedDowngradesHeadingConfidence() {
        val fix = NavigationMapFix(
            point = NavigationMapPoint(15.0, 103.0),
            bearingDegrees = 90f,
            speedMetersPerSecond = 4f,
            bearingAccuracyDegrees = 10f,
            speedAccuracyMetersPerSecond = 4f
        )

        assertEquals(
            NavigationHeadingConfidence.MEDIUM,
            navigationHeadingEstimate(fix).confidence
        )
    }

    @Test
    fun standstillBearingIsRejectedEvenWhenSensorClaimsAccuracy() {
        val fix = NavigationMapFix(
            point = NavigationMapPoint(15.0, 103.0),
            bearingDegrees = 180f,
            speedMetersPerSecond = 0.3f,
            bearingAccuracyDegrees = 5f
        )

        assertEquals(NavigationHeadingConfidence.LOW, navigationHeadingEstimate(fix).confidence)
        assertNull(navigationUsableGpsBearing(fix))
    }
}
