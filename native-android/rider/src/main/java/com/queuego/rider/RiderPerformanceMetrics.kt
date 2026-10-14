package com.queuego.rider

import java.util.Locale

internal data class RiderPerformanceMetrics(
    val averageIncomePerJob: Double?,
    val jobsPerOnlineHour: Double?,
    val averageIncomePerDay: Double,
    val averageJobsPerDay: Double
)

internal fun riderPerformanceMetrics(summary: RiderPeriodSummary): RiderPerformanceMetrics {
    val safeDays = summary.days.coerceAtLeast(1)
    val averageIncomePerJob =
        if (summary.jobs > 0) summary.income / summary.jobs.toDouble() else null
    val jobsPerOnlineHour =
        if (summary.onlineHours > 0.0) summary.jobs.toDouble() / summary.onlineHours else null

    return RiderPerformanceMetrics(
        averageIncomePerJob = averageIncomePerJob,
        jobsPerOnlineHour = jobsPerOnlineHour,
        averageIncomePerDay = summary.income / safeDays.toDouble(),
        averageJobsPerDay = summary.jobs.toDouble() / safeDays.toDouble()
    )
}

internal fun riderPerformanceNumber(value: Double): String =
    String.format(Locale.US, "%.1f", value)
