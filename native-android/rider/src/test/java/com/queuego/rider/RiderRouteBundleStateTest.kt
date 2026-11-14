package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiderRouteBundleStateTest {
    private fun job(id: String, status: String) = RiderJob(
        id = id,
        orderNumber = id,
        status = status,
        pickupAddress = "pickup-$id",
        pickupLat = 13.0,
        pickupLng = 100.0,
        deliveryAddress = "drop-$id",
        deliveryLat = 13.1,
        deliveryLng = 100.1,
        deliveryFee = 20.0,
        marketOrderId = null,
        arrivedShopAt = null,
        arrivedCustomerAt = null
    )

    private fun bundled(id: String, status: String, pickup: Int, drop: Int) = RiderBundleOrder(
        bundleId = "bundle",
        job = job(id, status),
        bundleSequence = pickup,
        dropRank = drop,
        shopId = "shop-$id",
        shopName = "Shop $id",
        originalDeliveryFee = 20.0,
        customerSavings = 5.0,
        riderExtraFee = 2.0,
        detourKm = 0.3,
        addedMinutes = 2,
        routeSequence = null
    )

    @Test fun pendingPickupWinsEvenWhenAnotherOrderIsAlreadyInProgress() {
        val state = RiderRouteBundleState(
            activeOrders = listOf(
                bundled("first", "in_progress", pickup = 1, drop = 1),
                bundled("second", "ready", pickup = 2, drop = 2)
            )
        )

        assertEquals("second", state.currentOrder()?.job?.id)
    }

    @Test fun pickupSequenceWinsAcrossMultiplePendingShops() {
        val state = RiderRouteBundleState(
            activeOrders = listOf(
                bundled("third", "ready", pickup = 3, drop = 1),
                bundled("second", "preparing", pickup = 2, drop = 3),
                bundled("first", "in_progress", pickup = 1, drop = 2)
            )
        )

        assertEquals("second", state.currentOrder()?.job?.id)
    }

    @Test fun afterAllPickupsDeliveryUsesDropRankAcrossPickedUpAndInProgress() {
        val state = RiderRouteBundleState(
            activeOrders = listOf(
                bundled("primary", "in_progress", pickup = 1, drop = 2),
                bundled("bundle", "picked_up", pickup = 2, drop = 1)
            )
        )

        assertEquals("bundle", state.currentOrder()?.job?.id)
    }

    @Test fun completedAndCancelledOrdersAreIgnored() {
        val state = RiderRouteBundleState(
            activeOrders = listOf(
                bundled("done", "completed", pickup = 1, drop = 1),
                bundled("cancelled", "cancelled", pickup = 2, drop = 2)
            )
        )

        assertNull(state.currentOrder())
    }
}
