package com.queuego.rider

import com.queuego.ridernavigationmap.NavigationGuidanceState
import com.queuego.ridernavigationmap.NavigationRouteInstruction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiderNavigationHudTest {
    @Test fun hudUsesAuthoritativeRouteDistanceEtaAndInstruction() {
        val hud = riderNavigationHudInfo(
            NavigationGuidanceState(
                distanceRemainingMeters = 3_240.0,
                etaSeconds = 510.0,
                nextInstruction = NavigationRouteInstruction(
                    sequence = 1,
                    turnCode = 1,
                    roadName = "ถนนหลัก",
                    sectionDistanceMeters = 300.0,
                    sectionDurationSeconds = 40.0
                ),
                distanceToInstructionMeters = 260.0
            )
        )!!

        assertEquals("3.2 กม.", hud.distanceText)
        assertEquals("9 นาที", hud.etaText)
        assertEquals("เลี้ยวขวาใน 260 ม. · ถนนหลัก", hud.instructionText)
    }

    @Test fun weakOrOffRouteGuidanceDoesNotDisplayMisleadingHud() {
        assertNull(
            riderNavigationHudInfo(
                NavigationGuidanceState(
                    weakGps = true,
                    distanceRemainingMeters = 900.0,
                    etaSeconds = 120.0
                )
            )
        )
        assertNull(
            riderNavigationHudInfo(
                NavigationGuidanceState(
                    offRoute = true,
                    distanceRemainingMeters = 900.0,
                    etaSeconds = 120.0
                )
            )
        )
    }

    @Test fun missingEtaKeepsRealDistanceWithoutInventingTime() {
        val hud = riderNavigationHudInfo(
            NavigationGuidanceState(distanceRemainingMeters = 480.0)
        )!!
        assertEquals("480 ม.", hud.distanceText)
        assertNull(hud.etaText)
    }
}
