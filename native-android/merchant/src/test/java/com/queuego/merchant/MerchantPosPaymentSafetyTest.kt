package com.queuego.merchant

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantPosPaymentSafetyTest {
    private fun paidBill(
        id: String = "bill-1",
        paymentStatus: String = "PAID",
        billStatus: String = "CLOSED",
        method: String = "cash",
        tender: Double = 120.0
    ) = JSONObject()
        .put("id", id)
        .put("payment_status", paymentStatus)
        .put("bill_status", billStatus)
        .put("payment_method", method)
        .put("cash_tendered", tender)

    @Test fun cashReceiptRequiresServerPaidClosedBillAndExactTender() {
        assertTrue(posPaymentReceiptMatches(paidBill(), "bill-1", "cash", 120.0))
        assertFalse(posPaymentReceiptMatches(paidBill(paymentStatus = "UNPAID"), "bill-1", "cash", 120.0))
        assertFalse(posPaymentReceiptMatches(paidBill(billStatus = "OPEN"), "bill-1", "cash", 120.0))
        assertFalse(posPaymentReceiptMatches(paidBill(tender = 100.0), "bill-1", "cash", 120.0))
        assertFalse(posPaymentReceiptMatches(paidBill(), "bill-other", "cash", 120.0))
        assertFalse(posPaymentReceiptMatches(paidBill(), "bill-1", "cash", null))
    }

    @Test fun nonCashReceiptStillRequiresMatchingServerBillAndMethod() {
        assertTrue(posPaymentReceiptMatches(paidBill(method = "card"), "bill-1", "card", null))
        assertFalse(posPaymentReceiptMatches(paidBill(method = "cash"), "bill-1", "card", null))
        assertFalse(posPaymentReceiptMatches(paidBill(paymentStatus = "UNPAID", method = "card"), "bill-1", "card", null))
    }
}
