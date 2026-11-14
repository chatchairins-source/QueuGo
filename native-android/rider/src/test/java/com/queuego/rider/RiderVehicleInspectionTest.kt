package com.queuego.rider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderVehicleInspectionTest {
    @Test fun emptyStatusDoesNotInventInspection() {
        val parsed = riderVehicleInspectionStatusFromRpc(JSONObject().put("required", false))
        assertFalse(parsed.required)
        assertEquals(null, parsed.requestId)
        assertEquals(null, parsed.status)
    }

    @Test fun rejectedInspectionStaysActionable() {
        val parsed = riderVehicleInspectionStatusFromRpc(
            JSONObject()
                .put("required", true)
                .put("request_id", "11111111-1111-1111-1111-111111111111")
                .put("status", "rejected")
                .put("review_note", "กรุณาถ่ายให้เห็นทะเบียนชัดเจน")
        )
        assertTrue(parsed.required)
        assertEquals("rejected", parsed.status)
        assertEquals("กรุณาถ่ายให้เห็นทะเบียนชัดเจน", parsed.reviewNote)
        assertEquals("ต้องถ่ายรูปรถใหม่", riderVehicleInspectionTitle(parsed.status))
    }

    @Test fun onlyOpenVehicleChecksBlockGoingOnline() {
        assertTrue(riderVehicleInspectionBlocksOnline(RiderVehicleInspectionStatus(true, "id", "required", null, null, null, null, null)))
        assertTrue(riderVehicleInspectionBlocksOnline(RiderVehicleInspectionStatus(true, "id", "pending", null, null, null, null, null)))
        assertTrue(riderVehicleInspectionBlocksOnline(RiderVehicleInspectionStatus(true, "id", "rejected", null, null, null, null, "ถ่ายใหม่")))
        assertFalse(riderVehicleInspectionBlocksOnline(RiderVehicleInspectionStatus(false, "id", "approved", null, null, null, null, null)))
    }
}
