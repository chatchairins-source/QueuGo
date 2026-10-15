package com.queuego.rider
import org.junit.Assert.*
import org.junit.Test

class RiderPassengerTest {
    private fun ride(status: String = "rider_assigned", pickup: String? = null, drop: String? = null) = RiderJob(
        id="ride",orderNumber="QT-0001",status=status,pickupAddress="จุดรับ",pickupLat=15.0,pickupLng=103.0,
        deliveryAddress="จุดหมาย",deliveryLat=15.02,deliveryLng=103.0,deliveryFee=50.0,marketOrderId=null,
        arrivedShopAt=pickup,arrivedCustomerAt=drop,customerCash=50.0,fulfillmentVertical="ride")
    @Test fun onlyTheNextPassengerStageCanBeSelected() {
        assertEquals("arrive_pickup", passengerStageAction(ride())?.first)
        assertEquals("start", passengerStageAction(ride(pickup="arrived"))?.first)
        assertEquals("arrive_dropoff", passengerStageAction(ride("in_progress",pickup="arrived"))?.first)
        assertEquals("complete", passengerStageAction(ride("in_progress",pickup="arrived",drop="arrived"))?.first)
        assertNull(passengerStageAction(ride("completed")))
        assertNull(passengerStageAction(ride("cancelled")))
        assertNull(passengerStageAction(ride().copy(fulfillmentVertical="food")))
    }
    @Test fun googleMapsTargetsPickupThenDestinationWithoutChangingCash() {
        val before = ride()
        val after = before.copy(status="in_progress")
        assertEquals(15.0 to 103.0, before.navigationTarget)
        assertEquals(15.02 to 103.0, after.navigationTarget)
        assertEquals(before.customerCash,after.customerCash)
        assertEquals(before.deliveryFee,after.deliveryFee)
    }
}
