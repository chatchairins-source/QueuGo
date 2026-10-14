package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderDiagnosticsPolicyTest {
    @Test fun deliveryReadinessRequiresAllFiveOperationalChecks() {
        val ready = RiderDiagnosticsSnapshot(
            networkReady = true,
            locationPermission = true,
            locationServices = true,
            notifications = true,
            googleMaps = true,
            overlayReturn = false,
            microphone = false
        )
        assertTrue(ready.readyForDelivery)
        assertEquals(5, ready.essentialPassedCount)

        val noNotifications = ready.copy(notifications = false)
        assertFalse(noNotifications.readyForDelivery)
        assertEquals(4, noNotifications.essentialPassedCount)
    }

    @Test fun backgroundHealthIsAdvisoryAndDoesNotChangeCoreDeliveryGate() {
        val restricted = RiderDiagnosticsSnapshot(
            networkReady = true,
            locationPermission = true,
            locationServices = true,
            notifications = true,
            googleMaps = true,
            overlayReturn = true,
            microphone = true,
            batteryOptimizationExempt = false,
            backgroundRestricted = true
        )
        assertTrue(restricted.readyForDelivery)
        assertEquals(5, restricted.essentialPassedCount)
        assertFalse(restricted.backgroundReliabilityReady)

        val healthy = restricted.copy(
            batteryOptimizationExempt = true,
            backgroundRestricted = false
        )
        assertTrue(healthy.backgroundReliabilityReady)
    }
}
