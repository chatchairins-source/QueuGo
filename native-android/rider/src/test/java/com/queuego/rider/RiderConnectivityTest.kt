package com.queuego.rider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderConnectivityTest {
    @Test fun offlineContinuityOnlyAppearsDuringActiveWork() {
        assertTrue(riderOfflineContinuityActive(activeWork = true, networkAvailable = false))
        assertFalse(riderOfflineContinuityActive(activeWork = false, networkAvailable = false))
        assertFalse(riderOfflineContinuityActive(activeWork = true, networkAvailable = true))
    }

    @Test fun serverMutationsRequireValidatedNetwork() {
        assertTrue(riderWorkMutationAllowed(networkAvailable = true))
        assertFalse(riderWorkMutationAllowed(networkAvailable = false))
    }

    @Test fun offlineMessageMakesStateAndRecoveryClear() {
        assertTrue(RIDER_OFFLINE_ACTION_MESSAGE.contains("งานยังอยู่"))
        assertTrue(RIDER_OFFLINE_ACTION_MESSAGE.contains("อินเทอร์เน็ต"))
    }
}
