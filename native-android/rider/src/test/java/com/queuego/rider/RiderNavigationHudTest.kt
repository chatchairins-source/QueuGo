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
                distanceToInstructionMeters = 260.0,
                followingInstruction = NavigationRouteInstruction(
                    sequence = 2,
                    turnCode = 0,
                    roadName = "ถนนรอง",
                    sectionDistanceMeters = 100.0,
                    sectionDurationSeconds = 20.0
                ),
                distanceToFollowingInstructionMeters = 290.0
            )
        )!!

        assertEquals("3.2 กม.", hud.distanceText)
        assertEquals("9 นาที", hud.etaText)
        assertEquals("เลี้ยวขวาใน 260 ม. · ถนนหลัก", hud.instructionText)
        assertEquals("260 ม.", hud.maneuverDistanceText)
        assertEquals("เลี้ยวขวา", hud.maneuverText)
        assertEquals("ถนนหลัก", hud.maneuverRoadName)
        assertEquals(1, hud.maneuverTurnCode)
        assertEquals("เลี้ยวซ้ายใน 290 ม. · ถนนรอง", hud.followingInstructionText)
        assertEquals(0, hud.followingTurnCode)
    }

    @Test fun followingTurnStaysHiddenWhenBothTurnsAreFar() {
        val hud = riderNavigationHudInfo(
            NavigationGuidanceState(
                distanceRemainingMeters = 2_000.0,
                etaSeconds = 300.0,
                nextInstruction = NavigationRouteInstruction(1, 1, "A", 600.0, 60.0),
                distanceToInstructionMeters = 600.0,
                followingInstruction = NavigationRouteInstruction(2, 0, "B", 500.0, 50.0),
                distanceToFollowingInstructionMeters = 1_100.0
            )
        )!!
        assertNull(hud.followingInstructionText)
        assertNull(hud.followingTurnCode)
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
