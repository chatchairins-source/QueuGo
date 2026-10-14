package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderJobHistoryDetailTest {
    @Test fun cashFlowIsCashCollectedMinusMerchantPayment() {
        val row = RiderCashLedgerEntry(
            orderId = "order-1",
            orderNumber = "QT-1234",
            status = "completed",
            deliveryFee = 45.0,
            cashPaidMerchant = 120.0,
            cashCollectedCustomer = 180.0,
            createdAt = "2026-10-14T05:00:00Z"
        )
        assertEquals(60.0, riderHistoryCashFlow(row), 0.0001)
        assertEquals("งานส่งสำเร็จ", riderHistoryStatusLabel("completed"))
    }

    @Test fun completionTimeUsesBangkokTimezoneAndRejectsGarbage() {
        val label = riderHistoryCompletedLabel("2026-10-14T05:00:00Z")
        assertTrue(label?.contains("12:00") == true)
        assertNull(riderHistoryCompletedLabel("not-a-time"))
        assertNull(riderHistoryCompletedLabel(null))
    }
}
