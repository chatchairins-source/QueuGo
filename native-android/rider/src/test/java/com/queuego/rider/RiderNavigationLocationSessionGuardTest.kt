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
    @Test fun suspendedNavigationCannotResumeAfterLogoutOrAccountSwitch() {
        assertTrue(riderNavigationResumeSessionEligible("rider-a", "session-a", "rider-a", "session-a", true))
        assertFalse(riderNavigationResumeSessionEligible("rider-a", "session-a", null, null, true))
        assertFalse(riderNavigationResumeSessionEligible("rider-a", "session-a", "rider-b", "session-a", true))
        assertFalse(riderNavigationResumeSessionEligible("rider-a", "session-a", "rider-a", "session-b", true))
        assertFalse(riderNavigationResumeSessionEligible("rider-a", "session-a", "rider-a", "session-a", false))
        assertFalse(riderNavigationResumeSessionEligible("rider-a", "session-a", " ", "session-a", true))
        assertFalse(riderNavigationResumeSessionEligible("rider-a", "session-a", "rider-a", " ", true))
    }

    @Test fun navigationLeaseSaturatesRatherThanWrappingAcrossLongBoundary() {
        assertTrue(riderNavigationRemainingLeaseMs(Long.MAX_VALUE, -1L) == Long.MAX_VALUE)
        assertTrue(riderNavigationRemainingLeaseMs(Long.MAX_VALUE, Long.MIN_VALUE) == Long.MAX_VALUE)
        assertTrue(riderNavigationRemainingLeaseMs(0L, Long.MIN_VALUE) == Long.MAX_VALUE)
        assertTrue(riderNavigationRemainingLeaseMs(Long.MIN_VALUE, Long.MAX_VALUE) == 0L)
        assertTrue(riderNavigationRemainingLeaseMs(Long.MAX_VALUE, Long.MAX_VALUE) == 0L)
        assertTrue(riderNavigationRemainingLeaseMs(0L, 1L) == 0L)
    }

    @Test fun locationUploadRequiresAnUnexpiredAuthenticatedSession() {
        val now = 1_800_000L
        assertTrue(riderNavigationLocationSessionActive(now + 1L, now))
        assertTrue(riderNavigationLocationSessionActive(now + 30_000L, now))
        assertFalse(riderNavigationLocationSessionActive(now, now))
        assertFalse(riderNavigationLocationSessionActive(now - 1L, now))
        assertFalse(riderNavigationLocationSessionActive(Long.MIN_VALUE, now))
        assertTrue(riderNavigationLocationSessionActive(Long.MAX_VALUE, Long.MIN_VALUE))
    }

    @Test fun renewedNavigationLeaseUsesCurrentSessionExpiry() {
        val now = 1_800_000L
        assertTrue(riderNavigationRemainingLeaseMs(now + 30_000L, now) == 30_000L)
        assertTrue(riderNavigationRemainingLeaseMs(now + 1L, now) == 1L)
        assertTrue(riderNavigationRemainingLeaseMs(now, now) == 0L)
        assertTrue(riderNavigationRemainingLeaseMs(now - 1L, now) == 0L)
        // The previous session may have time left while the renewed session has expired.
        val previousSessionExpiry = now + 60_000L
        val currentSessionExpiry = now - 10L
        assertTrue(riderNavigationRemainingLeaseMs(previousSessionExpiry, now) > 0L)
        assertTrue(riderNavigationRemainingLeaseMs(currentSessionExpiry, now) == 0L)
    }

}
