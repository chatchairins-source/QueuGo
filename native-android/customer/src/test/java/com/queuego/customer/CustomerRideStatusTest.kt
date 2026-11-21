package com.queuego.customer

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CustomerRideStatusTest {
    private fun ride(status: String, pickupAt: String? = null, dropoffAt: String? = null): PassengerRide {
        val raw = JSONObject().put("id", "ride").put("order_number", "QG-1234")
            .put("status", status).put("fare", 100).put("duration_minutes", 5).put("distance_km", 2)
            .put("arrived_pickup_at", pickupAt ?: JSONObject.NULL)
            .put("arrived_dropoff_at", dropoffAt ?: JSONObject.NULL)
        return parsePassengerRide(raw)
    }

    @Test fun arrivalSnapshotsAdvanceCustomerLabelsWithinTheirExistingServerState() {
        val pickup = ride("rider_assigned", pickupAt = "2026-10-26T11:00:00Z")
        assertNotEquals(ride("rider_assigned"), pickup)
        assertEquals("2026-10-26T11:00:00Z", pickup.arrivedPickupAt)
        assertEquals("คนขับถึงจุดรับแล้ว", passengerRideStatus(pickup))
        assertEquals("ถึงจุดหมายแล้ว", passengerRideStatus(
            ride("in_progress", dropoffAt = "2026-10-26T11:10:00Z")))
        assertEquals("คนขับกำลังไปจุดรับ", passengerRideStatus(ride("rider_assigned")))
        assertEquals("กำลังเดินทาง", passengerRideStatus(ride("in_progress")))
    }

    @Test fun staleArrivalFieldsCannotOverrideTerminalOrDifferentStates() {
        assertEquals("จบการเดินทางแล้ว", passengerRideStatus(ride("completed", "old", "old")))
        assertEquals("ยกเลิกแล้ว", passengerRideStatus(ride("cancelled", "old", "old")))
        assertEquals("กำลังเดินทาง", passengerRideStatus(ride("in_progress", pickupAt = "old")))
        assertEquals("คนขับกำลังไปจุดรับ", passengerRideStatus(ride("rider_assigned", pickupAt = " ")))
        assertEquals("future_state", passengerRideStatus(ride("future_state", "old", "old")))
    }
}
