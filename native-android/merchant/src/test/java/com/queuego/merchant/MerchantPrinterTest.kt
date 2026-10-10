package com.queuego.merchant

import org.junit.Assert.*
import org.junit.Test

class MerchantPrinterTest {
    private val bill = PosBill("bill", "QT-0123", "DINE_IN", "table", "SENT_TO_KITCHEN", "UNPAID", 90.0, 10.0, subtotal = 100.0)
    private fun snapshot(bill: PosBill = this.bill): PosSnapshot = PosSnapshot(
        "shop", "ร้านทดสอบ", true, null, emptyList(), emptyList(),
        listOf(PosTable("table", "โต๊ะ 7", true)), listOf(bill),
        mapOf(bill.id to listOf(
            PosLine("p1", "ข้าวผัด", "ไม่เผ็ด", 1, 50.0, batch = 1),
            PosLine("p2", "น้ำ", null, 2, 40.0, batch = 2)
        )), emptyList(), emptyMap()
    )
    @Test fun kitchenTicketKeepsQueuedBatchSeparateFromLaterAdditions() {
        val snap = snapshot()
        val first = merchantKitchenTicket(snap, bill, 1)
        assertTrue(first.contains("ข้าวผัด"))
        assertTrue(first.contains("ไม่เผ็ด"))
        assertFalse(first.contains("2 x น้ำ"))
        val latest = merchantKitchenTicket(snap, bill)
        assertTrue(latest.contains("2 x น้ำ"))
        assertFalse(latest.contains("ข้าวผัด"))
        assertTrue(latest.contains("โต๊ะ 7"))
        assertEquals("kitchen:shop:bill:2", merchantKitchenPrintKey(snap, bill))
    }
    @Test fun receiptIncludesDiscountCashAndChangeAndRefusesUnpaidReceipt() {
        assertNull(merchantReceiptPrintKey(snapshot(), bill))
        val paid = bill.copy(paymentStatus = "PAID", paymentMethod = "cash", cashTendered = 100.0, cashChange = 10.0)
        val text = merchantReceiptTicket(snapshot(paid), paid)
        assertEquals("receipt:shop:bill", merchantReceiptPrintKey(snapshot(paid), paid))
        assertTrue(text.contains("QT-0123"))
        assertTrue(text.contains("-฿10.00"))
        assertTrue(text.contains("ยอดสุทธิ     ฿90.00"))
        assertTrue(text.contains("เงินทอน      ฿10.00"))
        val refunded = paid.copy(paymentStatus = "REFUNDED", status = "cancelled")
        assertNull(merchantReceiptPrintKey(snapshot(refunded), refunded))
        assertNull(merchantKitchenPrintKey(snapshot(refunded), refunded))
    }
    @Test fun bridgeSettingsRejectCleartextCredentialsAndFragments() {
        for (url in listOf("http://192.168.1.2/print", "https://user:pass@printer.example/print", "https://printer.example/print#x", "https:/print")) {
            assertThrows(IllegalArgumentException::class.java) { validateMerchantPrintBridgeUrl(url) }
        }
        assertEquals(80, MerchantPrinterSettings().widthMm)
        assertEquals("https://printer.example/print", MerchantPrinterSettings(80, " https://printer.example/print/ ").validated().bridgeUrl)
        assertThrows(IllegalArgumentException::class.java) { MerchantPrinterSettings(57).validated() }
    }
    @Test fun staffExplicitFalseAndInactiveStayDenied() {
        val cashier = PosStaff("staff", "พนักงาน", "CASHIER", emptySet(), true, setOf("close_bill"))
        assertFalse(cashier.allows("close_bill"))
        assertFalse(cashier.copy(active = false, permissions = setOf("refund")).allows("refund"))
    }
}
