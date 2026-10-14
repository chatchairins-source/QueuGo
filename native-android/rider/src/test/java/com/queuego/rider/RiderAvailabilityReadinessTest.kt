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
        assertEquals(9, readiness.readyCount)
        assertEquals(9, readiness.totalCount)
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
        assertEquals(8, readiness.readyCount)
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
}
