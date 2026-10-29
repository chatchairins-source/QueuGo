package com.queuego.merchant

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantPosTaxTest {
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
