package com.queuego.rider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderAcceptanceModeTest {
    @Test fun autoAcceptIsOptInAndNeedsLiveTargetedOfferWindow() {
        assertFalse(
            riderAutoAcceptEligible(
                enabled = false,
                networkAvailable = true,
                online = true,
                hasActiveWork = false,
                actionBusy = false,
                secondsLeft = 30
            )
        )
        assertTrue(
            riderAutoAcceptEligible(
                enabled = true,
                networkAvailable = true,
                online = true,
                hasActiveWork = false,
                actionBusy = false,
                secondsLeft = 30
            )
        )
    }

    @Test fun autoAcceptFailsClosedForUnsafeOrStaleStates() {
        assertFalse(riderAutoAcceptEligible(true, false, true, false, false, 30))
        assertFalse(riderAutoAcceptEligible(true, true, false, false, false, 30))
        assertFalse(riderAutoAcceptEligible(true, true, true, true, false, 30))
        assertFalse(riderAutoAcceptEligible(true, true, true, false, true, 30))
        assertFalse(
            riderAutoAcceptEligible(
                true,
                true,
                true,
                false,
                false,
                RIDER_AUTO_ACCEPT_MIN_SECONDS_LEFT - 1
            )
        )
    }

    @Test fun acceptanceWindowKeepsRaceBufferBeforeExpiry() {
        assertTrue(RIDER_AUTO_ACCEPT_MIN_SECONDS_LEFT >= 2)
        assertTrue(
            riderAutoAcceptEligible(
                true,
                true,
                true,
                false,
                false,
                RIDER_AUTO_ACCEPT_MIN_SECONDS_LEFT
            )
        )
    }
}
