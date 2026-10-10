package com.queuego.merchant

import org.junit.Assert.*
import org.junit.Test

class MerchantPrinterTest {
    private val bill = PosBill("bill", "QT-0123", "DINE_IN", "table", "SENT_TO_KITCHEN", "UNPAID", 90.0, 10.0, subtotal = 100.0, createdAt = "2026-10-10T04:00:00Z")
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
        assertTrue(text.contains("ส่วนลด 10.00 ฿"))
        assertTrue(text.contains("รวม 90.00 ฿"))
        assertTrue(text.contains("เงินทอน 10.00 ฿"))
        assertTrue(text.startsWith("QueueGo\nร้านทดสอบ\nใบเสร็จรับเงิน\n"))
        assertTrue(text.contains("10/10/2569"))
        assertTrue(text.contains("  ไม่เผ็ด"))
        assertTrue(text.endsWith("--------------------------------\n\n"))
        val refunded = paid.copy(paymentStatus = "REFUNDED", status = "cancelled")
        assertNull(merchantReceiptPrintKey(snapshot(refunded), refunded))
        assertNull(merchantKitchenPrintKey(snapshot(refunded), refunded))
    }
    @Test fun printHistoryRetainsTheMostRecentJobsAfterRestoreAndClockCorrection() {
        val old = (0..299).associate { "receipt:$it" to it.toLong() }
        val next = merchantPrintHistory(old, "receipt:new", 10L)
        assertEquals(300, next.size)
        assertFalse(next.containsKey("receipt:0"))
        assertTrue(next.containsKey("receipt:new"))
        assertEquals(300L, next["receipt:new"])
        val reprint = merchantPrintHistory(next, "receipt:1", 20L)
        assertEquals(300, reprint.size)
        assertEquals(301L, reprint["receipt:1"])
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
