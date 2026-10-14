package com.queuego.ridernavigationmap

import kotlin.math.max

enum class NavigationPuckState {
    ACTIVE,
    DEGRADED,
    WEAK_GPS,
    OFF_ROUTE,
    HELD
}

data class NavigationSignalState(
    val rawFix: NavigationMapFix,
    val displayFix: NavigationMapFix?,
    val quality: NavigationFixQuality,
    val routeMatch: NavigationHybridRouteMatch?,
    val puckState: NavigationPuckState,
    val accepted: Boolean,
    val rejectionReason: String? = null
)

class NavigationSignalPipeline(
    private val locationFilter: NavigationLocationFilter = NavigationLocationFilter(),
    private val routeMatcher: NavigationHybridMatcher = NavigationHybridMatcher()
) {
    private var activeRouteId: String? = null
    private var lastDisplayFix: NavigationMapFix? = null
    private var lastRawFix: NavigationMapFix? = null
    private var lastSignal: NavigationSignalState? = null
    private var consecutiveLowConfidenceMatches: Int = 0
    private var confidenceMetrics = NavigationConfidenceMetrics()

    fun reset() {
        activeRouteId = null
        lastDisplayFix = null
        lastRawFix = null
        lastSignal = null
        consecutiveLowConfidenceMatches = 0
        confidenceMetrics = NavigationConfidenceMetrics()
        locationFilter.reset()
        routeMatcher.reset()
    }

    fun metrics(): NavigationConfidenceMetrics = confidenceMetrics

    fun update(
        rawFix: NavigationMapFix,
        state: RiderNavigationMapState
    ): NavigationSignalState {
        val activeRoute = state.styledRemainingRoutes()
            .firstOrNull { it.style == NavigationRouteStyle.ACTIVE }
        return update(rawFix, activeRoute)
    }

    /**
     * Produces a renderer-only state. The authoritative route coordinator and all
     * business/arrival logic must continue consuming the raw fix upstream.
     */
    fun applyToDisplayState(authoritativeState: RiderNavigationMapState): RiderNavigationMapState {
        val rawFix = authoritativeState.rider ?: return authoritativeState
        val signal = update(rawFix, authoritativeState)
        return authoritativeState.copy(
            rider = signal.displayFix ?: rawFix
        )
    }

    fun update(
        rawFix: NavigationMapFix,
        activeRoute: NavigationRouteSegment?
    ): NavigationSignalState {
        val nextRouteId = activeRoute?.id
        if (rawFix == lastRawFix && nextRouteId == activeRouteId) {
            return requireNotNull(lastSignal) {
                "Duplicate fix was observed before the first navigation signal was recorded"
            }
        }

        if (nextRouteId != activeRouteId) {
            activeRouteId = nextRouteId
            consecutiveLowConfidenceMatches = 0
            routeMatcher.reset()
        }

        val filtered = locationFilter.filter(rawFix)
        val acceptedFix = filtered.fix
        if (filtered.rejected || acceptedFix == null) {
            return record(
                NavigationSignalState(
                    rawFix = rawFix,
                    displayFix = lastDisplayFix,
                    quality = filtered.quality,
                    routeMatch = null,
                    puckState = NavigationPuckState.HELD,
                    accepted = false,
                    rejectionReason = filtered.reason
                )
            )
        }

        val previousDisplayFix = lastDisplayFix
        if (
            filtered.quality == NavigationFixQuality.WEAK &&
            previousDisplayFix != null &&
            navigationHaversineMeters(previousDisplayFix.point, acceptedFix.point) <=
                weakGpsFreezeRadiusMeters(acceptedFix.accuracyMeters)
        ) {
            return record(
                NavigationSignalState(
                    rawFix = rawFix,
                    displayFix = previousDisplayFix,
                    quality = filtered.quality,
                    routeMatch = null,
                    puckState = NavigationPuckState.WEAK_GPS,
                    accepted = true
                )
            )
        }

        val hybridMatch = activeRoute
            ?.takeIf { it.renderable }
            ?.let { routeMatcher.match(it, acceptedFix) }

        consecutiveLowConfidenceMatches = when (hybridMatch?.confidence) {
            NavigationMatchConfidence.LOW -> consecutiveLowConfidenceMatches + 1
            NavigationMatchConfidence.HIGH,
            NavigationMatchConfidence.MEDIUM -> 0
            null -> consecutiveLowConfidenceMatches
        }

        val snapThreshold = snapThresholdMeters(acceptedFix)
        val snapEligible = hybridMatch != null &&
            hybridMatch.confidence != NavigationMatchConfidence.LOW &&
            hybridMatch.routeMatch.offRouteMeters <= snapThreshold

        val holdAmbiguousMatch = !snapEligible &&
            hybridMatch?.confidence == NavigationMatchConfidence.LOW &&
            consecutiveLowConfidenceMatches <= 2 &&
            previousDisplayFix != null &&
            navigationHaversineMeters(previousDisplayFix.point, acceptedFix.point) <=
                ambiguousHoldRadiusMeters(acceptedFix.accuracyMeters)

        if (holdAmbiguousMatch) {
            return record(
                NavigationSignalState(
                    rawFix = rawFix,
                    displayFix = previousDisplayFix,
                    quality = filtered.quality,
                    routeMatch = hybridMatch,
                    puckState = NavigationPuckState.DEGRADED,
                    accepted = true
                )
            )
        }

        val displayBearing = when {
            !snapEligible -> acceptedFix.bearingDegrees
            acceptedFix.speedMetersPerSecond?.let { it.isFinite() && it < 2f } == true ->
                hybridMatch!!.routeMatch.segmentBearingDegrees ?: acceptedFix.bearingDegrees
            else -> acceptedFix.bearingDegrees ?: hybridMatch!!.routeMatch.segmentBearingDegrees
        }

        val displayFix = acceptedFix.copy(
            point = if (snapEligible) hybridMatch!!.routeMatch.snapped else acceptedFix.point,
            bearingDegrees = displayBearing
        )
        lastDisplayFix = displayFix

        return record(
            NavigationSignalState(
                rawFix = rawFix,
                displayFix = displayFix,
                quality = filtered.quality,
                routeMatch = hybridMatch,
                puckState = puckState(
                    quality = filtered.quality,
                    match = hybridMatch,
                    snapThresholdMeters = snapThreshold
                ),
                accepted = true
            )
        )
    }

    private fun record(signal: NavigationSignalState): NavigationSignalState {
        lastRawFix = signal.rawFix
        lastSignal = signal

        val candidateDelta = signal.displayFix?.let { displayFix ->
            navigationHaversineMeters(signal.rawFix.point, displayFix.point)
        }
        val matchConfidence = signal.routeMatch?.confidence
        confidenceMetrics = confidenceMetrics.copy(
            sampleCount = confidenceMetrics.sampleCount + 1L,
            acceptedCount = confidenceMetrics.acceptedCount + if (signal.accepted) 1L else 0L,
            rejectedCount = confidenceMetrics.rejectedCount + if (signal.accepted) 0L else 1L,
            highMatchCount = confidenceMetrics.highMatchCount +
                if (matchConfidence == NavigationMatchConfidence.HIGH) 1L else 0L,
            mediumMatchCount = confidenceMetrics.mediumMatchCount +
                if (matchConfidence == NavigationMatchConfidence.MEDIUM) 1L else 0L,
            lowMatchCount = confidenceMetrics.lowMatchCount +
                if (matchConfidence == NavigationMatchConfidence.LOW) 1L else 0L,
            heldPuckCount = confidenceMetrics.heldPuckCount +
                if (signal.puckState == NavigationPuckState.HELD) 1L else 0L,
            weakGpsCount = confidenceMetrics.weakGpsCount +
                if (signal.puckState == NavigationPuckState.WEAK_GPS) 1L else 0L,
            offRouteCount = confidenceMetrics.offRouteCount +
                if (signal.puckState == NavigationPuckState.OFF_ROUTE) 1L else 0L,
            lastCandidateDeltaMeters = candidateDelta,
            maxCandidateDeltaMeters = max(
                confidenceMetrics.maxCandidateDeltaMeters,
                candidateDelta ?: 0.0
            ),
            lastRejectionReason = signal.rejectionReason ?: confidenceMetrics.lastRejectionReason
        )
        return signal
    }

    private fun ambiguousHoldRadiusMeters(accuracyMeters: Float?): Double {
        val accuracy = accuracyMeters
            ?.takeIf { it.isFinite() && it >= 0f }
            ?.toDouble()
            ?: 20.0
        return max(40.0, accuracy * 1.8).coerceAtMost(90.0)
    }

    private fun weakGpsFreezeRadiusMeters(accuracyMeters: Float?): Double {
        val accuracy = accuracyMeters
            ?.takeIf { it.isFinite() && it >= 0f }
            ?.toDouble()
            ?: 60.0
        return max(24.0, accuracy * 0.8).coerceAtMost(64.0)
    }

    private fun snapThresholdMeters(fix: NavigationMapFix): Double {
        val accuracy = fix.accuracyMeters
            ?.takeIf { it.isFinite() && it >= 0f }
            ?.toDouble()
            ?: 15.0
        return max(35.0, accuracy * 1.6).coerceAtMost(75.0)
    }

    private fun puckState(
        quality: NavigationFixQuality,
        match: NavigationHybridRouteMatch?,
        snapThresholdMeters: Double
    ): NavigationPuckState {
        if (quality == NavigationFixQuality.WEAK) return NavigationPuckState.WEAK_GPS
        if (
            match != null &&
            match.confidence == NavigationMatchConfidence.LOW &&
            match.routeMatch.offRouteMeters > snapThresholdMeters
        ) {
            return NavigationPuckState.OFF_ROUTE
        }
        return when (quality) {
            NavigationFixQuality.GOOD -> NavigationPuckState.ACTIVE
            NavigationFixQuality.DEGRADED -> NavigationPuckState.DEGRADED
            NavigationFixQuality.WEAK -> NavigationPuckState.WEAK_GPS
            NavigationFixQuality.REJECTED -> NavigationPuckState.HELD
        }
    }
}
