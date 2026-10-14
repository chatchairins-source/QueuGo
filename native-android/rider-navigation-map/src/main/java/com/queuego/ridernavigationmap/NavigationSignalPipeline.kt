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

    fun reset() {
        activeRouteId = null
        lastDisplayFix = null
        locationFilter.reset()
        routeMatcher.reset()
    }

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
            routeMatcher.reset()
        }

        val filtered = locationFilter.filter(rawFix)
        val acceptedFix = filtered.fix
        if (filtered.rejected || acceptedFix == null) {
            return NavigationSignalState(
                rawFix = rawFix,
                displayFix = lastDisplayFix,
                quality = filtered.quality,
                routeMatch = null,
                puckState = NavigationPuckState.HELD,
                accepted = false,
                rejectionReason = filtered.reason
            )
        }

        val hybridMatch = activeRoute
            ?.takeIf { it.renderable }
            ?.let { routeMatcher.match(it, acceptedFix) }

        val snapEligible = hybridMatch != null &&
            hybridMatch.confidence != NavigationMatchConfidence.LOW &&
            hybridMatch.routeMatch.offRouteMeters <= snapThresholdMeters(acceptedFix)

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

        return NavigationSignalState(
            rawFix = rawFix,
            displayFix = displayFix,
            quality = filtered.quality,
            routeMatch = hybridMatch,
            puckState = puckState(
                quality = filtered.quality,
                match = hybridMatch,
                snapThresholdMeters = snapThresholdMeters(acceptedFix)
            ),
            accepted = true
        )
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
