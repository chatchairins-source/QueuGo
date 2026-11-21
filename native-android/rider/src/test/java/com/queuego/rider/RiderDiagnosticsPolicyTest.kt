package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderDiagnosticsPolicyTest {
    @Test fun deliveryReadinessRequiresFourQueueGoOperationalChecks() {
        val ready = RiderDiagnosticsSnapshot(
            networkReady = true,
            locationPermission = true,
            locationServices = true,
            notifications = true,
            googleMaps = false,
            overlayReturn = false,
            microphone = false
        )
        assertTrue(ready.readyForDelivery)
        assertEquals(4, ready.essentialPassedCount)

        val noNotifications = ready.copy(notifications = false)
        assertFalse(noNotifications.readyForDelivery)
        assertEquals(3, noNotifications.essentialPassedCount)
    }

    @Test fun backgroundHealthIsAdvisoryAndDoesNotChangeCoreDeliveryGate() {
        val restricted = RiderDiagnosticsSnapshot(
            networkReady = true,
            locationPermission = true,
            locationServices = true,
            notifications = true,
            googleMaps = false,
            overlayReturn = true,
            microphone = true,
            batteryOptimizationExempt = false,
            backgroundRestricted = true
        )
        assertTrue(restricted.readyForDelivery)
        assertEquals(4, restricted.essentialPassedCount)
        assertFalse(restricted.backgroundReliabilityReady)

        val healthy = restricted.copy(
            batteryOptimizationExempt = true,
            backgroundRestricted = false
        )
        assertTrue(healthy.backgroundReliabilityReady)
    }
    @Test fun externalGoogleMapsIsAdvisoryForNativeQueueGoNavigation() {
        val nativeReady = RiderDiagnosticsSnapshot(
            networkReady = true,
            locationPermission = true,
            locationServices = true,
            notifications = true,
            googleMaps = false,
            overlayReturn = false,
            microphone = false
        )
        assertTrue(nativeReady.readyForDelivery)
        assertEquals(4, nativeReady.essentialPassedCount)
        assertEquals(nativeReady.readyForDelivery, nativeReady.copy(googleMaps = true).readyForDelivery)
        assertEquals(nativeReady.essentialPassedCount, nativeReady.copy(googleMaps = true).essentialPassedCount)

        assertFalse(nativeReady.copy(networkReady = false).readyForDelivery)
        assertFalse(nativeReady.copy(locationPermission = false).readyForDelivery)
        assertFalse(nativeReady.copy(locationServices = false).readyForDelivery)
        assertFalse(nativeReady.copy(notifications = false).readyForDelivery)
    }

}
