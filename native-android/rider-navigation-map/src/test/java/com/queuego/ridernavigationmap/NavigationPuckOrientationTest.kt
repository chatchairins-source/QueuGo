package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationPuckOrientationTest {
    private fun fix(
        bearing: Float,
        speed: Float = 8f,
        bearingAccuracy: Float = 8f
    ) = NavigationMapFix(
        point = NavigationMapPoint(13.0, 100.0),
        bearingDegrees = bearing,
        speedMetersPerSecond = speed,
        bearingAccuracyDegrees = bearingAccuracy
    )

    @Test
    fun northUpMapShowsVehicleBearingOnScreen() {
        val decision = navigationPuckOrientationDecision(fix(90f), 0f)
        assertEquals(90f, decision.rotationDegrees!!, 0.001f)
    }

    @Test
    fun courseUpMapKeepsVehicleArrowPointingUp() {
        val decision = navigationPuckOrientationDecision(fix(90f), 90f)
        assertEquals(0f, decision.rotationDegrees!!, 0.001f)
    }

    @Test
    fun relativeRotationUsesShortestNormalizedWrap() {
        val decision = navigationPuckOrientationDecision(fix(5f), 355f)
        assertEquals(10f, decision.rotationDegrees!!, 0.001f)
    }

    @Test
    fun unreliableStationaryHeadingDoesNotSpinPuck() {
        val decision = navigationPuckOrientationDecision(
            fix(bearing = 220f, speed = 0.2f, bearingAccuracy = 80f),
            cameraBearingDegrees = 30f
        )
        assertNull(decision.rotationDegrees)
        assertEquals(NavigationHeadingConfidence.LOW, decision.headingConfidence)
    }
}
