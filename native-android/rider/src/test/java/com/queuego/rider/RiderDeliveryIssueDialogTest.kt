package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderDeliveryIssueDialogTest {
    @Test fun parityReasonsCoverUnavailableRefusalCashAndDropoffSafety() {
        assertEquals(
            listOf(
                "customer_unreachable",
                "recipient_refuses_delivery",
                "recipient_refuses_cash",
                "wrong_dropoff",
                "unsafe_dropoff",
                "other"
            ),
            riderDeliveryIssueReasons.map { it.key }
        )
    }

    @Test fun supportDetailsStayOrderLinkedAndMachineReadable() {
        val reason = riderDeliveryIssueReasons.first { it.key == "customer_unreachable" }
        val details = riderDeliveryIssueDetails("QT-1234", reason, "โทรสองครั้ง ไม่มีคนรับ")

        assertTrue(details.startsWith("[DELIVERY_ISSUE:customer_unreachable] QT-1234"))
        assertTrue(details.contains("ติดต่อลูกค้าไม่ได้"))
        assertTrue(details.contains("โทรสองครั้ง ไม่มีคนรับ"))
    }

    @Test fun blankNoteDoesNotAddEmptyNoteMarker() {
        val reason = riderDeliveryIssueReasons.first { it.key == "recipient_refuses_cash" }
        val details = riderDeliveryIssueDetails("QT-9988", reason, "   ")

        assertTrue(details.contains("ลูกค้าปฏิเสธชำระเงิน"))
        assertFalse(details.contains("หมายเหตุ:"))
    }

    @Test fun noteIsBoundedForExistingSupportTicketLimit() {
        val reason = riderDeliveryIssueReasons.last()
        val details = riderDeliveryIssueDetails("QT-1111", reason, "x".repeat(5000))

        assertTrue(details.length < 2000)
    }
}
