package com.queuego.merchant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantDeliveryCancellationTest {
    @Test fun riderAcceptanceDoesNotStartPreparation() {
        listOf("pending", "accepted", "searching_rider", "rider_assigned", "assigned").forEach {
            assertTrue(it, merchantDeliveryCanCancel(it, null, null))
        }
    }
    @Test fun startedPreparationAndTerminalStatesCannotBeCancelled() {
        listOf("preparing", "ready", "picked_up", "in_progress", "completed", "cancelled", "no_rider_available").forEach {
            assertFalse(it, merchantDeliveryCanCancel(it, null, null))
        }
    }
    @Test fun serverTimestampsOverrideAStaleAssignedStatus() {
        assertFalse(merchantDeliveryCanCancel("rider_assigned", "2026-10-22T01:00:00Z", null))
        assertFalse(merchantDeliveryCanCancel("assigned", null, "2026-10-22T01:00:00Z"))
    }
}
