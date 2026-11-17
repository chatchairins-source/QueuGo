package com.queuego.merchant

import org.json.JSONObject
import org.junit.Assert.assertFalse
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
