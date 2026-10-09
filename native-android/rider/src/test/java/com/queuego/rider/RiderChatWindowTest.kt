package com.queuego.rider

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class RiderChatWindowTest {
    private val delivered = "2026-10-09T06:00:00Z"
    private val at = Instant.parse(delivered).toEpochMilli()

    @Test fun completedRoomClosesAtExactThirtyMinuteBoundary() {
        val window = riderChatWindow("completed", delivered, "2026-10-09T06:10:00Z")
        assertTrue(window.isOpen(at + 30 * 60_000L - 1))
        assertFalse(window.isOpen(at + 30 * 60_000L))
        assertFalse(window.isOpen(at + 31 * 60_000L))
    }

    @Test fun deliveryTimestampTakesPrecedenceAndFallbackMatchesWeb() {
        assertEquals(at + 30 * 60_000L, riderChatWindow("completed", delivered, "2026-10-09T07:00:00Z").deadline)
        assertEquals(at + 30 * 60_000L, riderChatWindow("completed", null, delivered).deadline)
        assertFalse(riderChatWindow("completed", "invalid", delivered).isOpen(at))
        assertFalse(riderChatWindow("completed", null, null).isOpen(at))
    }

    @Test fun ActiveRoomHasNoDeadlineButCancelledRoomNeverOpens() {
        val active = riderChatWindow("in_progress", null, null)
        assertNull(active.deadline)
        assertTrue(active.isOpen(at + 90 * 60_000L))
        assertFalse(riderChatWindow("cancelled", delivered, delivered).isOpen(at))
    }
}
