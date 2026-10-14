package com.queuego.rider

import com.queuego.ridernavigationmap.NavigationMapFix
import kotlin.math.roundToInt

internal const val RIDER_REST_REMINDER_AFTER_MS = 2L * 60L * 60L * 1000L

internal fun riderReliableSpeedKph(fix: NavigationMapFix?): Int? {
    val metersPerSecond = fix?.speedMetersPerSecond ?: return null
    if (!metersPerSecond.isFinite() || metersPerSecond < 0f) return null
    val accuracy = fix.speedAccuracyMetersPerSecond
    if (accuracy != null && (!accuracy.isFinite() || accuracy > 4.5f)) return null
    val kilometersPerHour = metersPerSecond * 3.6f
    if (!kilometersPerHour.isFinite() || kilometersPerHour > 200f) return null
    return if (kilometersPerHour < 1f) 0 else kilometersPerHour.roundToInt()
}

internal fun riderContinuousNavigationMs(
    sessionStartedElapsedRealtimeMs: Long?,
    nowElapsedRealtimeMs: Long
): Long {
    val started = sessionStartedElapsedRealtimeMs ?: return 0L
    if (started <= 0L || nowElapsedRealtimeMs <= started) return 0L
    return nowElapsedRealtimeMs - started
}

internal fun riderRestReminderDue(
    sessionStartedElapsedRealtimeMs: Long?,
    nowElapsedRealtimeMs: Long,
    dismissedSessionStart: Long?
): Boolean {
    val started = sessionStartedElapsedRealtimeMs ?: return false
    if (dismissedSessionStart == started) return false
    return riderContinuousNavigationMs(started, nowElapsedRealtimeMs) >= RIDER_REST_REMINDER_AFTER_MS
}

internal fun riderDriveDurationLabel(durationMs: Long): String {
    val totalMinutes = (durationMs.coerceAtLeast(0L) / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours ชม. $minutes นาที"
        hours > 0 -> "$hours ชม."
        else -> "$minutes นาที"
    }
}
