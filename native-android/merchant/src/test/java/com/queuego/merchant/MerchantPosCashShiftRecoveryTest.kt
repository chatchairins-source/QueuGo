package com.queuego.merchant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantPosCashShiftRecoveryTest {
    @Test fun openingTimeoutIsConfirmedOnlyBySameServerOpenShiftAndExactFloat() {
        val open = PosCashShift(
            open = true,
            expectedCash = 100.0,
            openingFloat = 100.0,
            cashierName = "Cashier",
            id = "shift-1"
        )

        assertTrue(posOpenShiftMatches(open, 100.0))
        assertFalse(posOpenShiftMatches(open, 99.0))
        assertFalse(posOpenShiftMatches(open.copy(open = false), 100.0))
        assertFalse(posOpenShiftMatches(null, 100.0))
    }

    @Test fun closingTimeoutNeedsClosedServerHistoryForSameShiftAndExactCount() {
        val closedView = PosCashShift(
            open = false,
            expectedCash = null,
            openingFloat = null,
            cashierName = "Cashier"
        )
        val row = PosShiftHistory(
            id = "shift-1",
            cashierName = "Cashier",
            openedAt = "2026-10-28T10:00:00Z",
            closedAt = "2026-10-28T11:00:00Z",
            openingFloat = 100.0,
            expectedCash = 140.0,
            countedCash = 139.0,
            variance = -1.0
        )

        assertTrue(posCloseShiftMatches(closedView, listOf(row), "shift-1", 139.0))
        assertFalse(posCloseShiftMatches(closedView, listOf(row), "shift-1", 140.0))
        assertFalse(posCloseShiftMatches(closedView, listOf(row), "shift-2", 139.0))
        assertFalse(posCloseShiftMatches(closedView, listOf(row.copy(closedAt = null)), "shift-1", 139.0))
        assertFalse(posCloseShiftMatches(closedView.copy(open = true), listOf(row), "shift-1", 139.0))
        assertFalse(posCloseShiftMatches(null, listOf(row), "shift-1", 139.0))
    }
}