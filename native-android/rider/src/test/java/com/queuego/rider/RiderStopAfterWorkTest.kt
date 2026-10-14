package com.queuego.rider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderStopAfterWorkTest {
    @Test fun stopAfterWorkRunsOnlyWhenFullyIdleAndConnected() {
        assertTrue(
            riderStopAfterWorkReady(
                pending = true,
                networkAvailable = true,
                online = true,
                hasActiveJob = false,
                hasLaundryJob = false,
                hasDeliveryReturn = false,
                hasOffer = false,
                actionBusy = false
            )
        )
        assertFalse(riderStopAfterWorkReady(true, false, true, false, false, false, false, false))
        assertFalse(riderStopAfterWorkReady(true, true, true, true, false, false, false, false))
        assertFalse(riderStopAfterWorkReady(true, true, true, false, true, false, false, false))
        assertFalse(riderStopAfterWorkReady(true, true, true, false, false, true, false, false))
        assertFalse(riderStopAfterWorkReady(true, true, true, false, false, false, true, false))
    }

    @Test fun pendingRestIntentSuppressesAutoAcceptWithoutChangingPreference() {
        assertTrue(riderAutoAcceptAllowedWhileStopping(true, false))
        assertFalse(riderAutoAcceptAllowedWhileStopping(true, true))
        assertFalse(riderAutoAcceptAllowedWhileStopping(false, false))
    }

    @Test fun stopAfterWorkFailsClosedWhenAlreadyOfflineOrBusy() {
        assertFalse(riderStopAfterWorkReady(true, true, false, false, false, false, false, false))
        assertFalse(riderStopAfterWorkReady(true, true, true, false, false, false, false, true))
        assertFalse(riderStopAfterWorkReady(false, true, true, false, false, false, false, false))
    }
}
