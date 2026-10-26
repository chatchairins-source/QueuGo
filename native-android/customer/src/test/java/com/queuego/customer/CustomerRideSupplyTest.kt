package com.queuego.customer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CustomerRideSupplyTest {
    @Test fun foregroundOrPickerRestartsRespectTheSameCoordinateThrottle() {
        assertEquals(0L, passengerRideSupplyWaitMillis(0L, 100L))
        assertEquals(15_000L, passengerRideSupplyWaitMillis(100L, 100L))
        assertEquals(14_000L, passengerRideSupplyWaitMillis(100L, 1_100L))
        assertEquals(0L, passengerRideSupplyWaitMillis(100L, 15_100L))
        assertEquals(0L, passengerRideSupplyWaitMillis(100L, 30_000L))
        assertEquals(15_000L, passengerRideSupplyWaitMillis(100L, 50L))
    }
    private fun cluster(count: Int = 2, lat: Double = 15.1, lng: Double = 103.1,
        approximate: Boolean = true) = JSONObject().put("count", count)
        .put("latitude", lat).put("longitude", lng).put("approximate", approximate)
    @Test fun nearbyCountDoesNotRequireASingleRiderMarker() {
        val supply = parsePassengerRideSupply(JSONObject().put("nearby_count", 1)
            .put("clusters", JSONArray()))
        assertEquals(1, supply.nearbyCount)
        assertTrue(supply.clusters.isEmpty())
    }
    @Test fun onlyValidApproximateMultiRiderCellsBecomeMarkers() {
        val rows = JSONArray().put(cluster()).put(cluster(count = 1))
            .put(cluster(approximate = false)).put(cluster(lat = 0.0))
            .put(JSONObject().put("count", 3).put("approximate", true))
        val supply = parsePassengerRideSupply(JSONObject().put("nearby_count", 7).put("clusters", rows))
        assertEquals(1, supply.clusters.size)
        assertEquals(2, supply.clusters.single().count)
        assertEquals(15.1, supply.clusters.single().point.latitude, 0.0)
    }
    @Test fun malformedOrMissingSupplySafelyRendersEmpty() {
        assertEquals(PassengerRideSupply(), parsePassengerRideSupply(JSONObject()))
        assertEquals(0, parsePassengerRideSupply(JSONObject().put("nearby_count", -1)).nearbyCount)
        assertTrue(parsePassengerRideSupply(JSONObject().put("clusters", JSONArray()
            .put(JSONObject.NULL).put("invalid"))).clusters.isEmpty())
    }
    @Test fun clusterCountCannotExceedServerDisplayBound() {
        val rows = JSONArray()
        repeat(20) { rows.put(cluster()) }
        assertEquals(12, parsePassengerRideSupply(JSONObject().put("clusters", rows)).clusters.size)
    }
}
