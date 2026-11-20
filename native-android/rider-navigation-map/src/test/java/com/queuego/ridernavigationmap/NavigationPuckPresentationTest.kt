package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPuckPresentationTest {
    @Test
    fun staleAndLostOverrideSignalState() {
        val signal = NavigationSignalState(
            rawFix = NavigationMapFix(NavigationMapPoint(15.0, 103.0)),
            displayFix = NavigationMapFix(NavigationMapPoint(15.0, 103.0)),
            quality = NavigationFixQuality.GOOD,
            routeMatch = null,
            puckState = NavigationPuckState.ACTIVE,
            accepted = true
        )

        val stale = navigationPuckPresentation(
            signal,
            NavigationPuckFreshnessState(NavigationPuckFreshness.STALE, 6_000L)
        )
        val lost = navigationPuckPresentation(
            signal,
            NavigationPuckFreshnessState(NavigationPuckFreshness.LOST, 16_000L)
        )

        assertEquals(NavigationPuckVisualState.STALE, stale.state)
        assertTrue(stale.freezePosition)
        assertEquals(NavigationPuckVisualState.LOST, lost.state)
        assertTrue(lost.freezePosition)
    }

    @Test
    fun activePuckRotatesWithoutAccuracyHalo() {
        val signal = NavigationSignalState(
            rawFix = NavigationMapFix(NavigationMapPoint(15.0, 103.0)),
            displayFix = NavigationMapFix(NavigationMapPoint(15.0, 103.0)),
            quality = NavigationFixQuality.GOOD,
            routeMatch = null,
            puckState = NavigationPuckState.ACTIVE,
            accepted = true
        )

        val presentation = navigationPuckPresentation(
            signal,
            NavigationPuckFreshnessState(NavigationPuckFreshness.FRESH, 500L)
        )

        assertEquals(NavigationPuckVisualState.ACTIVE, presentation.state)
        assertTrue(presentation.rotateWithBearing)
        assertFalse(presentation.showAccuracyHalo)
        assertFalse(presentation.freezePosition)
    }

    @Test
    fun weakGpsFreezesAndShowsAccuracyHalo() {
        val signal = NavigationSignalState(
            rawFix = NavigationMapFix(NavigationMapPoint(15.0, 103.0)),
            displayFix = NavigationMapFix(NavigationMapPoint(15.0, 103.0)),
            quality = NavigationFixQuality.WEAK,
            routeMatch = null,
            puckState = NavigationPuckState.WEAK_GPS,
            accepted = true
        )

        val presentation = navigationPuckPresentation(
            signal,
            NavigationPuckFreshnessState(NavigationPuckFreshness.FRESH, 500L)
        )

        assertEquals(NavigationPuckVisualState.WEAK_GPS, presentation.state)
        assertTrue(presentation.showAccuracyHalo)
        assertTrue(presentation.freezePosition)
        assertFalse(presentation.rotateWithBearing)
    }
}
