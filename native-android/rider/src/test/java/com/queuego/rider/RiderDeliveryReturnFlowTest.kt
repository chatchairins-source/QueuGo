package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderDeliveryReturnFlowTest {
    private fun state(
        status: String,
        totalStops: Int = 1,
        returnedStops: Int = 0
    ) = RiderDeliveryReturn(
        caseId = "11111111-1111-1111-1111-111111111111",
        orderId = "22222222-2222-2222-2222-222222222222",
        orderNumber = "QT-1234",
        marketOrderId = if (totalStops > 1) "44444444-4444-4444-4444-444444444444" else null,
        status = status,
        reason = "customer_unreachable",
        adminNote = null,
        expectedRefundAmount = if (totalStops > 1) 300.0 else 120.0,
        returnStopCount = totalStops,
        returnedStopCount = returnedStops,
        remainingStopCount = (totalStops - returnedStops).coerceAtLeast(0),
        stopId = "55555555-5555-5555-5555-555555555555",
        stopOrderId = "66666666-6666-6666-6666-666666666666",
        stopSequence = returnedStops + 1,
        stopRefundAmount = if (totalStops > 1) 100.0 else 120.0,
        shopId = "33333333-3333-3333-3333-333333333333",
        shopName = "ร้านทดสอบ",
        shopAddress = "บุรีรัมย์",
        shopLatitude = 14.9,
        shopLongitude = 103.1,
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

    @Test fun marketReturnExposesCurrentStopProgressWithoutLosingTotalRefund() {
        val multi = state("returning", totalStops = 3, returnedStops = 1)
        assertTrue(multi.multiStop)
        assertEquals(2, multi.currentStopNumber)
        assertEquals(2, multi.remainingStopCount)
        assertEquals(100.0, multi.stopRefundAmount, 0.001)
        assertEquals(300.0, multi.expectedRefundAmount, 0.001)
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
