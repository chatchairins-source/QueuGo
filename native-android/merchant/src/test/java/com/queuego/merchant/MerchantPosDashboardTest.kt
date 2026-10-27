package com.queuego.merchant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantPosDashboardTest {
    @Test fun ownerReportKeepsWebStorefrontDeliveryAndGpBreakdown() {
        val report = merchantPosOwnerDashboard(
            JSONObject()
                .put("DINE_IN", 120.0)
                .put("TAKEAWAY", 80.0)
                .put("DELIVERY", 250.0)
                .put("GP", 37.5)
                .put("orders", 9)
        )

        assertEquals(120.0, report.dineIn, 0.0)
        assertEquals(80.0, report.takeaway, 0.0)
        assertEquals(250.0, report.delivery, 0.0)
        assertEquals(37.5, report.gp, 0.0)
        assertEquals(9, report.orders)
    }

    @Test fun missingOptionalSummaryFieldsDefaultToZero() {
        val report = merchantPosOwnerDashboard(JSONObject().put("DINE_IN", 20.0))

        assertEquals(20.0, report.dineIn, 0.0)
        assertEquals(0.0, report.takeaway, 0.0)
        assertEquals(0.0, report.delivery, 0.0)
        assertEquals(0.0, report.gp, 0.0)
        assertEquals(0, report.orders)
    }
}
