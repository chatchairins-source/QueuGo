package com.queuego.ridernavigationmap

data class NavigationPuckOrientationDecision(
    val rotationDegrees: Float?,
    val headingConfidence: NavigationHeadingConfidence
)

fun navigationPuckOrientationDecision(
    fix: NavigationMapFix,
    cameraBearingDegrees: Float?
): NavigationPuckOrientationDecision {
    val heading = navigationHeadingEstimate(fix)
    val vehicleBearing = heading.bearingDegrees
    val usable = heading.confidence == NavigationHeadingConfidence.HIGH ||
        heading.confidence == NavigationHeadingConfidence.MEDIUM

    if (!usable || vehicleBearing == null) {
        return NavigationPuckOrientationDecision(
            rotationDegrees = null,
            headingConfidence = heading.confidence
        )
    }

    val cameraBearing = cameraBearingDegrees
        ?.takeIf { it.isFinite() }
        ?.let(::normalizeNavigationBearing)
        ?: 0f

    return NavigationPuckOrientationDecision(
        rotationDegrees = normalizeNavigationBearing(vehicleBearing - cameraBearing),
        headingConfidence = heading.confidence
    )
}
