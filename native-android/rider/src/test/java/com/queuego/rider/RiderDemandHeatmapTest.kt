package com.queuego.rider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RiderDemandHeatmapTest {
    @Test fun parsesOnlyPrivacySafeValidDemandCells() {
        val zones = riderDemandZonesFromRpc(
            JSONArray()
                .put(JSONObject().put("latitude", 15.02).put("longitude", 103.10).put("level", 1).put("window_hours", 24))
                .put(JSONObject().put("latitude", 15.02).put("longitude", 103.10).put("level", 3).put("window_hours", 24))
                .put(JSONObject().put("latitude", 99.0).put("longitude", 103.12).put("level", 2).put("window_hours", 24))
                .put(JSONObject().put("latitude", 15.04).put("longitude", 103.12).put("level", 7).put("window_hours", 24))
        )
        assertEquals(1, zones.size)
        assertEquals(15.02, zones.single().latitude, 0.000001)
        assertEquals(103.10, zones.single().longitude, 0.000001)
        assertEquals(1, zones.single().level)
        assertEquals(24, zones.single().windowHours)
    }

    @Test fun emptyBackendArrayDoesNotInventDemand() {
        assertEquals(emptyList<RiderDemandZone>(), riderDemandZonesFromRpc(JSONArray()))
    }
}
