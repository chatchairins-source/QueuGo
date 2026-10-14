package com.queuego.rider

import org.junit.Assert.*
import org.junit.Test

class RiderPickupIssueTest {
    @Test fun allOperationalReasonsHaveDistinctStructuredKeys() {
        assertEquals(7, riderPickupIssueReasons.size)
        assertEquals(7, riderPickupIssueReasons.map { it.key }.toSet().size)
        assertTrue(riderPickupIssueReasons.all { it.category in setOf("merchant", "missing_item", "cash") })
    }

    @Test fun reportBindsQtOrderAndExactMarketStop() {
        val details = riderPickupIssueDetails("QT-1234", riderPickupIssueReasons[0], " ร้านไม่รับสาย ", "stop-1")
        assertTrue(details.contains("[PICKUP_ISSUE:merchant_unreachable]"))
        assertTrue(details.contains("QT-1234"))
        assertTrue(details.contains("[PICKUP_STOP:stop-1]"))
    }

    @Test fun retryIdentitySurvivesRecreationButCannotCrossActorOrderOrReason() {
        val key = riderPickupIssueRequestId("rider-1", "order-1", "report")
        assertEquals(key, riderPickupIssueRequestId("rider-1", "order-1", "report"))
        assertNotEquals(key, riderPickupIssueRequestId("rider-2", "order-1", "report"))
        assertNotEquals(key, riderPickupIssueRequestId("rider-1", "order-2", "report"))
        assertNotEquals(key, riderPickupIssueRequestId("rider-1", "order-1", "other report"))
    }

    @Test fun detailsRespectSupportServerLimit() {
        val details = riderPickupIssueDetails("QT-1234", riderPickupIssueReasons[5], "a".repeat(5000), "stop-1")
        assertTrue(details.length < 2000)
        assertTrue(details.length >= 1200)
    }
}
