package com.queuego.shared

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueGoPlacesReverseTest {
    @Test fun formatsNearestAdministrativeAddressWithoutDuplicates() {
        val row = JSONObject()
            .put("canonical_name", "บวรนิเวศ")
            .put("subdistrict", "บวรนิเวศ")
            .put("district", "พระนคร")
            .put("province", "กรุงเทพมหานคร")
            .put("postcode", "10200")

        assertEquals(
            "บวรนิเวศ พระนคร กรุงเทพมหานคร 10200",
            queueGoPlaceAddress(row)
        )
    }

    @Test fun prefersNearestSubdistrictFromQueueGoSearchResults() {
        val rows = JSONArray()
            .put(JSONObject().put("subcategory", "province").put("canonical_name", "กรุงเทพมหานคร").put("distance_m", 300.0))
            .put(JSONObject().put("subcategory", "subdistrict").put("canonical_name", "บวรนิเวศ").put("subdistrict", "บวรนิเวศ").put("district", "พระนคร").put("province", "กรุงเทพมหานคร").put("distance_m", 450.0))

        assertEquals("บวรนิเวศ", queueGoReverseCandidate(rows)?.optString("canonical_name"))
    }

    @Test fun refusesFarAwayAdministrativeLabels() {
        val rows = JSONArray()
            .put(JSONObject().put("subcategory", "subdistrict").put("canonical_name", "ไกลเกินไป").put("distance_m", 50_001.0))

        assertNull(queueGoReverseCandidate(rows))
    }
    @Test fun parsesNationwidePlaceSearchResult() {
        val row = JSONObject()
            .put("place_id", "place-1")
            .put("canonical_name", "พระสิงห์")
            .put("latitude", 18.7883)
            .put("longitude", 98.9853)
            .put("category", "administrative_area")
            .put("subcategory", "subdistrict")
            .put("subdistrict", "พระสิงห์")
            .put("district", "เมืองเชียงใหม่")
            .put("province", "เชียงใหม่")
            .put("postcode", "50200")

        val result = queueGoPlaceSearchResult(row)
        assertEquals("place-1", result?.id)
        assertEquals("พระสิงห์", result?.name)
        assertEquals(18.7883, result?.latitude ?: 0.0, 0.000001)
        assertEquals("พระสิงห์ เมืองเชียงใหม่ เชียงใหม่ 50200", result?.address)
    }

    @Test fun rejectsPlaceSearchResultOutsideThailandBounds() {
        val row = JSONObject()
            .put("place_id", "foreign")
            .put("canonical_name", "Foreign")
            .put("latitude", 3.139)
            .put("longitude", 101.6869)
        assertNull(queueGoPlaceSearchResult(row))
    }
}
