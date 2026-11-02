package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderOnlineStatusCopyTest {
    @Test fun offlineNeverClaimsToBeReceivingJobs() {
        for (permission in listOf(false, true)) {
            for (service in listOf(false, true)) {
                val copy = riderOnlineStatusCopy(false, permission, service)
                assertEquals("ยังไม่พร้อมรับงาน", copy.title)
                assertFalse(copy.locationWarning)
            }
        }
    }

    @Test fun serverOnlineWithMissingPermissionShowsWarningInsteadOfReady() {
        val copy = riderOnlineStatusCopy(true, false, true)
        assertTrue(copy.locationWarning)
        assertTrue(copy.title.contains("GPS ไม่พร้อม"))
        assertTrue(copy.detail.contains("สถานะเซิร์ฟเวอร์ยังเปิดอยู่"))
        assertFalse(copy.title == "พร้อมรับงานแล้ว")
    }

    @Test fun serverOnlineWithDisabledLocationServiceShowsWarning() {
        val copy = riderOnlineStatusCopy(true, true, false)
        assertTrue(copy.locationWarning)
        assertFalse(copy.title == "พร้อมรับงานแล้ว")
    }

    @Test fun onlyServerOnlineAndBothGpsChecksMayShowReady() {
        val copy = riderOnlineStatusCopy(true, true, true)
        assertEquals("พร้อมรับงานแล้ว", copy.title)
        assertFalse(copy.locationWarning)
    }

    @Test fun restoringGpsChangesOnlyPresentationNotServerLease() {
        val denied = riderOnlineStatusCopy(true, false, true)
        val restored = riderOnlineStatusCopy(true, true, true)
        val deniedAgain = riderOnlineStatusCopy(true, false, true)
        assertTrue(denied.locationWarning)
        assertFalse(restored.locationWarning)
        assertEquals(denied, deniedAgain)
    }
}
