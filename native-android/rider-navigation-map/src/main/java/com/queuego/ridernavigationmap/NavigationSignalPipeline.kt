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
    private val routeMatcher: NavigationHybridMatcher = NavigationHybridMatcher(),
    private val signalMetrics: NavigationSignalMetrics = NavigationSignalMetrics()
) {
    private var activeRouteId: String? = null
    private var lastDisplayFix: NavigationMapFix? = null
    private var consecutiveLowConfidenceMatches: Int = 0

    fun reset() {
        activeRouteId = null
        lastDisplayFix = null
        consecutiveLowConfidenceMatches = 0
        locationFilter.reset()
        routeMatcher.reset()
        signalMetrics.reset()
    }

    fun metricsSnapshot(): NavigationSignalMetricsSnapshot = signalMetrics.snapshot()

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
        if (activeRoute?.id != activeRouteId) {
            activeRouteId = activeRoute?.id
            consecutiveLowConfidenceMatches = 0
            routeMatcher.reset()
        }

        val filtered = locationFilter.filter(rawFix)
        val acceptedFix = filtered.fix
        if (filtered.rejected || acceptedFix == null) {
            return record(NavigationSignalState(
                rawFix = rawFix,
                displayFix = lastDisplayFix,
                quality = filtered.quality,
                routeMatch = null,
                puckState = NavigationPuckState.HELD,
                accepted = false,
                rejectionReason = filtered.reason
            ))
        }

        val previousDisplayFix = lastDisplayFix
        if (
            filtered.quality == NavigationFixQuality.WEAK &&
            previousDisplayFix != null &&
            navigationHaversineMeters(previousDisplayFix.point, acceptedFix.point) <=
                weakGpsFreezeRadiusMeters(acceptedFix.accuracyMeters)
        ) {
            return record(NavigationSignalState(
                rawFix = rawFix,
                displayFix = previousDisplayFix,
                quality = filtered.quality,
                routeMatch = null,
                puckState = NavigationPuckState.WEAK_GPS,
                accepted = true
            ))
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
            return record(NavigationSignalState(
                rawFix = rawFix,
                displayFix = previousDisplayFix,
                quality = filtered.quality,
                routeMatch = hybridMatch,
                puckState = NavigationPuckState.DEGRADED,
                accepted = true
            ))
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

        return record(NavigationSignalState(
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
        ))
    }

    private fun record(signal: NavigationSignalState): NavigationSignalState {
        signalMetrics.record(signal)
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
