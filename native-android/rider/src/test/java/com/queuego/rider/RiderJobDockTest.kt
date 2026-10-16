package com.queuego.rider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderJobDockTest {
    @Test fun deliberateFlingOverridesReleasePosition() {
        assertTrue(riderDockShouldExpand(100f, 92f, 450f, -900f, 600f))
        assertFalse(riderDockShouldExpand(440f, 92f, 450f, 900f, 600f))
    }

    @Test fun SlowReleaseSnapsToNearestStableHeight() {
        assertFalse(riderDockShouldExpand(270f, 92f, 450f, -30f, 600f))
        assertTrue(riderDockShouldExpand(272f, 92f, 450f, 30f, 600f))
    }

    @Test fun NoAvailableExpansionStaysCollapsedEvenOnUpwardFling() {
        assertFalse(riderDockShouldExpand(92f, 92f, 92f, -900f, 600f))
    }
}
