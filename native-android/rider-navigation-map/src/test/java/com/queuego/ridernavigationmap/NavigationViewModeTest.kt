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
    fun activeStopChangeClosesOverviewButPreservesFreePan() {
        val controller = NavigationViewModeController()
        controller.onOverview()
        assertEquals(NavigationViewMode.FOLLOW, controller.onActiveStopChanged().mode)

        controller.onUserGesture()
        assertEquals(NavigationViewMode.FREE_PAN, controller.onActiveStopChanged().mode)
    }
}
