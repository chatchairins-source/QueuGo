package com.queuego.rider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiderGrabParityReleaseGateTest {
    @Test fun emptyServerSignalsNeverCreateFakeDriverTrustData() {
        assertNull(riderProfilePhotoMimeType(null))
        assertNull(riderHistoryCompletedLabel(null))
        assertEquals(0, riderComplimentSummaryFromRpc(JSONObject()).total)
        assertEquals(emptyList<RiderComplimentCount>(), riderComplimentSummaryFromRpc(JSONObject()).items)
    }

    @Test fun performanceGateStaysDerivedFromAuthoritativeSummary() {
        val metrics = riderPerformanceMetrics(
            RiderPeriodSummary(
                days = 1,
                jobs = 2,
                income = 100.0,
                onlineHours = 4.0,
                incomePerHour = 25.0
            )
        )
        assertEquals(50.0, metrics.averageIncomePerJob!!, 0.0001)
        assertEquals(0.5, metrics.jobsPerOnlineHour!!, 0.0001)
    }
}
