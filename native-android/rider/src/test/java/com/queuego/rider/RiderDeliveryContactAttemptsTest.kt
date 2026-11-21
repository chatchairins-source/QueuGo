package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderDeliveryContactAttemptsTest {
    @Test fun realCallAndChatActionsIncrementSeparateCounters() {
        val afterCall = riderRecordDeliveryContactAttempt(
            RiderDeliveryContactAttempts(),
            RiderDeliveryContactChannel.CALL,
            1_000L
        )
        val afterChat = riderRecordDeliveryContactAttempt(
            afterCall,
            RiderDeliveryContactChannel.CHAT,
            2_000L
        )

        assertEquals(1, afterChat.callCount)
        assertEquals(1, afterChat.chatCount)
        assertEquals(2, afterChat.totalCount)
        assertEquals(2_000L, afterChat.lastAttemptAtMs)
    }

    @Test fun contactAttemptsExpireInsteadOfAuthorizingFutureOrdersForever() {
        val attempts = RiderDeliveryContactAttempts(
            callCount = 1,
            lastAttemptAtMs = 1_000L
        )

        assertTrue(riderDeliveryContactAttemptsFresh(attempts, 1_001L, 10_000L))
        assertFalse(riderDeliveryContactAttemptsFresh(attempts, 20_000L, 10_000L))
    }

    @Test fun reportNoteCarriesMachineReadableAttemptEvidence() {
        val note = riderDeliveryIssueNoteWithAttempts(
            "ไม่มีคนรับสาย",
            RiderDeliveryContactAttempts(callCount = 2, chatCount = 1, lastAttemptAtMs = 1_000L)
        )

        assertTrue(note.startsWith("[CONTACT_ATTEMPTS:3;CALL:2;CHAT:1]"))
        assertTrue(note.contains("ไม่มีคนรับสาย"))
    }

    @Test fun reportNoteStaysWithinBackendNoteLimit() {
        val note = riderDeliveryIssueNoteWithAttempts(
            "x".repeat(5_000),
            RiderDeliveryContactAttempts(callCount = 99, chatCount = 99, lastAttemptAtMs = 1_000L)
        )

        assertTrue(note.length <= 1_200)
    }
    @Test fun countersAreBoundedForLocalEvidenceStorage() {
        val capped = riderRecordDeliveryContactAttempt(
            RiderDeliveryContactAttempts(callCount = 99, chatCount = 99, lastAttemptAtMs = 1_000L),
            RiderDeliveryContactChannel.CALL,
            2_000L
        )

        assertEquals(99, capped.callCount)
        assertEquals(99, capped.chatCount)
        assertEquals(198, capped.totalCount)
    }

}
