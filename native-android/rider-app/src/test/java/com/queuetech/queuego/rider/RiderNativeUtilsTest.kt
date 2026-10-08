package com.queuetech.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RiderNativeUtilsTest {
    @Test
    fun supabaseTimestampWithMicrosecondsParses() {
        val parsed = RiderServerTime.parseMillis("2026-10-08T08:30:12.123456+00:00")
        assertNotNull(parsed)
    }

    @Test
    fun marketPickupDoneTreatsPickedUpAndCancelledAsDone() {
        val picked = RiderMarketPickup(
            pickupId = "pickup-1",
            sequence = 1,
            shopName = "ร้านหนึ่ง",
            shopAddress = "",
            latitude = null,
            longitude = null,
            shopAmount = 120.0,
            cashPaidAmount = 120.0,
            status = "PICKED_UP",
        )
        val cancelled = picked.copy(pickupId = "pickup-2", status = "CANCELLED")
        val ready = picked.copy(pickupId = "pickup-3", status = "READY")
        assertEquals(true, picked.done)
        assertEquals(true, cancelled.done)
        assertEquals(false, ready.done)
    }

    @Test
    fun activeJobPickupPhaseStopsAfterReady() {
        val job = RiderActiveJob(
            orderId = "order",
            orderNumber = "QT-1234",
            status = "ready",
            shopName = "ร้าน",
            pickupAddress = "",
            pickupLatitude = null,
            pickupLongitude = null,
            deliveryAddress = "",
            deliveryLatitude = null,
            deliveryLongitude = null,
            totalAmount = 200.0,
            deliveryFee = 30.0,
            subtotal = 170.0,
            customerName = "ลูกค้า",
            note = "",
            riderArrivedShopAt = null,
            riderArrivedCustomerAt = null,
            marketOrderId = null,
            fulfillmentVertical = "",
        )
        assertEquals(true, job.isPickupPhase)
        assertEquals(false, job.copy(status = "in_progress").isPickupPhase)
    }

    @Test
    fun riderActionIdIsStableForRetry() {
        val first = RiderActionIds.stable(
            "11111111-1111-1111-1111-111111111111",
            "claim",
            "22222222-2222-2222-2222-222222222222",
        )
        val second = RiderActionIds.stable(
            "11111111-1111-1111-1111-111111111111",
            "claim",
            "22222222-2222-2222-2222-222222222222",
        )
        assertEquals(first, second)
    }
}
