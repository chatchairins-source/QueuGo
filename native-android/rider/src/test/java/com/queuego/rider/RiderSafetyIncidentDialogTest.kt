package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderSafetyIncidentDialogTest {
    @Test fun safetyReasonsCoverOperationalRiskWithoutInventingEmergencyDispatch() {
        assertEquals(
            listOf(
                "accident_or_injury",
                "threat_or_harassment",
                "unsafe_location",
                "road_hazard",
                "vehicle_issue",
                "other"
            ),
            riderSafetyIncidentReasons.map { it.key }
        )
        assertEquals(
            riderSafetyIncidentReasons.size,
            riderSafetyIncidentReasons.map { it.key }.distinct().size
        )
    }

    @Test fun orderLinkedIncidentIsMachineReadable() {
        val reason = riderSafetyIncidentReasons.first { it.key == "unsafe_location" }
        val details = riderSafetyIncidentDetails(
            orderNumber = "QT-4321",
            reason = reason,
            note = "จุดส่งมืดและไม่มีทางจอด",
            location = null
        )

        assertTrue(details.startsWith("[SAFETY_INCIDENT:unsafe_location] QT-4321"))
        assertTrue(details.contains("พื้นที่หรือจุดส่งไม่ปลอดภัย"))
        assertFalse(details.contains("ตำแหน่งที่ Rider เลือกแนบ="))
    }

    @Test fun exactLocationAppearsOnlyWhenExplicitlyPassed() {
        val reason = riderSafetyIncidentReasons.first { it.key == "road_hazard" }
        val details = riderSafetyIncidentDetails(
            orderNumber = "QT-7777",
            reason = reason,
            note = "",
            location = RiderSafetyLocation(15.244444, 104.847222)
        )

        assertTrue(details.contains("ตำแหน่งที่ Rider เลือกแนบ=15.244444,104.847222"))
    }

    @Test fun incidentWithoutActiveOrderStillProducesValidSupportDetails() {
        val reason = riderSafetyIncidentReasons.last()
        val details = riderSafetyIncidentDetails(
            orderNumber = null,
            reason = reason,
            note = "",
            location = null
        )

        assertTrue(details.contains("ไม่มีออเดอร์"))
        assertTrue(details.length >= 10)
    }

    @Test fun noteIsBoundedForExistingSupportTicketLimit() {
        val reason = riderSafetyIncidentReasons.first()
        val details = riderSafetyIncidentDetails(
            orderNumber = "QT-9999",
            reason = reason,
            note = "x".repeat(5000),
            location = RiderSafetyLocation(15.0, 104.0)
        )

        assertTrue(details.length < 2000)
    }
}
