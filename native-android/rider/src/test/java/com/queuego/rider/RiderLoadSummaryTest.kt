package com.queuego.rider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiderLoadSummaryTest {
    @Test fun parserKeepsRealWeightCapacityAndUtilization() {
        val summary = riderLoadSummaryFromRpc(
            JSONObject()
                .put("weight_kg", 16.0)
                .put("vehicle_capacity_kg", 20.0)
                .put("utilization_ratio", 0.8)
                .put("state", "near_capacity")
        )
        assertEquals(16.0, summary.weightKg!!, 0.0001)
        assertEquals(20.0, summary.vehicleCapacityKg!!, 0.0001)
        assertEquals(0.8, summary.utilizationRatio!!, 0.0001)
        assertEquals("near_capacity", summary.state)
        assertEquals("งานใหญ่ · ใกล้เต็มความจุรถ", riderLoadSummaryStateLabel(summary.state))
        assertEquals("น้ำหนัก 16 กก. · รถรับได้ 20 กก. · 80%", riderLoadSummaryLabel(summary))
    }

    @Test fun unknownWeightDoesNotInventLoadLabel() {
        val summary = riderLoadSummaryFromRpc(JSONObject().put("state", "unknown"))
        assertNull(summary.weightKg)
        assertNull(summary.vehicleCapacityKg)
        assertNull(summary.utilizationRatio)
        assertNull(riderLoadSummaryLabel(summary))
    }

    @Test fun invalidBackendStateFailsClosedToUnknown() {
        val summary = riderLoadSummaryFromRpc(
            JSONObject()
                .put("weight_kg", 5.5)
                .put("state", "magic")
        )
        assertEquals("unknown", summary.state)
        assertEquals("น้ำหนัก 5.5 กก.", riderLoadSummaryLabel(summary))
    }

    @Test fun overCapacityAndUnknownCapacityRemainExplicit() {
        val over = riderLoadSummaryFromRpc(
            JSONObject()
                .put("weight_kg", 24.0)
                .put("vehicle_capacity_kg", 20.0)
                .put("utilization_ratio", 1.2)
                .put("state", "over_capacity")
        )
        assertEquals("เกินความจุรถที่เลือก", riderLoadSummaryStateLabel(over.state))
        assertEquals("น้ำหนัก 24 กก. · รถรับได้ 20 กก. · 120%", riderLoadSummaryLabel(over))

        val noCapacity = riderLoadSummaryFromRpc(
            JSONObject()
                .put("weight_kg", 7.25)
                .put("state", "capacity_unknown")
        )
        assertEquals("ยังไม่ทราบความจุรถ", riderLoadSummaryStateLabel(noCapacity.state))
        assertEquals("น้ำหนัก 7.3 กก.", riderLoadSummaryLabel(noCapacity))
    }
}
