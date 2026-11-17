package com.queuego.merchant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantPosTaxReadinessTest {
    private fun bill(
        enabled: Boolean,
        base: Double? = null,
        tax: Double? = null,
        rate: Double? = null
    ) = PosBill(
        id = "test-bill",
        number = "POS-TEST",
        type = "TAKEAWAY",
        tableId = null,
        kitchenStatus = "NEW",
        paymentStatus = "UNPAID",
        total = 107.0,
        discount = 0.0,
        taxEnabledSnapshot = enabled,
        taxRateSnapshot = rate,
        taxableBase = base,
        taxAmount = tax
    )

    @Test fun legacyBillWithoutTaxSnapshotIsStillPayable() {
        assertTrue(posBillTaxSnapshotComplete(bill(enabled = false)))
    }

    @Test fun newTaxBillRequiresAllServerTaxFields() {
        assertFalse(posBillTaxSnapshotComplete(bill(enabled = true)))
        assertFalse(posBillTaxSnapshotComplete(bill(enabled = true, base = 100.0, tax = 7.0)))
        assertTrue(posBillTaxSnapshotComplete(bill(enabled = true, base = 100.0, tax = 7.0, rate = 7.0)))
    }

    @Test fun taxBillRejectsInvalidMoneySnapshots() {
        assertFalse(posBillTaxSnapshotComplete(bill(true, base = -1.0, tax = 7.0, rate = 7.0)))
        assertFalse(posBillTaxSnapshotComplete(bill(true, base = 100.0, tax = Double.NaN, rate = 7.0)))
        assertFalse(posBillTaxSnapshotComplete(bill(true, base = 100.0, tax = -1.0, rate = 7.0)))
    }

    @Test fun missingBackendExplainsWhyTaxCannotBeSaved() {
        val message = posTaxSettingsError(
            IllegalStateException("Could not find the table 'public.pos_tax_settings' in the schema cache")
        )
        assertTrue(message.contains("ยังไม่ได้ติดตั้งระบบภาษี POS"))
    }

    @Test fun permissionDenialIsNotMisrepresentedAsMissingMigration() {
        val message = posTaxSettingsError(
            IllegalStateException("permission denied for table pos_tax_settings")
        )
        assertTrue(message.contains("ไม่มีสิทธิ์"))
    }
}
