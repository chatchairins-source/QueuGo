package com.queuego.rider

import com.queuego.ridernavigationmap.NavigationGuidanceState

private const val RIDER_UNPLANNED_STOP_MS = 10 * 60 * 1000L
private const val RIDER_UNPLANNED_STOP_MIN_REMAINING_METERS = 150.0
private const val RIDER_UNPLANNED_STOP_MOVING_KPH = 2

internal fun riderUnexpectedStopResetRequired(
    guidance: NavigationGuidanceState,
    reliableSpeedKph: Int?
): Boolean {
    val remaining = guidance.distanceRemainingMeters
    return guidance.weakGps ||
        guidance.offRoute ||
        guidance.arrived ||
        remaining == null ||
        !remaining.isFinite() ||
        remaining <= RIDER_UNPLANNED_STOP_MIN_REMAINING_METERS ||
        reliableSpeedKph == null ||
        reliableSpeedKph >= RIDER_UNPLANNED_STOP_MOVING_KPH
}

internal fun riderUnexpectedStopDue(
    activeNavigationWork: Boolean,
    guidance: NavigationGuidanceState,
    reliableSpeedKph: Int?,
    lastMovementElapsedMs: Long?,
    nowElapsedMs: Long,
    dismissedEpisodeStartMs: Long?
): Boolean {
    if (!activeNavigationWork) return false
    if (riderUnexpectedStopResetRequired(guidance, reliableSpeedKph)) return false
    val stoppedSince = lastMovementElapsedMs ?: return false
    if (dismissedEpisodeStartMs == stoppedSince) return false
    return nowElapsedMs >= stoppedSince &&
        nowElapsedMs - stoppedSince >= RIDER_UNPLANNED_STOP_MS
}

internal fun riderUnexpectedStopDurationLabel(
    lastMovementElapsedMs: Long?,
    nowElapsedMs: Long
): String? {
    val stoppedSince = lastMovementElapsedMs ?: return null
    if (nowElapsedMs < stoppedSince) return null
    val minutes = ((nowElapsedMs - stoppedSince) / 60_000L).coerceAtLeast(0L)
    return "$minutes นาที"
}
