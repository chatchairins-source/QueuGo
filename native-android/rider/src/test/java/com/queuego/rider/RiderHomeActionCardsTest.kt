package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderHomeActionCardsTest {
    private fun diagnostics(
        batteryExempt: Boolean = true,
        backgroundRestricted: Boolean = false
    ) = RiderDiagnosticsSnapshot(
        networkReady = true,
        locationPermission = true,
        locationServices = true,
        notifications = true,
        googleMaps = true,
        overlayReturn = true,
        microphone = true,
        batteryOptimizationExempt = batteryExempt,
        backgroundRestricted = backgroundRestricted
    )

    private fun mission(state: String = "active") = RiderMission(
        id = "mission-1",
        title = "ส่งครบ 10 เที่ยว",
        description = null,
        targetJobs = 10,
        rewardNote = null,
        startsAt = null,
        endsAt = null,
        state = state,
        progressJobs = 4,
        completed = false
    )

    @Test fun emptyRealStateDoesNotInventActionCards() {
        val cards = riderHomeActionCards(
            missions = emptyList(),
            diagnostics = diagnostics(),
            documents = RiderDocumentReadiness(blocking = false),
            documentsLoaded = true
        )
        assertTrue(cards.isEmpty())
    }

    @Test fun urgentDocumentThenMissionTakePriorityAndLimitToTwo() {
        val cards = riderHomeActionCards(
            missions = listOf(mission()),
            diagnostics = diagnostics(batteryExempt = false, backgroundRestricted = true),
            documents = RiderDocumentReadiness(
                blocking = true,
                items = listOf(
                    RiderDocumentStatus(
                        key = "license",
                        label = "ใบขับขี่",
                        vehicleId = null,
                        vehiclePlate = null,
                        expiresOn = "2026-10-10",
                        status = "expired",
                        daysRemaining = -4
                    )
                )
            ),
            documentsLoaded = true
        )

        assertEquals(2, cards.size)
        assertEquals("documents:blocked", cards[0].key)
        assertTrue(cards[0].urgent)
        assertEquals("mission:mission-1", cards[1].key)
        assertEquals(RiderHomeActionTarget.EARNINGS, cards[1].target)
    }

    @Test fun upcomingMissionAndBackgroundHealthStayActionable() {
        val cards = riderHomeActionCards(
            missions = listOf(mission("upcoming")),
            diagnostics = diagnostics(batteryExempt = false),
            documents = null,
            documentsLoaded = false
        )

        assertEquals(listOf("mission:mission-1", "background-health"), cards.map { it.key })
        assertEquals(RiderHomeActionTarget.PROFILE, cards.last().target)
    }
    @Test fun identityAndVehicleInspectionPreemptMissionAndBackground() {
        val cards = riderHomeActionCards(
            missions = listOf(mission()),
            diagnostics = diagnostics(batteryExempt = false, backgroundRestricted = true),
            documents = RiderDocumentReadiness(blocking = false),
            documentsLoaded = true,
            reverification = RiderReverificationStatus(
                required = true,
                requestId = "identity-1",
                status = "rejected",
                reason = null,
                requestedAt = null,
                submittedAt = null,
                reviewedAt = null,
                reviewNote = "รูปไม่ชัด"
            ),
            reverificationLoaded = true,
            vehicleInspection = RiderVehicleInspectionStatus(
                required = true,
                requestId = "vehicle-check-1",
                status = "pending",
                reason = null,
                requestedAt = null,
                submittedAt = null,
                reviewedAt = null,
                reviewNote = null
            ),
            vehicleInspectionLoaded = true
        )

        assertEquals(2, cards.size)
        assertEquals("identity:rejected", cards[0].key)
        assertEquals("vehicle-inspection:pending", cards[1].key)
        assertTrue(cards.all { it.urgent })
        assertTrue(cards.all { it.target == RiderHomeActionTarget.PROFILE })
    }

    @Test fun unapprovedSelectedVehicleBeatsExpiredDocument() {
        val cards = riderHomeActionCards(
            missions = emptyList(),
            diagnostics = diagnostics(),
            documents = RiderDocumentReadiness(
                blocking = true,
                items = listOf(
                    RiderDocumentStatus(
                        key = "license",
                        label = "ใบขับขี่",
                        vehicleId = null,
                        vehiclePlate = null,
                        expiresOn = "2026-10-10",
                        status = "expired",
                        daysRemaining = -4
                    )
                )
            ),
            documentsLoaded = true,
            selectedVehicle = RiderVehicle(
                id = "vehicle-1",
                type = "motorcycle",
                name = null,
                plate = "1กข1234",
                capacityKg = null,
                status = "pending",
                selected = true,
                legacySeed = false,
                approvedAt = null,
                reviewNote = null
            ),
            vehiclesLoaded = true
        )

        assertEquals(listOf("selected-vehicle", "documents:blocked"), cards.map { it.key })
    }
}
