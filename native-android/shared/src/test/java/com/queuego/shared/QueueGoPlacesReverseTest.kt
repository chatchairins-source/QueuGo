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
}
