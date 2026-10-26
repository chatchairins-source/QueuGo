package com.queuego.customer

import com.queuego.shared.QgMapRole
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CustomerRideMapPointsTest {
    private fun raw() = JSONObject().put("id", "ride").put("order_number", "QG-1234")
        .put("status", "searching_rider").put("fare", 100).put("duration_minutes", 5).put("distance_km", 2)
        .put("pickup_latitude", 15.1).put("pickup_longitude", 103.1)
        .put("delivery_latitude", 15.2).put("delivery_longitude", 103.2)

    @Test fun searchingRideShowsAuthoritativeEndpointsBeforeRiderExists() {
        val ride = parsePassengerRide(raw())
        val points = passengerRideMapPoints(ride)
        assertEquals(2, points.size)
        assertEquals(15.1, points[0].latitude, 0.0)
        assertEquals(103.2, points[1].longitude, 0.0)
        assertTrue(points.all { it.role == QgMapRole.CUSTOMER })
    }
    @Test fun assignedRideIncludesRealRiderWithoutLosingEndpoints() {
        val ride = parsePassengerRide(raw().put("status", "rider_assigned")
            .put("rider_latitude", 15.15).put("rider_longitude", 103.15))
        val points = passengerRideMapPoints(ride)
        assertEquals(3, points.size)
        assertEquals(QgMapRole.RIDER, points.last().role)
        assertNotEquals(ride, ride.copy(pickupLat = 15.11))
    }
    @Test fun invalidMissingAndPartialPairsNeverCreateMarkers() {
        val ride = parsePassengerRide(raw().put("pickup_latitude", JSONObject.NULL)
            .put("delivery_latitude", 0).put("rider_latitude", 15.1))
        assertTrue(passengerRideMapPoints(ride).isEmpty())
        assertTrue(passengerRideMapPoints(ride.copy(pickupLat = Double.NaN,
            pickupLng = 103.1, destinationLat = 22.0, destinationLng = 103.1)).isEmpty())
    }
    @Test fun allTerminalStatesKeepTrackingHidden() {
        val ride = parsePassengerRide(raw())
        for (status in listOf("completed", "cancelled", "no_rider_available"))
            assertTrue(passengerRideMapPoints(ride.copy(status = status)).isEmpty())
    }
}
