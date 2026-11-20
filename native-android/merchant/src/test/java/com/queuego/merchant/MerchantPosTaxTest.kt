package com.queuego.merchant

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantPosTaxTest {
    private fun serverTaxBill(
        enabled: Boolean = true,
        base: Double? = 100.0,
        tax: Double? = 7.0
    ): PosBill = PosBill(
        id = "order-test", number = "POS-TEST", type = "TAKEAWAY",
        tableId = null, kitchenStatus = "NEW",
        paymentStatus = "UNPAID", total = 107.0, discount = 0.0,
        taxEnabledSnapshot = enabled, taxableBase = base, taxAmount = tax
    )

    @Test fun taxSummaryRequiresAuthoritativeServerAmounts() {
        org.junit.Assert.assertTrue(posBillTaxSnapshotReady(serverTaxBill()))
        org.junit.Assert.assertFalse(posBillTaxSnapshotReady(serverTaxBill(enabled = false)))
        org.junit.Assert.assertFalse(posBillTaxSnapshotReady(serverTaxBill(base = null)))
        org.junit.Assert.assertFalse(posBillTaxSnapshotReady(serverTaxBill(tax = null)))
    }

    @Test fun taxSummaryMustReconcileWithAuthoritativeBillTotal() {
        // A one-satang rounding difference is tolerable, but never a material mismatch.
        org.junit.Assert.assertTrue(posBillTaxSnapshotReady(serverTaxBill(base = 100.0, tax = 7.0)))
        org.junit.Assert.assertTrue(posBillTaxSnapshotReady(serverTaxBill(base = 100.0, tax = 6.99)))
        org.junit.Assert.assertFalse(posBillTaxSnapshotReady(serverTaxBill(base = 100.0, tax = 6.97)))
        org.junit.Assert.assertFalse(posBillTaxSnapshotReady(serverTaxBill(base = 107.0, tax = 7.0)))
        org.junit.Assert.assertFalse(posBillTaxSnapshotReady(serverTaxBill(base = Double.POSITIVE_INFINITY)))
    }

    @Test fun taxSummaryRejectsUnusableServerNumbers() {
        org.junit.Assert.assertFalse(posBillTaxSnapshotReady(serverTaxBill(base = Double.NaN)))
        org.junit.Assert.assertFalse(posBillTaxSnapshotReady(serverTaxBill(tax = Double.POSITIVE_INFINITY)))
        org.junit.Assert.assertFalse(posBillTaxSnapshotReady(serverTaxBill(base = -1.0)))
        org.junit.Assert.assertFalse(posBillTaxSnapshotReady(serverTaxBill(tax = -0.01)))
    }

    @Test fun taxInclusivePriceExtractsTaxWithoutChangingCustomerTotal() {
        val result = calculatePosTaxBreakdown(10_700L, 7.0, true)

        assertEquals(10_000L, result.taxableBaseSatang)
        assertEquals(700L, result.taxSatang)
        assertEquals(10_700L, result.totalSatang)
    }

    @Test fun taxExclusivePriceAddsTaxToCustomerTotal() {
        val result = calculatePosTaxBreakdown(10_000L, 7.0, false)

        assertEquals(10_000L, result.taxableBaseSatang)
        assertEquals(700L, result.taxSatang)
        assertEquals(10_700L, result.totalSatang)
    }

    @Test fun zeroRateDoesNotChangeEitherPriceMode() {
        assertEquals(10_000L, calculatePosTaxBreakdown(10_000L, 0.0, true).totalSatang)
        assertEquals(10_000L, calculatePosTaxBreakdown(10_000L, 0.0, false).totalSatang)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeAmount() {
        calculatePosTaxBreakdown(-1L, 7.0, true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRangeTaxRate() {
        calculatePosTaxBreakdown(100L, 100.01, false)
    }

    @Test(expected = ArithmeticException::class)
    fun taxExclusiveTotalDetectsLongOverflow() {
        calculatePosTaxBreakdown(Long.MAX_VALUE, 100.0, false)
    }
}
