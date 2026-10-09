package com.queuego.customer

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class CustomerChatWindowTest {
    private val delivered = "2026-10-09T07:00:00Z"
    private val at = Instant.parse(delivered).toEpochMilli()
    private val active = CustomerOrder("local-unit-order", "QT-1234", null, "in_progress", 0.0, 0.0, 0.0, null, null, riderId = "local-unit-rider")

    @Test fun completedRoomClosesAtExactThirtyMinuteBoundary() {
        val completed = active.copy(status = "completed", completedAt = delivered)
        assertTrue(completed.chatAvailable(at + 30 * 60_000L - 1))
        assertFalse(completed.chatAvailable(at + 30 * 60_000L))
    }
    @Test fun cancelledUnavailableOrUnassignedRoomsNeverOpen() {
        assertFalse(active.copy(status = "cancelled").chatAvailable(at))
        assertFalse(active.copy(status = "no_rider_available").chatAvailable(at))
        assertFalse(active.copy(riderId = null).chatAvailable(at))
        assertTrue(active.chatAvailable(at))
    }
    @Test fun missingOrInvalidCompletionClosesAndExistingFallbackWorks() {
        assertFalse(active.copy(status = "completed").chatAvailable(at))
        assertFalse(active.copy(status = "completed", completedAt = "invalid", updatedAt = delivered).chatAvailable(at))
        assertTrue(active.copy(status = "completed", updatedAt = delivered).chatAvailable(at + 1))
    }
}
