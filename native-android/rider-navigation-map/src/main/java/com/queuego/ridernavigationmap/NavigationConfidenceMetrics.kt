package com.queuego.ridernavigationmap

data class NavigationConfidenceMetrics(
    val sampleCount: Long = 0L,
    val acceptedCount: Long = 0L,
    val rejectedCount: Long = 0L,
    val highMatchCount: Long = 0L,
    val mediumMatchCount: Long = 0L,
    val lowMatchCount: Long = 0L,
    val heldPuckCount: Long = 0L,
    val weakGpsCount: Long = 0L,
    val offRouteCount: Long = 0L,
    val lastCandidateDeltaMeters: Double? = null,
    val maxCandidateDeltaMeters: Double = 0.0,
    val lastRejectionReason: String? = null
)
