package com.queuego.rider

import com.queuego.ridernavigationmap.NavigationGuidanceState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderUnexpectedStopTest {
    private val travelling = NavigationGuidanceState(
        weakGps = false,
        offRoute = false,
        arrived = false,
        distanceRemainingMeters = 2_500.0
    )

    @Test fun tenMinuteStationaryEpisodeCanPromptOnce() {
        val stoppedSince = 1_000L
        assertFalse(
            riderUnexpectedStopDue(
                true,
                travelling,
                0,
                stoppedSince,
                stoppedSince + 9 * 60_000L,
                null
            )
        )
        assertTrue(
            riderUnexpectedStopDue(
                true,
                travelling,
                0,
                stoppedSince,
                stoppedSince + 10 * 60_000L,
                null
            )
        )
        assertFalse(
            riderUnexpectedStopDue(
                true,
                travelling,
                0,
                stoppedSince,
                stoppedSince + 20 * 60_000L,
                stoppedSince
            )
        )
    }

    @Test fun movementOrUnsafeGuidanceSuppressesFalseAlarm() {
        assertTrue(riderUnexpectedStopResetRequired(travelling, 8))
        assertTrue(riderUnexpectedStopResetRequired(travelling.copy(weakGps = true), 0))
        assertTrue(riderUnexpectedStopResetRequired(travelling.copy(offRoute = true), 0))
        assertTrue(riderUnexpectedStopResetRequired(travelling.copy(arrived = true), 0))
        assertTrue(riderUnexpectedStopResetRequired(travelling.copy(distanceRemainingMeters = 100.0), 0))
        assertTrue(riderUnexpectedStopResetRequired(travelling, null))
    }
}
