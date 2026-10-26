package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiderPuckParityTest {
    @Test
    fun lowSpeedKeepsPreviousHeadingLikeWeb() {
        assertEquals(42f, smoothRiderPuckHeading(42f, 130f, 0.7f)!!, 0.001f)
    }

    @Test
    fun mediumSpeedUsesWebHeadingGain() {
        assertEquals(30.6f, smoothRiderPuckHeading(0f, 90f, 6f)!!, 0.001f)
    }

    @Test
    fun highSpeedUsesWebHeadingGain() {
        assertEquals(37.8f, smoothRiderPuckHeading(0f, 90f, 12f)!!, 0.001f)
    }

    @Test
    fun headingTakesShortestTurnAcrossNorth() {
        assertEquals(356.8f, smoothRiderPuckHeading(350f, 10f, 6f)!!, 0.001f)
        assertEquals(0f, interpolateRiderPuckBearing(350f, 10f, 0.5f)!!, 0.001f)
    }

    @Test
    fun missingRawHeadingPreservesKnownHeading() {
        assertEquals(123f, smoothRiderPuckHeading(123f, null, 8f)!!, 0.001f)
        assertNull(smoothRiderPuckHeading(null, null, 8f))
    }
}
