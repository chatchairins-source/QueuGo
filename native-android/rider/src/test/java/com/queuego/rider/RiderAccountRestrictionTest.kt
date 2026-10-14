package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RiderAccountRestrictionTest {
    @Test fun suspendedAccountIsNeverDescribedAsPendingApproval() {
        val suspended = riderAccountRestrictionContent("suspended")
        assertEquals("บัญชีถูกระงับ", suspended.badge)
        assertEquals("บัญชี Rider ถูกระงับชั่วคราว", suspended.title)
        assertFalse(suspended.description.contains("เมื่อ Admin อนุมัติ"))
    }

    @Test fun pendingAccountKeepsApplicationReviewCopy() {
        val pending = riderAccountRestrictionContent("pending")
        assertEquals("อยู่ระหว่างตรวจสอบ", pending.badge)
        assertEquals("ใบสมัครของคุณอยู่ระหว่างตรวจสอบ", pending.title)
    }

    @Test fun unknownNonActiveStatusFailsClosedToGenericRestriction() {
        val unknown = riderAccountRestrictionContent("custom_hold")
        assertEquals("บัญชียังไม่พร้อมใช้งาน", unknown.badge)
        assertEquals("ยังไม่สามารถเปิดรับงานได้", unknown.title)
    }
}
