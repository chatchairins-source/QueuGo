package com.queuego.rider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderRoadAlertsTest {
    @Test fun parserKeepsOnlyPublicVerifiedRoadAlertCategories() {
        val rows = JSONArray()
            .put(
                JSONObject()
                    .put("alert_id", "alert-1")
                    .put("category", "road_closed")
                    .put("latitude", 14.995)
                    .put("longitude", 103.102)
                    .put("distance_km", 0.35)
                    .put("note", "ปิดถนนชั่วคราว")
            )
            .put(
                JSONObject()
                    .put("alert_id", "alert-2")
                    .put("category", "private_note")
                    .put("latitude", 14.996)
                    .put("longitude", 103.103)
                    .put("distance_km", 0.2)
            )

        val parsed = riderRoadAlertsFromRpc(rows)
        assertEquals(1, parsed.size)
        assertEquals("road_closed", parsed.single().category)
    }

    @Test fun parserRejectsInvalidThailandCoordinatesAndDistance() {
        val rows = JSONArray()
            .put(
                JSONObject()
                    .put("alert_id", "outside")
                    .put("category", "bad_road")
                    .put("latitude", 40.0)
                    .put("longitude", 120.0)
                    .put("distance_km", 1.0)
            )
            .put(
                JSONObject()
                    .put("alert_id", "negative")
                    .put("category", "unsafe_turn")
                    .put("latitude", 15.0)
                    .put("longitude", 104.0)
                    .put("distance_km", -1.0)
            )

        assertTrue(riderRoadAlertsFromRpc(rows).isEmpty())
    }

    @Test fun nearbyAlertsSortByRealDistance() {
        val rows = JSONArray()
            .put(
                JSONObject()
                    .put("alert_id", "far")
                    .put("category", "bad_road")
                    .put("latitude", 15.0)
                    .put("longitude", 104.0)
                    .put("distance_km", 4.2)
            )
            .put(
                JSONObject()
                    .put("alert_id", "near")
                    .put("category", "unsafe_turn")
                    .put("latitude", 15.0)
                    .put("longitude", 104.0)
                    .put("distance_km", 0.8)
            )

        assertEquals(listOf("near", "far"), riderRoadAlertsFromRpc(rows).map { it.id })
    }

    @Test fun distanceLabelsStayDrivingFriendly() {
        assertEquals("350 ม.", riderRoadAlertDistanceLabel(0.35))
        assertEquals("1.2 กม.", riderRoadAlertDistanceLabel(1.24))
        assertEquals("—", riderRoadAlertDistanceLabel(-1.0))
    }
}
