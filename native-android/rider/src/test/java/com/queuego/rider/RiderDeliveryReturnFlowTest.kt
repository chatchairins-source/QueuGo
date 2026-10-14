package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderDeliveryReturnFlowTest {
    private fun state(status: String) = RiderDeliveryReturn(
        caseId = "11111111-1111-1111-1111-111111111111",
        orderId = "22222222-2222-2222-2222-222222222222",
        orderNumber = "QT-1234",
        marketOrderId = null,
        status = status,
        reason = "customer_unreachable",
        adminNote = null,
        shopId = "33333333-3333-3333-3333-333333333333",
        shopName = "ร้านทดสอบ",
        shopAddress = "บุรีรัมย์",
        shopLatitude = 14.9,
        shopLongitude = 103.1,
        expectedRefundAmount = 120.0,
        returnStopCount = 1,
        returnedStopCount = 0,
        remainingStopCount = 1,
        stopId = null,
        stopOrderId = null,
        stopSequence = 1,
        stopRefundAmount = 120.0,
        ticketId = null,
        reportedAt = null,
        returnRequiredAt = null,
        startedReturnAt = null
    )

    @Test fun reportedCaseWaitsForSupportInsteadOfPretendingReturnIsApproved() {
        val reported = state("reported")
        assertTrue(reported.waitingForSupport)
        assertFalse(reported.canReturnToShop)
    }

    @Test fun approvedAndReturningCasesExposeReturnFlow() {
        assertTrue(state("return_required").canReturnToShop)
        assertTrue(state("returning").canReturnToShop)
        assertFalse(state("returned").canReturnToShop)
    }

    @Test fun multiStopReturnReportsCurrentAndRemainingStops() {
        val multi = state("returning").copy(
            marketOrderId = "44444444-4444-4444-4444-444444444444",
            returnStopCount = 3,
            returnedStopCount = 1,
            remainingStopCount = 2,
            stopId = "55555555-5555-5555-5555-555555555555",
            stopSequence = 2,
            stopRefundAmount = 80.0
        )
        assertTrue(multi.multiStop)
        assertEquals(2, multi.currentStopNumber)
        assertEquals(2, multi.remainingStopCount)
        assertEquals(80.0, multi.stopRefundAmount, 0.001)
    }

    @Test fun merchantRefundOffsetsCashPreviouslyPaidToMerchant() {
        val row = RiderCashLedgerEntry(
            orderId = "order",
            orderNumber = "QT-1234",
            status = "cancelled",
            deliveryFee = 25.0,
            cashPaidMerchant = 120.0,
            cashCollectedCustomer = 0.0,
            createdAt = null,
            cashRefundedMerchant = 120.0
        )
        assertEquals(0.0, riderHistoryCashFlow(row), 0.001)
    }

    @Test fun returnReasonCopyIsOperationallyClear() {
        assertEquals("ติดต่อลูกค้าไม่ได้", riderDeliveryReturnReasonLabel("customer_unreachable"))
        assertEquals("ลูกค้าปฏิเสธชำระเงิน", riderDeliveryReturnReasonLabel("recipient_refuses_cash"))
        assertEquals("ปัญหาการจัดส่ง", riderDeliveryReturnReasonLabel("unknown"))
    }
}
