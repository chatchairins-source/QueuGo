package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiderMessageBadgeTest {
    private fun message(id: String, order: String = "active", sender: String = "customer", at: Long = 100L) =
        BadgeMessage(id, order, sender, "hello", at)

    @Test fun activeBaselineDuplicateOwnMessageAndOpenRoom() {
        val tracker = RiderMessageBadgeTracker("rider")
        fun update(rows: List<BadgeMessage>, open: String? = null) =
            tracker.update("active", open, false, emptyList(), rows, 0, 1000)
        assertEquals(0, update(listOf(message("baseline"))).first)
        assertEquals(1, update(listOf(message("new", at = 200))).first)
        assertEquals(1, update(listOf(message("new", at = 200))).first)
        assertEquals(1, update(listOf(message("own", sender = "rider", at = 300))).first)
        assertEquals(0, update(listOf(message("own", sender = "rider", at = 300)), "active").first)
        assertNull(update(listOf(message("own", sender = "rider", at = 300))).second)
    }

    @Test fun completedOnlyCustomerAfterDeliveryAfterSeenAndWithinWindow() {
        val rows = listOf(message("before", "done", at = 99), message("own", "done", "rider", 120),
            message("seen", "done", at = 130), message("new", "done", at = 160),
            message("late", "done", at = 1_800_100), message("other", "unowned", at = 170))
        val rooms = listOf(BadgeRoom("done", "customer", 100))
        val tracker = RiderMessageBadgeTracker("rider")
        assertEquals(1, tracker.update(null, null, false, rooms, rows, 150, 200).first)
        assertEquals(0, tracker.update(null, null, true, rooms, rows, 150, 200).first)
        assertEquals(0, tracker.update(null, null, false, rooms, rows, 150, 1_800_100).first)
    }

    @Test fun changingOrderAndAccountNeverCarriesActiveUnread() {
        val tracker = RiderMessageBadgeTracker("rider")
        tracker.update("active", null, false, emptyList(), emptyList(), 0, 1000)
        assertEquals(1, tracker.update("active", null, false, emptyList(), listOf(message("new")), 0, 1000).first)
        assertEquals(0, tracker.update("next", null, false, emptyList(), listOf(message("other", "next")), 0, 1000).first)
        assertEquals(0, RiderMessageBadgeTracker("second-user").update("active", null, false,
            emptyList(), listOf(message("new")), 0, 1000).first)
    }
}
