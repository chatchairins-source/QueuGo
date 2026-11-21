package com.queuego.merchant

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantPosDashboardTest {
    @Test fun ownerReportKeepsServerBreakdownsAndParsesWebPosMetrics() {
        val report = merchantPosOwnerDashboard(
            JSONObject()
                .put("DINE_IN", 120.0)
                .put("TAKEAWAY", 80.0)
                .put("DELIVERY", 250.0)
                .put("GP", 37.5)
                .put("orders", 9)
                .put("average_ticket", 22.22)
                .put("cash_sales", 180.0)
                .put("discounts", 5.0)
                .put("refunds", 3.0)
                .put("voids", 1)
                .put("change_percent", 12.5)
                .put("hourly_sales", JSONArray().put(
                    JSONObject().put("hour", 9).put("bills", 2).put("sales", 100.0)
                ))
        )

        assertEquals(120.0, report.dineIn, 0.0)
        assertEquals(80.0, report.takeaway, 0.0)
        assertEquals(250.0, report.delivery, 0.0)
        assertEquals(37.5, report.gp, 0.0)
        assertEquals(9, report.orders)
        assertEquals(22.22, report.averageTicket, 0.0)
        assertEquals(180.0, report.cashSales, 0.0)
        assertEquals(5.0, report.discounts, 0.0)
        assertEquals(3.0, report.refunds, 0.0)
        assertEquals(1, report.voids)
        assertEquals(12.5, report.changePercent!!, 0.0)
        assertEquals(1, report.hourlySales.size)
        assertEquals(9, report.hourlySales.single().hour)
        assertEquals(2, report.hourlySales.single().bills)
        assertEquals(100.0, report.hourlySales.single().sales, 0.0)
    }

    @Test fun missingOptionalSummaryFieldsDefaultSafely() {
        val report = merchantPosOwnerDashboard(JSONObject().put("DINE_IN", 20.0))

        assertEquals(20.0, report.dineIn, 0.0)
        assertEquals(0.0, report.takeaway, 0.0)
        assertEquals(0.0, report.delivery, 0.0)
        assertEquals(0.0, report.gp, 0.0)
        assertEquals(0, report.orders)
        assertEquals(0.0, report.averageTicket, 0.0)
        assertEquals(0.0, report.cashSales, 0.0)
        assertEquals(0.0, report.discounts, 0.0)
        assertEquals(0.0, report.refunds, 0.0)
        assertEquals(0, report.voids)
        assertNull(report.changePercent)
        assertTrue(report.hourlySales.isEmpty())
    }

    @Test fun posCsvIncludesHourlyAndItemRowsAndEscapesSpreadsheetFormulas() {
        val report = merchantPosOwnerDashboard(
            JSONObject()
                .put("DINE_IN", 100.0)
                .put("TAKEAWAY", 50.0)
                .put("orders", 3)
                .put("average_ticket", 50.0)
                .put("cash_sales", 90.0)
                .put("discounts", 5.0)
                .put("refunds", 2.0)
                .put("voids", 1)
                .put("change_percent", -2.5)
                .put("hourly_sales", JSONArray().put(
                    JSONObject().put("hour", 9).put("bills", 2).put("sales", 100.0)
                ))
        )
        val items = PosItemSalesReport(
            itemCount = 1,
            items = listOf(PosItemSalesRow(name = "=1+1", quantity = 2.0, bills = 1, sales = 50.0))
        )

        val csv = buildPosReportCsv(report, items)

        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"ยอดขายรายชั่วโมง\""))
        assertTrue(csv.contains("\"09:00\",\"2\",\"100.0\""))
        assertTrue(csv.contains("\"'=1+1\""))
        assertTrue(csv.contains("\"รับเงินสด\",\"90.0\""))
        assertFalse(csv.contains("\"Delivery\""))
        assertFalse(csv.contains("\"GP Delivery\""))
    }
}
