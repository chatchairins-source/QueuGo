package com.queuego.rider

import org.junit.Assert.*
import org.junit.Test

class RiderContinuousDispatchTest {
    @Test fun backgroundLeaseRequiresFreshNavigationAndExplicitSafeSession() {
        fun eligible(ready: Boolean = true, auto: Boolean = true, stop: Boolean = false,
                     job: Boolean = true, token: Long = 120_000L, age: Long? = 1_000L) =
            riderNavigationDispatchLeaseEligible(ready, auto, stop, job, token, age)
        assertTrue(eligible())
        assertTrue(eligible(age = 0))
        assertTrue(eligible(age = 15_000))
        assertFalse(eligible(ready = false)) // Busy/offline/conflicting service.
        assertFalse(eligible(auto = false))
        assertFalse(eligible(stop = true))
        assertFalse(eligible(job = false)) // No resurrection after process death/logout/completion.
        assertFalse(eligible(token = 60_000))
        assertFalse(eligible(token = 0))
        assertFalse(eligible(age = null))
        assertFalse(eligible(age = -1))
        assertFalse(eligible(age = 15_001))
    }
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
    private fun bundled(id: String, sequence: Int, pickup: Int, drop: Int, status: String) = RiderBundleOrder(
        bundleId = "bundle", job = job(id, status), bundleSequence = sequence, dropRank = sequence,
        shopId = id, shopName = "shop-$id", originalDeliveryFee = 25.0, customerSavings = 2.0,
        riderExtraFee = 5.0, detourKm = 0.5, addedMinutes = 2, routeSequence = null,
        pickupStopRank = pickup, dropoffStopRank = drop
    )
    @Test fun interleavedServerPlanCanDeliverBeforeTheLastPickup() {
        val a = bundled("A", 1, 1, 3, "picked_up")
        val b = bundled("B", 2, 2, 5, "ready")
        val c = bundled("C", 3, 4, 6, "ready")
        val waitingB = RiderRouteBundleState(listOf(a, b, c))
        assertEquals("B", waitingB.currentOrder()?.job?.id)
        val afterB = waitingB.copy(activeOrders = listOf(a, b.copy(job = b.job.copy(status = "picked_up")), c))
        assertEquals("A", afterB.currentOrder()?.job?.id)
        assertEquals(listOf(3, 4, 5, 6), afterB.routeStops().map { it.rank })
        val afterA = riderAfterDeliveryCommit(RiderSnapshot(true, a.job, null, bundle = afterB), "A")
        assertEquals("C", afterA.activeJob?.id)
        assertEquals(125.0, afterA.activeJob?.customerCash)
    }
    @Test fun cancellationSkipsOnlyThatOrdersStops() {
        val a = bundled("A", 1, 1, 3, "picked_up")
        val b = bundled("B", 2, 2, 5, "cancelled")
        val c = bundled("C", 3, 4, 6, "ready")
        val stack = RiderRouteBundleState(listOf(a, b, c))
        assertEquals("A", stack.currentOrder()?.job?.id)
        assertEquals(listOf(3, 4, 6), stack.routeStops().map { it.rank })
        assertEquals(listOf("A", "C", "C"), stack.routeStops().map { it.order.job.id })
    }
    @Test fun fiveOrdersRetainTenSeparateStopsAndQtIdentifiers() {
        val orders = (1..5).map { n -> bundled("QT-$n", n, n, n + 5, "ready") }
        val stack = RiderRouteBundleState(orders)
        assertEquals(10, stack.routeStops().size)
        assertEquals((1..10).toList(), stack.routeStops().map { it.rank })
        assertEquals(5, stack.routeStops().map { it.order.job.id }.distinct().size)
        assertTrue(stack.routeStops().take(5).all { it.pickup })
    }

}
