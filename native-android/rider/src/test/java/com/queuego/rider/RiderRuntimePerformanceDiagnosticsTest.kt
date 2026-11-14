package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderRuntimePerformanceDiagnosticsTest {
    @Test fun networkStatsAggregateOnlyOutcomeAndTiming() {
        var snapshot = RiderNetworkRuntimeSnapshot()
        snapshot = riderNetworkSnapshotAfter(snapshot, 200L, success = true)
        snapshot = riderNetworkSnapshotAfter(snapshot, 1_700L, success = true)
        snapshot = riderNetworkSnapshotAfter(snapshot, 500L, success = false)

        assertEquals(3L, snapshot.totalRequests)
        assertEquals(1L, snapshot.failedRequests)
        assertEquals(1L, snapshot.slowRequests)
        assertEquals(800L, snapshot.averageDurationMs)
        assertEquals(1_700L, snapshot.worstDurationMs)
        assertFalse(riderNetworkRuntimeHealth(snapshot).healthy == true)
    }

    @Test fun healthyNetworkAndSmoothFramesStayGreen() {
        var network = RiderNetworkRuntimeSnapshot()
        repeat(10) {
            network = riderNetworkSnapshotAfter(network, 220L, success = true)
        }
        assertTrue(riderNetworkRuntimeHealth(network).healthy == true)

        var frames = RiderUiJankSnapshot()
        repeat(60) {
            frames = riderUiJankSnapshotAfter(frames, 16L)
        }
        assertTrue(riderUiJankRuntimeHealth(frames).healthy == true)
    }

    @Test fun severeFrameFlagsJankAndBackgroundGapIsIgnored() {
        var frames = RiderUiJankSnapshot()
        repeat(60) {
            frames = riderUiJankSnapshotAfter(frames, 16L)
        }
        frames = riderUiJankSnapshotAfter(frames, 120L)

        assertFalse(riderUiJankRuntimeHealth(frames).healthy == true)
        assertEquals(1L, frames.severeFrames)
        assertTrue(riderShouldRecordFrameDuration(120L))
        assertFalse(riderShouldRecordFrameDuration(700L))
    }

    @Test fun mainThreadWatchdogSeparatesSlowPingFromRealStall() {
        var snapshot = RiderMainThreadWatchdogSnapshot()
        snapshot = riderMainThreadWatchdogAfter(snapshot, 40L)
        snapshot = riderMainThreadWatchdogAfter(snapshot, 700L)
        snapshot = riderMainThreadWatchdogAfter(snapshot, 5_500L)

        assertEquals(3L, snapshot.pingCount)
        assertEquals(2L, snapshot.slowPingCount)
        assertEquals(1L, snapshot.stallCount)
        assertEquals(5_500L, snapshot.worstDelayMs)
        assertFalse(riderMainThreadWatchdogHealth(snapshot).healthy == true)
    }

    @Test fun responsiveMainThreadStaysHealthyAfterEnoughPings() {
        var snapshot = RiderMainThreadWatchdogSnapshot()
        repeat(5) {
            snapshot = riderMainThreadWatchdogAfter(snapshot, 30L)
        }
        assertTrue(riderMainThreadWatchdogHealth(snapshot).healthy == true)
    }

    @Test fun emptyRuntimeStatsRemainInformationalNotFailure() {
        assertNull(riderNetworkRuntimeHealth(RiderNetworkRuntimeSnapshot()).healthy)
        assertNull(riderUiJankRuntimeHealth(RiderUiJankSnapshot()).healthy)
        assertNull(riderMainThreadWatchdogHealth(RiderMainThreadWatchdogSnapshot()).healthy)
    }
}
