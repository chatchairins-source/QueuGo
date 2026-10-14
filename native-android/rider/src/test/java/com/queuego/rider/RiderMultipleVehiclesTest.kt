package com.queuego.rider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderMultipleVehiclesTest {
    @Test fun parsesVehicleListWithoutInventingRows() {
        val parsed = riderVehiclesFromRpc(
            JSONArray()
                .put(
                    JSONObject()
                        .put("vehicle_id", "v1")
                        .put("vehicle_type", "motorcycle")
                        .put("vehicle_plate", "1กข1234")
                        .put("status", "approved")
                        .put("is_selected", true)
                        .put("legacy_seed", false)
                        .put("vehicle_capacity_kg", 25.0)
                )
                .put(JSONObject().put("vehicle_id", "bad"))
        )

        assertEquals(1, parsed.size)
        assertEquals("v1", parsed.first().id)
        assertTrue(parsed.first().selected)
        assertEquals(25.0, parsed.first().capacityKg!!, 0.0001)
        assertEquals("กำลังใช้ · อนุมัติแล้ว", riderVehicleStatusLabel(parsed.first()))
    }

    @Test fun legacySelectedVehicleDoesNotBlockExistingRiderOnline() {
        val legacy = RiderVehicle(
            id = "legacy",
            type = "motorcycle",
            name = null,
            plate = "OLD",
            capacityKg = null,
            status = "pending",
            selected = true,
            legacySeed = true,
            approvedAt = null,
            reviewNote = null
        )
        assertFalse(riderVehicleBlocksOnline(legacy))
        assertEquals("รถเดิม · ยังไม่ยืนยันใหม่", riderVehicleStatusLabel(legacy))
    }

    @Test fun newUnapprovedSelectedVehicleBlocksOnline() {
        val pending = RiderVehicle(
            id = "new",
            type = "car",
            name = "รถใหม่",
            plate = "NEW",
            capacityKg = 100.0,
            status = "pending",
            selected = true,
            legacySeed = false,
            approvedAt = null,
            reviewNote = null
        )
        assertTrue(riderVehicleBlocksOnline(pending))
        assertEquals("100", riderVehicleCapacityLabel(100.0))
        assertEquals("12.5", riderVehicleCapacityLabel(12.5))
    }
}
