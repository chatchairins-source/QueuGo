package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiderPerformanceMetricsTest {
    @Test fun derivesPerformanceOnlyFromRealPeriodSummaryValues() {
        val metrics = riderPerformanceMetrics(
            RiderPeriodSummary(
                days = 7,
                jobs = 14,
                income = 1400.0,
                onlineHours = 20.0,
                incomePerHour = 70.0
            )
        )

        assertEquals(100.0, metrics.averageIncomePerJob!!, 0.0001)
        assertEquals(0.7, metrics.jobsPerOnlineHour!!, 0.0001)
        assertEquals(200.0, metrics.averageIncomePerDay, 0.0001)
        assertEquals(2.0, metrics.averageJobsPerDay, 0.0001)
        assertEquals("0.7", riderPerformanceNumber(metrics.jobsPerOnlineHour))
    }

    @Test fun emptyPeriodDoesNotInventPerJobOrPerHourPerformance() {
        val metrics = riderPerformanceMetrics(
            RiderPeriodSummary(
                days = 0,
                jobs = 0,
                income = 0.0,
                onlineHours = 0.0,
                incomePerHour = null
            )
        )

        assertNull(metrics.averageIncomePerJob)
        assertNull(metrics.jobsPerOnlineHour)
        assertEquals(0.0, metrics.averageIncomePerDay, 0.0001)
        assertEquals(0.0, metrics.averageJobsPerDay, 0.0001)
    }
}
