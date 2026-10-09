package com.queuego.rider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RiderChatDeliveryTest {
    @Test fun lostInsertResponseUsesConfirmedRowRatherThanDuplicating() = runBlocking {
        var committed = false
        var inserts = 0
        sendRiderChatOnce(exists = { committed }, insert = {
            inserts++
            committed = true
            throw IllegalStateException("response interrupted")
        })
        sendRiderChatOnce(exists = { committed }, insert = { inserts++; true })
        assertEquals(1, inserts)
    }

    @Test fun confirmedInsertDoesNotDependOnAnotherRead() = runBlocking {
        var reads = 0
        sendRiderChatOnce(exists = { reads++; false }, insert = { true })
        assertEquals(1, reads)
    }

    @Test fun missingConfirmationFailsAndRetainsRetryId() = runBlocking {
        val outbox = RiderChatOutbox()
        val id = outbox.requestId("ข้อความ")
        try {
            sendRiderChatOnce(exists = { false }, insert = { false })
            fail("Unconfirmed insertion must not be shown as successful")
        } catch (_: IllegalStateException) { }
        assertEquals(id, outbox.requestId("ข้อความ"))
        outbox.confirmed("ข้อความ")
        assertNotEquals(id, outbox.requestId("ข้อความ"))
    }

    @Test fun cancellationIsPropagatedWithoutRecoveryRead() = runBlocking {
        var reads = 0
        try {
            sendRiderChatOnce(exists = { reads++; false }, insert = { throw CancellationException("room closed") })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(1, reads)
    }

    @Test fun textLimitRemainsAndImageProtocolIsSeparate() {
        assertEquals("ข้อความ", validateRiderChatPayload(" ข้อความ "))
        assertEquals(500, validateRiderChatPayload("ก".repeat(500)).length)
        assertEquals("__IMG__data:image/jpeg;base64,YQ==", validateRiderChatPayload("__IMG__data:image/jpeg;base64,YQ=="))
        for (invalid in listOf("", "ก".repeat(501), "__IMG__data:image/jpeg;base64,", "__IMG__https://example.invalid/image")) {
            try { validateRiderChatPayload(invalid); fail("Invalid payload accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
