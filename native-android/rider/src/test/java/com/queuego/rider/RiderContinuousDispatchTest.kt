package com.queuego.rider

import org.junit.Assert.*
import org.junit.Test

class RiderContinuousDispatchTest {
    private fun job(id: String, status: String = "in_progress", goods: Double = 100.0) = RiderJob(
        id, id, status, "shop-$id", 15.0, 103.0, "customer-$id", 15.01, 103.0,
        25.0, null, arrivedShopAt = null, arrivedCustomerAt = null,
        shopCash = goods, customerCash = goods + 25.0, customerSubtotal = goods, customerDeliveryFee = 25.0
    )
    @Test fun queuedJobCannotReplaceCurrentWorkBeforeAnAcknowledgedCompletion() {
        val current = job("A")
        val queued = job("B", "preparing", 250.0)
        val snapshot = RiderSnapshot(true, current, null, nextJob = queued)
        assertEquals("A", snapshot.activeJob?.id)
        assertEquals(125.0, snapshot.activeJob?.customerCash)
        assertEquals("B", snapshot.nextJob?.id)
        assertEquals(275.0, snapshot.nextJob?.customerCash)
        assertSame(snapshot, riderAfterDeliveryCommit(snapshot, "unrelated-QT"))
    }
    @Test fun matchingCommitPromotesExactlyOneQueuedOrderAndPreservesItsCash() {
        val snapshot = RiderSnapshot(true, job("A"), null, nextJob = job("B", "ready", 250.0))
        val committed = riderAfterDeliveryCommit(snapshot, "A")
        assertEquals("B", committed.activeJob?.id)
        assertEquals("ready", committed.activeJob?.status)
        assertEquals(250.0, committed.activeJob?.shopCash)
        assertEquals(275.0, committed.activeJob?.customerCash)
        assertNull(committed.nextJob)
        assertSame(committed, riderAfterDeliveryCommit(committed, "A"))
    }
    @Test fun cancelledOrCompletedQueuedOrderIsNeverPromoted() {
        for (status in listOf("cancelled", "completed", "no_rider_available")) {
            val committed = riderAfterDeliveryCommit(RiderSnapshot(true, job("A"), null, nextJob = job("B", status)), "A")
            assertNull(committed.activeJob)
            assertNull(committed.nextJob)
        }
    }
    @Test fun ordinarySingleJobCompletionStillBecomesIdle() {
        val committed = riderAfterDeliveryCommit(RiderSnapshot(true, job("A"), null), "A")
        assertNull(committed.activeJob)
        assertTrue(committed.online)
    }
}
