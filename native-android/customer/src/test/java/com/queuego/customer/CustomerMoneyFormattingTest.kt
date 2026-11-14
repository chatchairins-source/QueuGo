package com.queuego.customer

import org.junit.Assert.*
import org.junit.Test

class CustomerMoneyFormattingTest {
    @Test fun marketMatchesCurrentWebBahtWithoutDroppingFractionalPrices() {
        assertEquals("฿1,234.5", customerBaht(1234.50))
        assertEquals("฿12.125", customerBaht(12.125))
        assertEquals("฿0.3", customerBaht(0.1 + 0.2))
        assertEquals("฿0", customerBaht(0.0))
    }
    @Test fun laundryMatchesCurrentTwoDecimalFeeTextAndInvalidMoneyDoesNotBecomeZero() {
        assertEquals("12.35 บาท", laundryMoney(12.345))
        assertEquals("1,234.5 บาท", laundryMoney(1234.5))
        assertEquals("รอสรุปราคา", customerBaht(Double.NaN))
        assertEquals("รอสรุป", laundryMoney(Double.POSITIVE_INFINITY))
    }
    @Test fun laundryServiceAreaFailuresKeepProductionThaiCopy() {
        assertTrue(laundryCheckoutMessage("OUTSIDE_SERVICE_AREA").contains("อยู่นอกพื้นที่"))
        assertTrue(laundryCheckoutMessage("DELIVERY_DISTANCE_EXCEEDED").contains("ห่างเกินขอบเขต"))
    }
}
