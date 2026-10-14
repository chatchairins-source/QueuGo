package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderDocumentReadinessTest {
    @Test fun parserKeepsServerDocumentStatesWithoutInventingDates() {
        val raw = org.json.JSONObject()
            .put("blocking", true)
            .put(
                "items",
                org.json.JSONArray()
                    .put(
                        org.json.JSONObject()
                            .put("key", "driver_license")
                            .put("label", "ใบขับขี่")
                            .put("expires_on", "2026-10-01")
                            .put("status", "expired")
                            .put("days_remaining", -13)
                    )
                    .put(
                        org.json.JSONObject()
                            .put("key", "vehicle_prb")
                            .put("label", "พ.ร.บ. รถคันปัจจุบัน")
                            .put("vehicle_plate", "1กข1234")
                            .put("expires_on", org.json.JSONObject.NULL)
                            .put("status", "unknown")
                            .put("days_remaining", org.json.JSONObject.NULL)
                    )
            )

        val parsed = riderDocumentReadinessFromRpc(raw)
        assertTrue(parsed.blocking)
        assertEquals(2, parsed.items.size)
        assertEquals("expired", parsed.items.first().status)
        assertEquals(null, parsed.items.last().expiresOn)
        assertEquals("หมดอายุ · 2026-10-01", riderDocumentStatusText(parsed.items.first()))
        assertEquals("ยังไม่ระบุวันหมดอายุ", riderDocumentStatusText(parsed.items.last()))
    }

    @Test fun unknownBackendDoesNotInventExpiredState() {
        val parsed = riderDocumentReadinessFromRpc(org.json.JSONObject())
        assertEquals(false, parsed.blocking)
        assertEquals(emptyList<RiderDocumentStatus>(), parsed.items)
    }
}
