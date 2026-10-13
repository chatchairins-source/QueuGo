package com.queuego.rider

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
