package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiderLaundryModelTest {
    private fun job(
        status: String,
        fromLat: Double? = 14.99,
        fromLng: Double? = 103.10,
        toLat: Double? = 15.00,
        toLng: Double? = 103.11
    ) = RiderLaundryJob(
        jobId = "job-1",
        laundryOrderId = "laundry-1",
        orderNumber = "1234",
        leg = "pickup",
        jobStatus = status,
        orderStatus = "pickup_assigned",
        hubId = "hub-1",
        hubName = "ศูนย์ฝากซัก",
        shopName = "ร้านซัก",
        serviceName = "ซักอบ",
        actualQuantity = null,
        pricingType = "kg",
        jobFee = 25.0,
        fromAddress = "ลูกค้า",
        fromLatitude = fromLat,
        fromLongitude = fromLng,
        toAddress = "ร้าน",
        toLatitude = toLat,
        toLongitude = toLng,
        customerAmount = 120.0
    )

    @Test
    fun assignedLaundryNavigatesToPickup() {
        assertEquals(14.99 to 103.10, job("assigned").navigationTarget)
    }

    @Test
    fun collectedLaundryNavigatesToDestination() {
        assertEquals(15.00 to 103.11, job("collected").navigationTarget)
    }

    @Test
    fun missingCurrentCoordinatesDoNotInventNavigation() {
        assertNull(job("assigned", fromLat = null, fromLng = null).navigationTarget)
    }

    @Test
    fun laundryUsesCanonicalVisibleOrderPrefix() {
        assertEquals("QT-1234", job("assigned").numberLabel)
    }
}
