package com.queuego.rider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderNetworkGuardTest {
    @Test fun serverMutationRequiresValidatedNetwork() {
        assertTrue(riderServerMutationAllowed(networkAvailable = true))
        assertFalse(riderServerMutationAllowed(networkAvailable = false))
    }

    @Test fun guardMessagePreservesTrustAndExplainsRecovery() {
        assertTrue(RIDER_NETWORK_GUARD_MESSAGE.contains("งานยังอยู่ในเครื่อง"))
        assertTrue(RIDER_NETWORK_GUARD_MESSAGE.contains("อินเทอร์เน็ต"))
        assertTrue(RIDER_NETWORK_GUARD_MESSAGE.contains("อัปเดตสถานะ"))
    }
}
