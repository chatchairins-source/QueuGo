package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderAccountStatusTest {
    @Test fun suspendedAccountShowsRealReasonSurface() {
        val user = QueueGoUser(
            id = "r1",
            name = "Rider",
            role = "rider",
            status = "suspended",
            suspensionReason = "เอกสารรถหมดอายุ",
            suspendedAt = "2026-10-14T05:00:00Z"
        )
        val copy = riderAccountStatusCopy(user)
        assertEquals("บัญชีถูกพักสิทธิ์", copy.badge)
        assertTrue(copy.showReason)
        assertTrue(riderAccountStatusTimeLabel(user.suspendedAt)?.contains("12:00") == true)
    }

    @Test fun pendingAndDeletedStatesDoNotInventSuspensionReasons() {
        val pending = riderAccountStatusCopy(
            QueueGoUser("p","P","rider","pending")
        )
        val deleted = riderAccountStatusCopy(
            QueueGoUser("d","D","rider","deleted")
        )
        assertFalse(pending.showReason)
        assertFalse(deleted.showReason)
        assertEquals("รอตรวจสอบ", pending.badge)
        assertEquals("บัญชีถูกปิด", deleted.badge)
        assertNull(riderAccountStatusTimeLabel("not-a-time"))
    }

    @Test fun rejectedReasonIsShownOnlyWhenBackendActuallyProvidesOne() {
        val withoutReason = riderAccountStatusCopy(
            QueueGoUser("r","R","rider","rejected")
        )
        val withReason = riderAccountStatusCopy(
            QueueGoUser("r2","R2","rider","rejected", suspensionReason = "ข้อมูลไม่ครบ")
        )
        assertFalse(withoutReason.showReason)
        assertTrue(withReason.showReason)
    }
}
