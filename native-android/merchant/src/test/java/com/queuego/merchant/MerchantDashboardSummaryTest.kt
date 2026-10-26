package com.queuego.merchant

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantDashboardSummaryTest {
    private val today = LocalDate.parse("2026-10-26")
    private val zone = ZoneId.of("Asia/Bangkok")

    @Test fun matchesWebLocalDayAndOrderStatusGroups() {
        val orders = listOf(
            order("shop_accepted", "2026-10-25T17:30:00Z"),
            order("preparing", "2026-10-26T12:00:00+07:00"),
            order("completed", "2026-10-26T14:00:00+07:00"),
            order("delivered", "2026-10-26T15:00:00+07:00"),
            order("cancelled", "2026-10-26T16:00:00+07:00"),
            order("pending_shop", "2026-10-25T16:59:00Z")
        )

        assertEquals(MerchantDashboardSummary(5, 2, 2, 1), merchantDashboardSummary(orders, today, zone))
    }

    @Test fun ordersWithoutUsableTimestampAreNotCountedAsToday() {
        assertEquals(
            MerchantDashboardSummary(0, 0, 0, 0),
            merchantDashboardSummary(listOf(order("completed", null)), today, zone)
        )
    }

    @Test fun dateLabelUsesWebThaiCalendarYear() {
        assertEquals("26 ต.ค. 2569", merchantDashboardDateLabel(today))
    }

    @Test fun orderStatusFiltersMatchWebBuckets() {
        assertEquals("pending", merchantOrderFilterBucket("pending_shop"))
        assertEquals("preparing", merchantOrderFilterBucket("shop_accepted"))
        assertEquals("preparing", merchantOrderFilterBucket("rider_to_shop"))
        assertEquals("searching", merchantOrderFilterBucket("searching_rider"))
        assertEquals("ready", merchantOrderFilterBucket("waiting_rider"))
        assertEquals("delivering", merchantOrderFilterBucket("delivering"))
        assertEquals("delivering", merchantOrderFilterBucket("rider_to_customer", "2026-10-26T10:00:00Z"))
        assertEquals("done", merchantOrderFilterBucket("delivered"))
        assertEquals("cancel", merchantOrderFilterBucket("no_rider_available"))
    }

    private fun order(status: String, createdAt: String?) = MerchantOrder(
        id = status + (createdAt ?: "unknown"),
        number = "QG-1",
        status = status,
        subtotal = 0.0,
        deliveryFee = 0.0,
        total = 0.0,
        note = null,
        createdAt = createdAt,
        preparingAt = null,
        readyAt = null,
        customerName = "สมชาย"
    )

    @Test fun orderProgressMatchesWebStagesAndHidesTerminalFailures() {
        assertEquals(0, merchantOrderProgressStage("pending"))
        assertEquals(1, merchantOrderProgressStage("searching_rider"))
        assertEquals(2, merchantOrderProgressStage("preparing"))
        assertEquals(3, merchantOrderProgressStage("picked_up"))
        assertEquals(4, merchantOrderProgressStage("completed"))
        assertTrue(merchantOrderShowsProgress("completed"))
        assertFalse(merchantOrderShowsProgress("cancelled"))
        assertFalse(merchantOrderShowsProgress("no_rider_available"))
    }

    @Test fun orderSearchMatchesWebOrderNumberAndCustomerName() {
        val sample = order("preparing", "2026-10-26T10:00:00+07:00").copy(number = "QT-1234")
        assertEquals(true, merchantOrderSearchMatches(sample, "qt-12"))
        assertEquals(true, merchantOrderSearchMatches(sample, "สมชาย"))
        assertEquals(false, merchantOrderSearchMatches(sample, "unmatched"))
    }
}
