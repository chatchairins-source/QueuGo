package com.queuego.rider

import org.junit.Assert.*
import org.junit.Test

class RiderNotificationPolicyTest {
    @Test fun replayAfterProcessRestoreDoesNotAlertAgain() {
        val key = riderOfferAlertKey("rider-a", "reference-a", 123L)
        val saved = riderAlertReceipts(emptyList(), key)!!
        assertNull(riderAlertReceipts(saved.toList(), key))
        assertNotNull(riderAlertReceipts(saved, riderOfferAlertKey("rider-b", "reference-a", 123L)))
        assertNotNull(riderAlertReceipts(saved, riderOfferAlertKey("rider-a", "reference-a", 456L)))
    }

    @Test fun receiptHistoryIsBoundedWithoutDroppingTheNewestEvent() {
        val history = (0 until 256).map { "event-$it" }
        val next = riderAlertReceipts(history, "new-event")!!
        assertEquals(256, next.size)
        assertEquals("new-event", next.last())
        assertNull(riderAlertReceipts(next, "new-event"))
    }

    @Test fun equivalentServerTimezoneFormsShareAnExpiryKey() {
        assertEquals(riderOfferExpiryMillis("2026-10-12T06:00:30Z"), riderOfferExpiryMillis("2026-10-12T13:00:30+07:00"))
        assertNull(riderOfferExpiryMillis("not-an-expiry"))
        assertNull(riderOfferExpiryMillis(null))
    }
}
