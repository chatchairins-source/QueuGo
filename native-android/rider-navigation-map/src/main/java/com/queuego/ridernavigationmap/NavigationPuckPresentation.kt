package com.queuego.ridernavigationmap

enum class NavigationPuckVisualState {
    ACTIVE,
    DEGRADED,
    WEAK_GPS,
    OFF_ROUTE,
    HELD,
    STALE,
    LOST
}

data class NavigationPuckPresentation(
    val state: NavigationPuckVisualState,
    val alpha: Float,
    val showAccuracyHalo: Boolean,
    val rotateWithBearing: Boolean,
    val freezePosition: Boolean
)

fun navigationPuckPresentation(
    signal: NavigationSignalState?,
    freshness: NavigationPuckFreshnessState
): NavigationPuckPresentation {
    if (freshness.freshness == NavigationPuckFreshness.LOST) {
        return NavigationPuckPresentation(
            state = NavigationPuckVisualState.LOST,
            alpha = 0.35f,
            showAccuracyHalo = false,
            rotateWithBearing = false,
            freezePosition = true
        )
    }
    if (freshness.freshness == NavigationPuckFreshness.STALE) {
        return NavigationPuckPresentation(
            state = NavigationPuckVisualState.STALE,
            alpha = 0.55f,
            showAccuracyHalo = false,
            rotateWithBearing = false,
            freezePosition = true
        )
    }

    return when (signal?.puckState ?: NavigationPuckState.DEGRADED) {
        NavigationPuckState.ACTIVE -> NavigationPuckPresentation(
            state = NavigationPuckVisualState.ACTIVE,
            alpha = 1f,
            showAccuracyHalo = false,
            rotateWithBearing = true,
            freezePosition = false
        )
        NavigationPuckState.DEGRADED -> NavigationPuckPresentation(
            state = NavigationPuckVisualState.DEGRADED,
            alpha = 0.88f,
            showAccuracyHalo = true,
            rotateWithBearing = true,
            freezePosition = false
        )
        NavigationPuckState.WEAK_GPS -> NavigationPuckPresentation(
            state = NavigationPuckVisualState.WEAK_GPS,
            alpha = 0.72f,
            showAccuracyHalo = true,
            rotateWithBearing = false,
            freezePosition = true
        )
        NavigationPuckState.OFF_ROUTE -> NavigationPuckPresentation(
            state = NavigationPuckVisualState.OFF_ROUTE,
            alpha = 1f,
            showAccuracyHalo = true,
            rotateWithBearing = true,
            freezePosition = false
        )
        NavigationPuckState.HELD -> NavigationPuckPresentation(
            state = NavigationPuckVisualState.HELD,
            alpha = 0.68f,
            showAccuracyHalo = true,
            rotateWithBearing = false,
            freezePosition = true
        )
    }
}
