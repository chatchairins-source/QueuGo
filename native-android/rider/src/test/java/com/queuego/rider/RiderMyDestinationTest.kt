package com.queuego.rider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderMyDestinationTest {
    @Test fun parsesConfiguredPreferenceWithoutInventingCoordinates() {
        val value = riderDestinationPreferenceFromRpc(
            JSONObject()
                .put("configured", true)
                .put("enabled", true)
                .put("label", "บ้าน")
                .put("latitude", 15.1)
                .put("longitude", 103.1)
        )
        assertTrue(value.configured)
        assertTrue(value.enabled)
        assertEquals("บ้าน", value.label)
        assertEquals("ไปทาง บ้าน", riderDestinationControlLabel(value))
    }

    @Test fun invalidBackendCoordinatesFailClosed() {
        val value = riderDestinationPreferenceFromRpc(
            JSONObject()
                .put("configured", true)
                .put("enabled", true)
                .put("label", "ผิดพื้นที่")
                .put("latitude", 0.0)
                .put("longitude", 0.0)
        )
        assertFalse(value.configured)
        assertFalse(value.enabled)
        assertNull(value.latitude)
        assertEquals("ตั้งจุดหมาย", riderDestinationControlLabel(value))
    }

    @Test fun savePolicyRequiresThailandPointAndRealLabel() {
        assertTrue(riderDestinationReady(15.0, 103.0, "บ้าน"))
        assertFalse(riderDestinationReady(null, 103.0, "บ้าน"))
        assertFalse(riderDestinationReady(15.0, 103.0, "   "))
        assertFalse(riderDestinationReady(30.0, 103.0, "บ้าน"))
    }

    @Test fun reverseGeocodeFormatterKeepsOnlyUsefulAddressParts() {
        val raw = JSONObject()
            .put("house_number", "99")
            .put("road", "ถนนทดสอบ")
            .put("province", "บุรีรัมย์")
            .put("postcode", "31000")
            .toString()
        assertEquals("99 ถนนทดสอบ บุรีรัมย์ 31000", riderFormatLongdoAddress(raw))
    }
}
