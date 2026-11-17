package com.queuego.merchant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exact-server-receipt checks for an uncertain POS payment response.
 * These synthetic test records are never written to a shop or Production.
 */
class MerchantPosPaymentReceiptTest {
    private fun paid(
        id: String = "bill-a",
        paymentStatus: String = "PAID",
        billStatus: String = "CLOSED",
        method: String = "cash",
        tender: Double = 500.0
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("payment_status", paymentStatus)
        .put("bill_status", billStatus)
        .put("payment_method", method)
        .put("cash_tendered", tender)

    private fun confirmedCash(
        paymentStatus: String = "PAID",
        billStatus: String = "CLOSED",
        method: String = "cash",
        tender: Double? = 200.0,
        change: Double? = 50.0,
        total: Double = 150.0
    ): PosBill = PosBill(
        id = "bill-a", number = "POS-001", type = "TAKEAWAY",
        tableId = null, kitchenStatus = "NEW",
        paymentStatus = paymentStatus, total = total, discount = 0.0,
        paymentMethod = method, billStatus = billStatus,
        cashTendered = tender, cashChange = change
    )

    @Test fun confirmedChangeUsesServerReceiptAndNeverAnUnpaidBill() {
        assertEquals(50.0, posConfirmedCashChange(confirmedCash())!!, 0.0001)
        assertEquals(60.0, posConfirmedCashChange(confirmedCash(change = null, tender = 210.0))!!, 0.0001)
        assertNull(posConfirmedCashChange(confirmedCash(paymentStatus = "UNPAID")))
        assertNull(posConfirmedCashChange(confirmedCash(billStatus = "OPEN")))
        assertNull(posConfirmedCashChange(confirmedCash(method = "card")))
        assertNull(posConfirmedCashChange(confirmedCash(tender = null, change = null)))
        assertNull(posConfirmedCashChange(confirmedCash(change = -10.0)))
        assertNull(posConfirmedCashChange(confirmedCash(change = Double.NaN)))
    }

    @Test fun acceptsOnlySameBillWithPaidAndClosedTerminalState() {
        val receipt = paid()
        assertTrue(posPaymentReceiptMatches(receipt, "bill-a", "cash", 500.0))
        assertFalse(posPaymentReceiptMatches(null, "bill-a", "cash", 500.0))
        assertFalse(posPaymentReceiptMatches(receipt, "bill-b", "cash", 500.0))
        assertFalse(posPaymentReceiptMatches(paid(paymentStatus = "UNPAID"), "bill-a", "cash", 500.0))
        assertFalse(posPaymentReceiptMatches(paid(billStatus = "OPEN"), "bill-a", "cash", 500.0))
        assertFalse(posPaymentReceiptMatches(paid(method = "card"), "bill-a", "cash", 500.0))
    }

    @Test fun cashConfirmationRequiresSameFiniteTender() {
        val receipt = paid()
        assertFalse(posPaymentReceiptMatches(receipt, "bill-a", "cash", null))
        assertFalse(posPaymentReceiptMatches(receipt, "bill-a", "cash", 499.0))
        assertFalse(posPaymentReceiptMatches(receipt, "bill-a", "cash", Double.NaN))
        assertFalse(posPaymentReceiptMatches(receipt, "bill-a", "cash", Double.POSITIVE_INFINITY))
        assertFalse(posPaymentReceiptMatches(paid().put("cash_tendered", JSONObject.NULL), "bill-a", "cash", 500.0))
    }

    @Test fun nonCashStillRequiresMatchingMethodAndTerminalState() {
        val cardReceipt = paid(method = "card")
        assertTrue(posPaymentReceiptMatches(cardReceipt, "bill-a", "card", null))
        assertFalse(posPaymentReceiptMatches(cardReceipt, "bill-a", "promptpay", null))
        assertFalse(posPaymentReceiptMatches(paid(method = "card", paymentStatus = "UNPAID"), "bill-a", "card", null))
        assertFalse(posPaymentReceiptMatches(paid(method = "card", billStatus = "OPEN"), "bill-a", "card", null))
    }
}
