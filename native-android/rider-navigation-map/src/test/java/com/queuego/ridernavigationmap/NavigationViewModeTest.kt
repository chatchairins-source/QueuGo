package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationViewModeTest {
    @Test
    fun userGestureLeavesFollowAndShowsRecenter() {
        val controller = NavigationViewModeController()

        val state = controller.onUserGesture()

        assertEquals(NavigationViewMode.FREE_PAN, state.mode)
        assertTrue(state.recenterVisible)
    }

    @Test
    fun recenterReturnsToFollow() {
        val controller = NavigationViewModeController()
        controller.onUserGesture()

        val state = controller.onRecenter()

        assertEquals(NavigationViewMode.FOLLOW, state.mode)
        assertFalse(state.recenterVisible)
    }

    @Test
    fun idleTimeoutReturnsActiveNavigationToFollow() {
        val controller = NavigationViewModeController()
        controller.onUserGesture()

        val state = controller.onIdleTimeout(navigationActive = true)

        assertEquals(NavigationViewMode.FOLLOW, state.mode)
        assertFalse(state.recenterVisible)
        assertEquals(8_000L, NAVIGATION_FREE_PAN_AUTO_FOLLOW_MS)
    }

    @Test
    fun idleTimeoutDoesNotForceIdleMapBackToFollow() {
        val controller = NavigationViewModeController()
        controller.onUserGesture()

        val state = controller.onIdleTimeout(navigationActive = false)

        assertEquals(NavigationViewMode.FREE_PAN, state.mode)
        assertTrue(state.recenterVisible)
    }

    @Test
    fun activeStopChangePreservesOverviewAndFreePanUntilWorkEnds() {
        val controller = NavigationViewModeController()
        controller.onOverview()
        assertEquals(NavigationViewMode.OVERVIEW, controller.onActiveStopChanged(navigationActive = true).mode)

        controller.onUserGesture()
        assertEquals(NavigationViewMode.FREE_PAN, controller.onActiveStopChanged(navigationActive = true).mode)

        controller.onOverview()
        assertEquals(NavigationViewMode.FOLLOW, controller.onActiveStopChanged(navigationActive = false).mode)
    }

    @Test
    fun twoFingerPinchAndRotationKeepWebFollowOwnership() {
        assertTrue(navigationTouchOwnsFollow(pointerCount = 2, multiTouchStarted = false))
        assertTrue(navigationTouchOwnsFollow(pointerCount = 1, multiTouchStarted = true))
        assertFalse(navigationTouchOwnsFollow(pointerCount = 1, multiTouchStarted = false))
        assertFalse(navigationTouchOwnsFollow(pointerCount = 0, multiTouchStarted = false))
        val controller = NavigationViewModeController()
        // A two-finger transform is not a request to enter FREE_PAN.
        if (!navigationTouchOwnsFollow(pointerCount = 2, multiTouchStarted = false)) {
            controller.onUserGesture()
        }
        assertEquals(NavigationViewMode.FOLLOW, controller.state().mode)
    }

    @Test
    fun pinchZoomReanchorsWithTheSameTimingAsQueueGoWeb() {
        assertEquals(110L, NAVIGATION_TRANSFORM_REANCHOR_MS)
        assertEquals(12_000L, NAVIGATION_MANUAL_ZOOM_HOLD_MS)
        assertEquals(8_000L, NAVIGATION_FREE_PAN_AUTO_FOLLOW_MS)
    }
}
