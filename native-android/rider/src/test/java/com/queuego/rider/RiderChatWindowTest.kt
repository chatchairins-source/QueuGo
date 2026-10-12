package com.queuego.rider

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RiderChatWindowTest {
    private val delivered = "2026-10-09T06:00:00Z"
    private val at = Instant.parse(delivered).toEpochMilli()

    @Test fun callingStopsWhenOpenChatRefreshesToTerminalOrder() {
        assertTrue(riderChatCanCall("in_progress", null))
        assertTrue(riderChatCanCall("in_progress", "arrival"))
        for (status in listOf("completed", "cancelled", "no_rider_available")) {
            assertFalse(riderChatCanCall("in_progress", status))
            assertFalse(riderChatCanCall(status, null))
            assertFalse(riderChatCanCall("in_progress", status.uppercase(java.util.Locale.ROOT)))
        }
        // The post-delivery chat window stays available, but voice calling must stop.
        assertTrue(riderChatWindow("completed", delivered, delivered).isOpen(at + 60_000))
        assertFalse(riderChatCanCall("in_progress", "completed"))
    }

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

    @Test fun chatTimestampIsRenderedAsCompactClockTime() {
        assertEquals("06:07", riderChatTimeLabel("2026-10-09T06:07:00Z", ZoneId.of("UTC")))
        assertEquals("", riderChatTimeLabel("not-a-time", ZoneId.of("UTC")))
        assertEquals("", riderChatTimeLabel(null, ZoneId.of("UTC")))
    }
}
