package com.queuego.ridernavigationmap

import kotlin.math.max

enum class NavigationHeadingConfidence {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN
}

data class NavigationHeadingEstimate(
    val bearingDegrees: Float?,
    val confidence: NavigationHeadingConfidence
)

fun navigationHeadingEstimate(fix: NavigationMapFix): NavigationHeadingEstimate {
    val bearing = fix.bearingDegrees
        ?.takeIf { it.isFinite() }
        ?.let(::normalizeNavigationBearing)
        ?: return NavigationHeadingEstimate(
            bearingDegrees = null,
            confidence = NavigationHeadingConfidence.UNKNOWN
        )

    val speed = fix.speedMetersPerSecond
        ?.takeIf { it.isFinite() && it >= 0f }
    val bearingAccuracy = fix.bearingAccuracyDegrees
        ?.takeIf { it.isFinite() && it >= 0f }
    val speedAccuracy = fix.speedAccuracyMetersPerSecond
        ?.takeIf { it.isFinite() && it >= 0f }

    var confidence = when {
        speed != null && speed < 1.2f -> NavigationHeadingConfidence.LOW
        bearingAccuracy == null && speed != null && speed >= 2f ->
            NavigationHeadingConfidence.MEDIUM
        bearingAccuracy == null -> NavigationHeadingConfidence.UNKNOWN
        bearingAccuracy <= 15f -> NavigationHeadingConfidence.HIGH
        bearingAccuracy <= 35f -> NavigationHeadingConfidence.MEDIUM
        else -> NavigationHeadingConfidence.LOW
    }

    if (
        speed != null &&
        speedAccuracy != null &&
        speedAccuracy > max(3f, speed * 0.75f)
    ) {
        confidence = when (confidence) {
            NavigationHeadingConfidence.HIGH -> NavigationHeadingConfidence.MEDIUM
            NavigationHeadingConfidence.MEDIUM -> NavigationHeadingConfidence.LOW
            else -> confidence
        }
    }

    return NavigationHeadingEstimate(
        bearingDegrees = bearing,
        confidence = confidence
    )
}

internal fun navigationUsableGpsBearing(fix: NavigationMapFix): Float? {
    val estimate = navigationHeadingEstimate(fix)
    return estimate.bearingDegrees?.takeIf {
        estimate.confidence == NavigationHeadingConfidence.HIGH ||
            estimate.confidence == NavigationHeadingConfidence.MEDIUM
    }
}
