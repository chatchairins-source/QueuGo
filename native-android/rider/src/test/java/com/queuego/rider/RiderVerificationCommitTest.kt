package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderVerificationCommitTest {
    private fun job(status: String, id: String = "order-1") = RiderJob(
        id = id,
        orderNumber = "1234",
        status = status,
        pickupAddress = "shop",
        pickupLat = 14.0,
        pickupLng = 103.0,
        deliveryAddress = "customer",
        deliveryLat = 14.1,
        deliveryLng = 103.1,
        deliveryFee = 30.0,
        marketOrderId = null,
        arrivedShopAt = null,
        arrivedCustomerAt = null
    )

    @Test
    fun pickupMutationInProgressRejectsStalePickedUpRead() {
        assertEquals("in_progress", riderPickupCommittedStatus("in_progress"))
        assertFalse(riderPickupReadCanReplace("in_progress", "order-1", RiderSnapshot(false, job("picked_up"), null)))
        assertTrue(riderPickupReadCanReplace("in_progress", "order-1", RiderSnapshot(false, job("in_progress"), null)))
        assertTrue(riderPickupReadCanReplace("in_progress", "order-1", RiderSnapshot(false, job("completed"), null)))
    }

    @Test
    fun pickedUpMutationMayAdvanceButNeverReturnToReady() {
        assertEquals("picked_up", riderPickupCommittedStatus("picked_up"))
        assertFalse(riderPickupReadCanReplace("picked_up", "order-1", RiderSnapshot(false, job("ready"), null)))
        assertTrue(riderPickupReadCanReplace("picked_up", "order-1", RiderSnapshot(false, job("in_progress"), null)))
    }

    @Test
    fun refreshMergeNeverRegressesSameActiveJobOrDoneMarketPickup() {
        val ready = job("ready")
        val inProgress = job("in_progress").copy(
            arrivedShopAt = "local:1",
            customerSubtotal = 100.0,
            customerDeliveryFee = 20.0
        )
        val donePickup = MarketPickup("pickup-1", "PICKED_UP", "ร้าน", "ตลาด", 100.0, 14.0, 103.0)
        val stalePickup = donePickup.copy(status = "READY")

        val merged = riderMergeLiveSnapshot(
            RiderSnapshot(false, inProgress, null, listOf(donePickup)),
            RiderSnapshot(false, ready, null, listOf(stalePickup))
        )

        assertEquals("in_progress", merged.activeJob?.status)
        assertTrue(merged.activeJob?.shopArrived == true)
        assertEquals(100.0, merged.activeJob?.customerSubtotal)
        assertEquals(20.0, merged.activeJob?.customerDeliveryFee)
        assertTrue(merged.marketPickups.single().done)
    }

    @Test
    fun refreshMergeAllowsForwardProgressAndDifferentJob() {
        val forward = riderMergeLiveSnapshot(
            RiderSnapshot(false, job("ready"), null),
            RiderSnapshot(false, job("in_progress"), null)
        )
        assertEquals("in_progress", forward.activeJob?.status)

        val next = riderMergeLiveSnapshot(
            RiderSnapshot(false, job("in_progress"), null),
            RiderSnapshot(false, job("rider_assigned", "order-2"), null)
        )
        assertEquals("order-2", next.activeJob?.id)
    }

    @Test
    fun staleEmptyRefreshSeparatesLookupFailureFromAuthoritativeAbsence() {
        val claimed = job("rider_assigned")
        val current = RiderSnapshot(true, claimed, null)
        val emptyIncoming = RiderSnapshot(true, null, null)

        val lookupFailed = riderResolveMissingActiveSnapshot(current, emptyIncoming, null, false)
        assertEquals("order-1", lookupFailed.activeJob?.id)
        assertEquals("rider_assigned", lookupFailed.activeJob?.status)

        val authoritativelyGone = riderResolveMissingActiveSnapshot(current, emptyIncoming, null, true)
        assertEquals(null, authoritativelyGone.activeJob)

        val confirmed = riderResolveMissingActiveSnapshot(current, emptyIncoming, job("in_progress"), true)
        assertEquals("in_progress", confirmed.activeJob?.status)

        val cancelled = riderResolveMissingActiveSnapshot(current, emptyIncoming, job("cancelled"), true)
        assertEquals(null, cancelled.activeJob)
    }

    @Test
    fun failedEmptyRefreshKeepsCompletedMarketPickupProgress() {
        val marketJob = job("picked_up").copy(marketOrderId = "market-1")
        val donePickup = MarketPickup("pickup-1", "PICKED_UP", "ร้าน", "ตลาด", 100.0, 14.0, 103.0)
        val current = RiderSnapshot(true, marketJob, null, listOf(donePickup))
        val emptyIncoming = RiderSnapshot(true, null, null)

        val merged = riderResolveMissingActiveSnapshot(current, emptyIncoming, null, false)
        assertEquals("order-1", merged.activeJob?.id)
        assertTrue(merged.marketPickups.single().done)
    }

    @Test
    fun arrivalReadRequiresCommittedArrivalMarker() {
        assertFalse(riderArrivalReadCanReplace("shop", "order-1", RiderSnapshot(false, job("ready"), null)))
        assertTrue(
            riderArrivalReadCanReplace(
                "shop",
                "order-1",
                RiderSnapshot(false, job("ready").copy(arrivedShopAt = "2026-10-13T04:00:00Z"), null)
            )
        )
        assertFalse(
            riderArrivalReadCanReplace(
                "customer",
                "order-1",
                RiderSnapshot(false, job("in_progress").copy(arrivedShopAt = "2026-10-13T04:00:00Z"), null)
            )
        )
        assertTrue(
            riderArrivalReadCanReplace(
                "customer",
                "order-1",
                RiderSnapshot(false, job("in_progress").copy(arrivedCustomerAt = "2026-10-13T04:01:00Z"), null)
            )
        )
    }

    @Test
    fun pickupRejectsStaleReadySnapshot() {
        assertFalse(
            riderVerificationCommitVisible(
                "pickup",
                "order-1",
                null,
                RiderSnapshot(false, job("ready"), null)
            )
        )
        assertTrue(
            riderVerificationCommitVisible(
                "pickup",
                "order-1",
                null,
                RiderSnapshot(false, job("picked_up"), null)
            )
        )
        assertTrue(
            riderVerificationCommitVisible(
                "pickup",
                "order-1",
                null,
                RiderSnapshot(false, job("in_progress"), null)
            )
        )
    }

    @Test
    fun deliveryRejectsStaleInProgressSnapshot() {
        assertFalse(
            riderVerificationCommitVisible(
                "delivery",
                "order-1",
                null,
                RiderSnapshot(false, job("in_progress"), null)
            )
        )
        assertTrue(
            riderVerificationCommitVisible(
                "delivery",
                "order-1",
                null,
                RiderSnapshot(false, null, null)
            )
        )
        assertTrue(
            riderVerificationCommitVisible(
                "delivery",
                "order-1",
                null,
                RiderSnapshot(false, job("rider_assigned", "order-2"), null)
            )
        )
    }

    @Test
    fun marketPickupRequiresCommittedPickupOrAdvancedOrder() {
        val stalePickup = MarketPickup("pickup-1", "READY", "ร้าน", null, 100.0, 14.0, 103.0)
        val donePickup = stalePickup.copy(status = "PICKED_UP")

        assertFalse(
            riderVerificationCommitVisible(
                "marketPickup",
                "order-1",
                "pickup-1",
                RiderSnapshot(false, job("ready"), null, listOf(stalePickup))
            )
        )
        assertTrue(
            riderVerificationCommitVisible(
                "marketPickup",
                "order-1",
                "pickup-1",
                RiderSnapshot(false, job("ready"), null, listOf(donePickup))
            )
        )
        assertTrue(
            riderVerificationCommitVisible(
                "marketPickup",
                "order-1",
                "pickup-1",
                RiderSnapshot(false, job("picked_up"), null, emptyList())
            )
        )
    }
}
