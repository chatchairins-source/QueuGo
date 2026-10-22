package com.queuego.merchant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantPosReportingTest {
    @Test fun parsesTheSharedPosOnlyOwnerReportAndHourlyRows() {
        val report = parsePosOwnerDashboard(JSONObject("""
            {"DINE_IN":125.0,"TAKEAWAY":75.0,"orders":4,
             "average_ticket":50.0,"cash_sales":150.0,"discounts":10.0,
             "refunds":5.0,"voids":1,"previous_period_sales":160.0,
             "change_percent":25.0,
             "hourly_sales":[{"hour":12,"bills":3,"sales":120.0},
                             {"hour":9,"bills":1,"sales":80.0}]}
        """))
        assertEquals(125.0, report.dineIn, 0.001)
        assertEquals(75.0, report.takeaway, 0.001)
        assertEquals(50.0, report.averageTicket, 0.001)
        assertEquals(150.0, report.cashSales, 0.001)
        assertEquals(10.0, report.discounts, 0.001)
        assertEquals(5.0, report.refunds, 0.001)
        assertEquals(1, report.voids)
        assertEquals(160.0, report.previousPeriodSales, 0.001)
        assertEquals(25.0, report.changePercent!!, 0.001)
        assertEquals(listOf(9, 12), report.hourlySales.map { it.hour })
    }

    @Test fun nullComparisonAndMalformedHourlyRowsAreSafe() {
        val report = parsePosOwnerDashboard(JSONObject("""
            {"DINE_IN":0,"TAKEAWAY":0,"orders":0,"change_percent":null,
             "hourly_sales":[{"hour":24,"bills":1,"sales":2},
                             {"hour":8,"bills":-1,"sales":2},
                             {"hour":7,"bills":2,"sales":4},
                             {"hour":7,"bills":3,"sales":5},null]}
        """))
        assertNull(report.changePercent)
        assertEquals(listOf(7), report.hourlySales.map { it.hour })
        assertEquals(2, report.orders)
    }
}
