package com.queuego.rider

import com.queuego.ridernavigationmap.NavigationMapFix
import com.queuego.ridernavigationmap.NavigationMapPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderDriveSafetyTest {
    private fun fix(speed: Float?, accuracy: Float?) = NavigationMapFix(
        point = NavigationMapPoint(15.0, 103.0),
        speedMetersPerSecond = speed,
        elapsedRealtimeNanos = 10_000_000_000L,
        speedAccuracyMetersPerSecond = accuracy
    )

    @Test fun speedUsesExistingGpsFixAndRejectsWeakSamples() {
        assertEquals(36, riderReliableSpeedKph(fix(10f, 1f)))
        assertEquals(0, riderReliableSpeedKph(fix(0.1f, 1f)))
        assertNull(riderReliableSpeedKph(fix(10f, 6f)))
        assertNull(riderReliableSpeedKph(fix(60f, 1f)))
        assertNull(riderReliableSpeedKph(null))
    }

    @Test fun restReminderIsAdvisoryOncePerContinuousNavigationSession() {
        val start = 1_000L
        assertFalse(riderRestReminderDue(start, start + RIDER_REST_REMINDER_AFTER_MS - 1L, null))
        assertTrue(riderRestReminderDue(start, start + RIDER_REST_REMINDER_AFTER_MS, null))
        assertFalse(riderRestReminderDue(start, start + RIDER_REST_REMINDER_AFTER_MS + 60_000L, start))
        assertEquals("2 ชม. 5 นาที", riderDriveDurationLabel(125L * 60_000L))
    }
}
