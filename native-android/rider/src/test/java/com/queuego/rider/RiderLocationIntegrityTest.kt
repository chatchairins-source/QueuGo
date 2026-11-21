package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderLocationIntegrityTest {
    @Test fun noObservedFixesRemainInformational() {
        val health = riderLocationIntegrityHealth(RiderLocationIntegrityDiagnostics())
        assertNull(health.healthy)
        assertTrue(health.headline.contains("ยังไม่มีข้อมูล"))
    }

    @Test fun trustedFixesRemainHealthy() {
        val health = riderLocationIntegrityHealth(
            RiderLocationIntegrityDiagnostics(observedFixes = 24, mockFixes = 0)
        )
        assertEquals(true, health.healthy)
        assertTrue(health.detail.contains("24"))
    }

    @Test fun anyAndroidMarkedMockFixProducesWarningWithoutInventingBlockState() {
        val health = riderLocationIntegrityHealth(
            RiderLocationIntegrityDiagnostics(
                observedFixes = 20,
                mockFixes = 2,
                lastMockElapsedRealtimeNanos = 123L
            )
        )
        assertFalse(health.healthy!!)
        assertTrue(health.detail.contains("2/20"))
        assertTrue(health.detail.contains("ยังไม่บล็อกงาน"))
    }

    @Test fun operationalLocationNeverUsesStaleLocationManagerCache() {
        val now = 600_000_000_000L
        assertTrue(riderOperationalLocationFixIsFresh(now, now - 1_000_000_000L))
        assertTrue(riderOperationalLocationFixIsFresh(now, now - 30_000_000_000L))
        assertFalse(riderOperationalLocationFixIsFresh(now, now - 31_000_000_000L))
        assertFalse(riderOperationalLocationFixIsFresh(now, 0L))
    }

    @Test fun operationalLocationRejectsFutureAndCrossRebootFixes() {
        val now = 10_000_000_000L
        assertFalse(riderOperationalLocationFixIsFresh(now, now + 1_000_000L))
        assertFalse(riderOperationalLocationFixIsFresh(now, 800_000_000_000L))
        assertFalse(riderOperationalLocationFixIsFresh(0L, 1_000_000L))
    }
}
