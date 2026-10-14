package com.queuego.rider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderDestinationPreferenceTest {
    @Test fun emptyPreferenceDoesNotInventDestination() {
        val value = riderDestinationPreferenceFromRpc(
            JSONObject()
                .put("configured", false)
                .put("enabled", false)
        )
        assertFalse(value.configured)
        assertFalse(value.enabled)
        assertNull(value.label)
        assertNull(value.latitude)
        assertNull(value.longitude)
    }

    @Test fun configuredPreferenceKeepsRealSoftPriorityDestination() {
        val value = riderDestinationPreferenceFromRpc(
            JSONObject()
                .put("configured", true)
                .put("enabled", true)
                .put("label", "บ้าน")
                .put("latitude", 15.1234)
                .put("longitude", 104.5678)
        )
        assertTrue(value.configured)
        assertTrue(value.enabled)
        assertEquals("บ้าน", value.label)
        assertEquals(15.1234, value.latitude!!, 0.000001)
        assertEquals(104.5678, value.longitude!!, 0.000001)
        assertTrue(validRiderDestinationPoint(value.latitude, value.longitude))
    }

    @Test fun parserDoesNotEnableUnconfiguredPayload() {
        val value = riderDestinationPreferenceFromRpc(
            JSONObject()
                .put("configured", false)
                .put("enabled", true)
                .put("label", "should-not-surface")
        )
        assertFalse(value.enabled)
        assertNull(value.label)
    }

    @Test fun destinationPointUsesThailandBounds() {
        assertTrue(validRiderDestinationPoint(15.0, 104.0))
        assertFalse(validRiderDestinationPoint(0.0, 0.0))
        assertFalse(validRiderDestinationPoint(22.0, 104.0))
    }
}
