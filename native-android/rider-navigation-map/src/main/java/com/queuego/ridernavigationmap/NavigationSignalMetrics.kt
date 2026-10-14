package com.queuego.ridernavigationmap

import kotlin.math.max

data class NavigationSignalMetricsSnapshot(
    val totalSamples: Long = 0,
    val acceptedSamples: Long = 0,
    val rejectedSamples: Long = 0,
    val snappedSamples: Long = 0,
    val heldSamples: Long = 0,
    val weakGpsSamples: Long = 0,
    val offRouteSamples: Long = 0,
    val degradedSamples: Long = 0,
    val averageRawDisplayOffsetMeters: Double = 0.0,
    val maxRawDisplayOffsetMeters: Double = 0.0
)

/**
 * Privacy-safe, in-memory rollout diagnostics.
 *
 * This class never stores coordinates, routes, addresses, order ids or timestamps. It only
 * accumulates counters and aggregate raw-to-display offset distances so shadow-mode behavior can
 * be evaluated before the display gate is enabled.
 */
class NavigationSignalMetrics {
    private var totalSamples = 0L
    private var acceptedSamples = 0L
    private var rejectedSamples = 0L
    private var snappedSamples = 0L
    private var heldSamples = 0L
    private var weakGpsSamples = 0L
    private var offRouteSamples = 0L
    private var degradedSamples = 0L
    private var offsetSampleCount = 0L
    private var offsetTotalMeters = 0.0
    private var maxOffsetMeters = 0.0

    fun reset() {
        totalSamples = 0
        acceptedSamples = 0
        rejectedSamples = 0
        snappedSamples = 0
        heldSamples = 0
        weakGpsSamples = 0
        offRouteSamples = 0
        degradedSamples = 0
        offsetSampleCount = 0
        offsetTotalMeters = 0.0
        maxOffsetMeters = 0.0
    }

    fun record(signal: NavigationSignalState) {
        totalSamples += 1
        if (signal.accepted) acceptedSamples += 1 else rejectedSamples += 1

        when (signal.puckState) {
            NavigationPuckState.HELD -> heldSamples += 1
            NavigationPuckState.WEAK_GPS -> weakGpsSamples += 1
            NavigationPuckState.OFF_ROUTE -> offRouteSamples += 1
            NavigationPuckState.DEGRADED -> degradedSamples += 1
            NavigationPuckState.ACTIVE -> Unit
        }

        val displayFix = signal.displayFix
        if (displayFix != null && displayFix.point.valid && signal.rawFix.point.valid) {
            val offset = navigationHaversineMeters(signal.rawFix.point, displayFix.point)
            if (offset.isFinite() && offset >= 0.0) {
                offsetSampleCount += 1
                offsetTotalMeters += offset
                maxOffsetMeters = max(maxOffsetMeters, offset)
            }

            val match = signal.routeMatch
            if (
                signal.accepted &&
                match != null &&
                match.confidence != NavigationMatchConfidence.LOW &&
                navigationHaversineMeters(displayFix.point, match.routeMatch.snapped) <= 0.5
            ) {
                snappedSamples += 1
            }
        }
    }

    fun snapshot(): NavigationSignalMetricsSnapshot = NavigationSignalMetricsSnapshot(
        totalSamples = totalSamples,
        acceptedSamples = acceptedSamples,
        rejectedSamples = rejectedSamples,
        snappedSamples = snappedSamples,
        heldSamples = heldSamples,
        weakGpsSamples = weakGpsSamples,
        offRouteSamples = offRouteSamples,
        degradedSamples = degradedSamples,
        averageRawDisplayOffsetMeters =
            if (offsetSampleCount == 0L) 0.0 else offsetTotalMeters / offsetSampleCount,
        maxRawDisplayOffsetMeters = maxOffsetMeters
    )
}
