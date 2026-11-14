package com.queuego.rider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// QueueGo Rider exact-head unified safety/message release gate.
class RiderNotificationCenterTest {
    @Test fun parserKeepsRealRowsAndUnreadState() {
        val rows = riderNotificationsFromRows(
            JSONArray()
                .put(
                    JSONObject()
                        .put("id", "11111111-1111-1111-1111-111111111111")
                        .put("title", "มีงานใหม่")
                        .put("message", "กรุณาตอบรับงาน")
                        .put("type", "rider_offer")
                        .put("reference_id", "22222222-2222-2222-2222-222222222222")
                        .put("is_read", false)
                        .put("created_at", "2026-10-14T05:00:00Z")
                )
        )

        assertEquals(1, rows.size)
        assertFalse(rows.single().isRead)
        assertTrue(riderNotificationCanOpenOrder(rows.single()))
        assertTrue(riderNotificationTimeLabel(rows.single().createdAt)?.contains("12:00") == true)
    }

    @Test fun nonOrderNotificationNeverInventsNavigation() {
        val row = RiderNotificationRow(
            id = "11111111-1111-1111-1111-111111111111",
            title = "QueueGo",
            message = "ประกาศระบบ",
            type = "system",
            referenceId = "22222222-2222-2222-2222-222222222222",
            isRead = false,
            createdAt = null
        )
        assertFalse(riderNotificationCanOpenOrder(row))
        assertNull(riderNotificationTimeLabel(null))
    }

    @Test fun invalidOrderReferenceFailsClosed() {
        val row = RiderNotificationRow(
            id = "11111111-1111-1111-1111-111111111111",
            title = "งาน",
            message = "รายละเอียด",
            type = "order",
            referenceId = "not-a-uuid",
            isRead = true,
            createdAt = null
        )
        assertFalse(riderNotificationCanOpenOrder(row))
    }

    @Test fun unreadBadgeIsBoundedForBottomNavigation() {
        assertEquals(0, riderNotificationBadgeCount(0))
        assertEquals(12, riderNotificationBadgeCount(12))
        assertEquals(99, riderNotificationBadgeCount(100))
        assertEquals(99, riderNotificationBadgeCount(999))
    }
}
