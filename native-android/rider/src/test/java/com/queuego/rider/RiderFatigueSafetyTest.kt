package com.queuego.rider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RiderFatigueSafetyTest {
    @Test fun parserKeepsServerOwnedPresenceStatsWithoutMedicalClaim() {
        val status = riderFatigueStatusFromRpc(
            JSONObject()
                .put("online", true)
                .put("online_since", "2026-10-14T05:00:00Z")
                .put("continuous_online_minutes", 245)
                .put("online_minutes_today", 360)
                .put("level", "strong_rest_recommended")
                .put("medical_detection", false)
        )

        assertEquals(245, status.continuousOnlineMinutes)
        assertEquals(360, status.onlineMinutesToday)
        assertEquals("strong_rest_recommended", status.level)
        assertFalse(status.medicalDetection)
    }

    @Test fun formattingAndUnknownLevelsFailSafe() {
        assertEquals("2 ชม. 5 นาที", riderFatigueDurationLabel(125))
        assertEquals("45 นาที", riderFatigueDurationLabel(45))

        val status = riderFatigueStatusFromRpc(
            JSONObject()
                .put("continuous_online_minutes", -9)
                .put("online_minutes_today", -3)
                .put("level", "unknown")
        )
        assertEquals(0, status.continuousOnlineMinutes)
        assertEquals(0, status.onlineMinutesToday)
        assertEquals("normal", status.level)
    }
}
