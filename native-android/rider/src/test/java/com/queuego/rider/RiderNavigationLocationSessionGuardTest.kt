package com.queuego.rider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderNavigationLocationSessionGuardTest {
    private fun allowed(
        boundUser: String = "rider-a",
        boundSession: String = "session-a",
        currentUser: String = "rider-a",
        currentSession: String = "session-a",
        ready: Boolean = true,
        orderId: String? = "order-a",
        gps: Boolean = true,
        bindingCurrent: Boolean = true
    ) = riderNavigationLocationUpdateEligible(
        boundUser, boundSession, currentUser, currentSession,
        ready, orderId, gps, bindingCurrent
    )


    private fun foregroundAllowed(
        boundUser: String = "rider-a",
        boundSession: String = "session-a",
        currentUser: String = "rider-a",
        currentSession: String = "session-a",
        bindingCurrent: Boolean = true
    ) = riderNavigationForegroundSessionEligible(
        boundUser, boundSession, currentUser, currentSession, bindingCurrent
    )

    @Test fun validForegroundSessionCanSyncDispatchPreference() {
        assertTrue(foregroundAllowed())
    }

    @Test fun staleForegroundBindingOrAccountSwitchCannotSyncDispatchPreference() {
        assertFalse(foregroundAllowed(bindingCurrent = false))
        assertFalse(foregroundAllowed(currentUser = "rider-b"))
        assertFalse(foregroundAllowed(currentSession = "session-b"))
    }

    @Test fun blankForegroundIdentityCannotSyncDispatchPreference() {
        assertFalse(foregroundAllowed(boundUser = ""))
        assertFalse(foregroundAllowed(boundSession = " "))
        assertFalse(foregroundAllowed(currentUser = ""))
        assertFalse(foregroundAllowed(currentSession = " "))
        assertFalse(foregroundAllowed(boundUser = " ", currentUser = " "))
        assertFalse(foregroundAllowed(boundSession = " ", currentSession = " "))
    }

    @Test fun validActiveNavigationCanUpload() {
        assertTrue(allowed())
    }

    @Test fun accountOrSessionSwitchCannotUploadOldCallback() {
        assertFalse(allowed(currentUser = "rider-b"))
        assertFalse(allowed(currentSession = "session-b"))
        assertFalse(allowed(bindingCurrent = false))
    }

    @Test fun blankSessionUserOrOrderCannotUpload() {
        assertFalse(allowed(boundUser = ""))
        assertFalse(allowed(currentUser = ""))
        assertFalse(allowed(boundSession = ""))
        assertFalse(allowed(currentSession = ""))
        assertFalse(allowed(orderId = ""))
        assertFalse(allowed(orderId = "   "))
        assertFalse(allowed(boundUser = " ", currentUser = " "))
        assertFalse(allowed(boundSession = " ", currentSession = " "))
    }

    @Test fun revokedGpsOrInactiveJobCannotUpload() {
        assertFalse(allowed(gps = false))
        assertFalse(allowed(ready = false))
        assertFalse(allowed(orderId = null))
    }
}
