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
            microphone = false,
            powerSaverOff = false,
            backgroundUnrestricted = false
        )
        assertTrue(ready.readyForDelivery)
        assertEquals(5, ready.essentialPassedCount)
        assertFalse(ready.powerSaverOff)
        assertFalse(ready.backgroundUnrestricted)

        val noNotifications = ready.copy(notifications = false)
        assertFalse(noNotifications.readyForDelivery)
        assertEquals(4, noNotifications.essentialPassedCount)
    }
}
