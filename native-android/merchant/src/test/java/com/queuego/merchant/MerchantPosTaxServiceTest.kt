package com.queuego.merchant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantPosTaxServiceTest {
    private fun bill(
        taxEnabled: Boolean,
        base: Double? = null,
        tax: Double? = null,
        rate: Double? = null
    ) = PosBill(
        id = "non-production-test-bill",
        number = "TEST",
        type = "TAKEAWAY",
        tableId = null,
        kitchenStatus = "NEW",
        paymentStatus = "UNPAID",
        total = 107.0,
        discount = 0.0,
        taxEnabledSnapshot = taxEnabled,
        taxRateSnapshot = rate,
        taxableBase = base,
        taxAmount = tax
    )

    @Test fun legacyPosBillWithoutTaxIsStillPayable() {
        assertTrue(posBillReadyForPayment(bill(taxEnabled = false)))
    }

    @Test fun taxEnabledBillCannotBePaidBeforeAuthoritativeSnapshotArrives() {
        assertFalse(posBillReadyForPayment(bill(taxEnabled = true)))
        assertFalse(posBillReadyForPayment(bill(taxEnabled = true, base = 100.0, tax = 7.0)))
        assertTrue(posBillReadyForPayment(bill(taxEnabled = true, base = 100.0, tax = 7.0, rate = 7.0)))
    }

    @Test fun taxEnabledBillRejectsInvalidServerTaxNumbers() {
        assertFalse(posBillReadyForPayment(bill(true, base = -1.0, tax = 7.0, rate = 7.0)))
        assertFalse(posBillReadyForPayment(bill(true, base = 100.0, tax = Double.NaN, rate = 7.0)))
        assertFalse(posBillReadyForPayment(bill(true, base = 100.0, tax = 7.0, rate = -1.0)))
        assertFalse(posBillReadyForPayment(bill(true, base = 100.0, tax = 7.0, rate = 101.0)))
    }

    @Test fun missingBackendTaxSettingsReturnActionableError() {
        val actual = posTaxSettingsServiceError(
            IllegalStateException("Could not find public.pos_tax_settings in the schema cache")
        )
        assertTrue(actual.contains("เซิร์ฟเวอร์ยังไม่มีระบบภาษี POS"))
    }

    @Test fun taxPermissionDeniedDoesNotSuggestInstallingMigration() {
        val actual = posTaxSettingsServiceError(
            IllegalStateException("permission denied for pos_tax_settings")
        )
        assertTrue(actual.contains("ไม่มีสิทธิ์"))
        assertFalse(actual.contains("ติดตั้ง"))
    }
}
