package com.queuego.rider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderSupplyPlanningTest {
    @Test fun parserKeepsOnlyValidHistoricalWindows() {
        val plan = riderSupplyPlanFromRpc(
            JSONObject()
                .put("ready", true)
                .put("confidence", "medium")
                .put("source_days", 28)
                .put("forecast", false)
                .put(
                    "windows",
                    JSONArray()
                        .put(JSONObject().put("day_of_week", 1).put("start_hour", 6).put("end_hour", 8).put("level", 3))
                        .put(JSONObject().put("day_of_week", 7).put("start_hour", 18).put("end_hour", 20).put("level", 2))
                        .put(JSONObject().put("day_of_week", 9).put("start_hour", 2).put("end_hour", 4).put("level", 3))
                )
        )

        assertTrue(plan.ready)
        assertEquals("medium", plan.confidence)
        assertFalse(plan.forecast)
        assertEquals(2, plan.windows.size)
        assertEquals("จันทร์ · 06:00–08:00", riderSupplyWindowLabel(plan.windows.first()))
        assertEquals("คึกคักมาก", riderSupplyLevelLabel(plan.windows.first().level))
    }

    @Test fun insufficientDataDoesNotInventPlanningWindows() {
        val plan = riderSupplyPlanFromRpc(
            JSONObject()
                .put("ready", false)
                .put("confidence", "low")
                .put("source_days", 28)
                .put("forecast", false)
                .put("windows", JSONArray().put(JSONObject().put("day_of_week", 1).put("start_hour", 6).put("end_hour", 8).put("level", 3)))
        )

        assertFalse(plan.ready)
        assertTrue(plan.windows.isEmpty())
        assertEquals("ข้อมูลย้อนหลังยังน้อย", riderSupplyConfidenceLabel(plan.confidence))
    }
}
