package com.queuego.merchant

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
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
        readyAt = null
    )
}
