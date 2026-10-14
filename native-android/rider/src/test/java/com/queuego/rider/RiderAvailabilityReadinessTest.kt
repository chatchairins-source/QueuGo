package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderAvailabilityReadinessTest {
    private val readyDiagnostics = RiderDiagnosticsSnapshot(
        networkReady = true,
        locationPermission = true,
        locationServices = true,
        notifications = true,
        googleMaps = true,
        overlayReturn = true,
        microphone = false
    )

    @Test fun fullyReadyRequiresAllOperationalChecksAndKnownIdentityState() {
        val readiness = riderAvailabilityReadiness(
            diagnostics = readyDiagnostics,
            firebaseConfigured = true,
            reverification = null,
            reverificationLoaded = true,
            documents = RiderDocumentReadiness(blocking = false, items = emptyList()),
            documentsLoaded = true
        )
        assertTrue(readiness.fullyReady)
        assertEquals(11, readiness.readyCount)
        assertEquals(11, readiness.totalCount)
    }

    @Test fun deviceBlockersAreVisibleBeforeGoOnlineAttempt() {
        val readiness = riderAvailabilityReadiness(
            diagnostics = readyDiagnostics.copy(
                networkReady = false,
                locationServices = false,
                notifications = false
            ),
            firebaseConfigured = false,
            reverification = null,
            reverificationLoaded = true,
            documents = RiderDocumentReadiness(blocking = false, items = emptyList()),
            documentsLoaded = true
        )
        assertFalse(readiness.fullyReady)
        assertEquals(
            setOf("network", "firebase", "notifications", "location_service"),
            readiness.blocked.map { it.key }.toSet()
        )
    }

    @Test fun rejectedReverificationBlocksReadinessWithServerReason() {
        val readiness = riderAvailabilityReadiness(
            diagnostics = readyDiagnostics,
            firebaseConfigured = true,
            reverification = RiderReverificationStatus(
                required = true,
                requestId = "request-1",
                status = "rejected",
                reason = null,
                requestedAt = null,
                submittedAt = null,
                reviewedAt = null,
                reviewNote = "รูปไม่ชัด กรุณาถ่ายหน้าตรงใหม่"
            ),
            reverificationLoaded = true,
            documents = RiderDocumentReadiness(blocking = false, items = emptyList()),
            documentsLoaded = true
        )
        assertFalse(readiness.fullyReady)
        assertEquals(listOf("identity"), readiness.blocked.map { it.key })
        assertEquals("รูปไม่ชัด กรุณาถ่ายหน้าตรงใหม่", readiness.blocked.single().detail)
    }

    @Test fun unknownIdentityIsCheckingNotFakeReadyOrBlocked() {
        val readiness = riderAvailabilityReadiness(
            diagnostics = readyDiagnostics,
            firebaseConfigured = true,
            reverification = null,
            reverificationLoaded = false,
            documents = RiderDocumentReadiness(blocking = false, items = emptyList()),
            documentsLoaded = true
        )
        assertFalse(readiness.fullyReady)
        assertEquals(10, readiness.readyCount)
        assertEquals(listOf("identity"), readiness.checking.map { it.key })
    }

    @Test fun expiredKnownDocumentBlocksButUnknownDatesDoNot() {
        val unknown = riderAvailabilityReadiness(
            diagnostics = readyDiagnostics,
            firebaseConfigured = true,
            reverification = null,
            reverificationLoaded = true,
            documents = RiderDocumentReadiness(
                blocking = false,
                items = listOf(
                    RiderDocumentStatus("driver_license", "ใบขับขี่", null, null, null, "unknown", null)
                )
            ),
            documentsLoaded = true
        )
        assertTrue(unknown.fullyReady)

        val expired = riderAvailabilityReadiness(
            diagnostics = readyDiagnostics,
            firebaseConfigured = true,
            reverification = null,
            reverificationLoaded = true,
            documents = RiderDocumentReadiness(
                blocking = true,
                items = listOf(
                    RiderDocumentStatus("driver_license", "ใบขับขี่", null, null, "2026-10-01", "expired", -13)
                )
            ),
            documentsLoaded = true
        )
        assertFalse(expired.fullyReady)
        assertEquals(listOf("documents"), expired.blocked.map { it.key })
    }

    @Test fun expiringDocumentWarnsWithoutBlockingAvailability() {
        val readiness = riderAvailabilityReadiness(
            diagnostics = readyDiagnostics,
            firebaseConfigured = true,
            reverification = null,
            reverificationLoaded = true,
            documents = RiderDocumentReadiness(
                blocking = false,
                items = listOf(
                    RiderDocumentStatus("national_id", "บัตรประชาชน", null, null, "2026-10-30", "expiring", 16)
                )
            ),
            documentsLoaded = true
        )
        assertTrue(readiness.fullyReady)
        assertEquals(RiderAvailabilityCheckState.READY, readiness.checks.single { it.key == "documents" }.state)
    }

    @Test fun pendingVehicleInspectionIsVisibleBeforeOnlineAttempt() {
        val readiness = riderAvailabilityReadiness(
            diagnostics = readyDiagnostics,
            firebaseConfigured = true,
            reverification = null,
            reverificationLoaded = true,
            documents = RiderDocumentReadiness(false),
            documentsLoaded = true,
            vehicleInspection = RiderVehicleInspectionStatus(
                required = true,
                requestId = "inspection-1",
                status = "pending",
                reason = null,
                requestedAt = null,
                submittedAt = null,
                reviewedAt = null,
                reviewNote = null
            ),
            vehicleInspectionLoaded = true
        )
        assertFalse(readiness.fullyReady)
        assertEquals(listOf("vehicle_inspection"), readiness.blocked.map { it.key })
    }

    @Test fun unapprovedSelectedVehicleMatchesSetOnlineBlocker() {
        val readiness = riderAvailabilityReadiness(
            diagnostics = readyDiagnostics,
            firebaseConfigured = true,
            reverification = null,
            reverificationLoaded = true,
            documents = RiderDocumentReadiness(false),
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
        assertFalse(readiness.fullyReady)
        assertEquals(listOf("selected_vehicle"), readiness.blocked.map { it.key })
    }
}
