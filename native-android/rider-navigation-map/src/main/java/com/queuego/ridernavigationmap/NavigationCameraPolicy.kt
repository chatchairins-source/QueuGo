package com.queuego.ridernavigationmap

import kotlin.math.abs

private const val ROUTE_BEARING_MAX_OFF_ROUTE_METERS = 60.0

data class NavigationCameraDecision(
    val center: NavigationMapPoint,
    val zoom: Int,
    val bearingDegrees: Float?,
    val tiltDegrees: Float,
    val routeMatch: NavigationRouteMatch?,
    val remainingRouteMeters: Double?
)

internal fun navigationCameraDecision(
    state: RiderNavigationMapState,
    previousBearingDegrees: Float?
): NavigationCameraDecision? {
    val fix = state.rider?.takeIf { it.point.valid } ?: return null
    val navigationActive = state.perspectiveCameraEnabled || state.remainingStops().isNotEmpty()
    if (!navigationActive) {
        return NavigationCameraDecision(
            center = fix.point,
            zoom = state.followZoom.coerceIn(14, 20),
            bearingDegrees = 0f,
            tiltDegrees = 0f,
            routeMatch = null,
            remainingRouteMeters = null
        )
    }

    val activeRoute = state.styledRemainingRoutes()
        .firstOrNull { it.style == NavigationRouteStyle.ACTIVE }
    val match = activeRoute?.let { matchNavigationRoute(it, fix.point) }
    val reliableRouteMatch = match?.takeIf { it.offRouteMeters <= ROUTE_BEARING_MAX_OFF_ROUTE_METERS }

    val gpsBearing = navigationUsableGpsBearing(fix)
    val speed = fix.speedMetersPerSecond
        ?.takeIf { it.isFinite() && it >= 0f }
    val routeBearing = if (activeRoute != null && reliableRouteMatch != null) {
        navigationRouteBearing(
            segment = activeRoute,
            match = reliableRouteMatch,
            speedMetersPerSecond = speed
        )
    } else null

    // Keep the navigation camera aligned to the matched road whenever the Rider is
    // confidently on-route. Raw GNSS course can jump left/right between adjacent fixes,
    // especially on motorcycles and urban roads, so it is only the fallback when there
    // is no reliable route segment to follow.
    val desiredBearing = when {
        routeBearing != null -> routeBearing
        speed == null || speed < 0.83f -> previousBearingDegrees
        else -> gpsBearing ?: previousBearingDegrees
    }
    val smoothedBearing = smoothNavigationBearing(
        previous = previousBearingDegrees,
        target = desiredBearing,
        factor = navigationBearingSmoothingFactor(speed)
    )

    val remaining = if (match != null) {
        (match.geometryDistanceMeters - match.progressMeters).coerceAtLeast(0.0)
    } else null
    val zoom = navigationZoom(
        baseZoom = state.followZoom,
        speedMetersPerSecond = speed,
        remainingRouteMeters = remaining,
        accuracyMeters = fix.accuracyMeters
    )
    val tilt = navigationTilt(
        speedMetersPerSecond = speed,
        remainingRouteMeters = remaining,
        accuracyMeters = fix.accuracyMeters
    )

    val lookAheadMeters = navigationLookAheadMeters(speed, remaining)
    val center = if (activeRoute != null && match != null && match.offRouteMeters <= ROUTE_BEARING_MAX_OFF_ROUTE_METERS) {
        navigationPointAtDistance(
            activeRoute,
            (match.progressMeters + lookAheadMeters).coerceAtMost(match.geometryDistanceMeters)
        ) ?: fix.point
    } else {
        fix.point
    }

    return NavigationCameraDecision(
        center = center,
        zoom = zoom,
        bearingDegrees = smoothedBearing,
        tiltDegrees = tilt,
        routeMatch = match,
        remainingRouteMeters = remaining
    )
}

internal fun navigationZoom(
    baseZoom: Int,
    speedMetersPerSecond: Float?,
    remainingRouteMeters: Double?,
    accuracyMeters: Float?
): Int {
    val base = baseZoom.coerceIn(14, 20)
    var target = when {
        remainingRouteMeters != null && remainingRouteMeters <= 60.0 -> 19
        remainingRouteMeters != null && remainingRouteMeters <= 180.0 -> 18
        speedMetersPerSecond == null -> base
        speedMetersPerSecond >= 18f -> 15
        speedMetersPerSecond >= 11f -> 16
        speedMetersPerSecond >= 5f -> 17
        speedMetersPerSecond <= 1.5f -> maxOf(base, 18)
        else -> base
    }
    if (accuracyMeters != null && accuracyMeters.isFinite() && accuracyMeters > 60f) {
        target = (target - 1).coerceAtLeast(14)
    }
    return target.coerceIn(14, 20)
}

internal fun navigationTilt(
    speedMetersPerSecond: Float?,
    remainingRouteMeters: Double?,
    accuracyMeters: Float?
): Float {
    val target = when {
        accuracyMeters != null && accuracyMeters.isFinite() && accuracyMeters > 80f -> 24f
        remainingRouteMeters != null && remainingRouteMeters <= 60.0 -> 28f
        remainingRouteMeters != null && remainingRouteMeters <= 180.0 -> 34f
        speedMetersPerSecond == null -> 38f
        speedMetersPerSecond >= 18f -> 50f
        speedMetersPerSecond >= 11f -> 48f
        speedMetersPerSecond >= 5f -> 44f
        speedMetersPerSecond >= 2f -> 40f
        else -> 34f
    }
    return target.coerceIn(0f, 50f)
}

internal fun smoothNavigationTilt(
    previous: Float,
    target: Float,
    maxStepDegrees: Float = 4f
): Float {
    val safePrevious = previous.takeIf { it.isFinite() } ?: 0f
    val safeTarget = target.takeIf { it.isFinite() }?.coerceIn(0f, 50f) ?: safePrevious
    val delta = safeTarget - safePrevious
    val maxStep = maxStepDegrees.coerceIn(0.5f, 12f)
    return when {
        abs(delta) <= maxStep -> safeTarget
        delta > 0f -> safePrevious + maxStep
        else -> safePrevious - maxStep
    }.coerceIn(0f, 50f)
}

internal fun navigationTiltChangedEnough(
    previous: Float?,
    next: Float,
    minimumDeltaDegrees: Float = 0.75f
): Boolean {
    if (!next.isFinite()) return false
    if (previous == null || !previous.isFinite()) return true
    return abs(next - previous) >= minimumDeltaDegrees.coerceAtLeast(0.25f)
}

internal fun navigationLookAheadMeters(
    speedMetersPerSecond: Float?,
    remainingRouteMeters: Double?
): Double {
    val desired = when {
        speedMetersPerSecond == null -> 55.0
        speedMetersPerSecond >= 18f -> 150.0
        speedMetersPerSecond >= 11f -> 120.0
        speedMetersPerSecond >= 5f -> 85.0
        speedMetersPerSecond >= 2f -> 55.0
        else -> 30.0
    }
    return if (remainingRouteMeters == null) desired
    else minOf(desired, (remainingRouteMeters * 0.55).coerceAtLeast(0.0))
}

internal fun navigationRouteBearing(
    segment: NavigationRouteSegment,
    match: NavigationRouteMatch,
    speedMetersPerSecond: Float?
): Float? {
    val currentBearing = match.segmentBearingDegrees
        ?.takeIf { it.isFinite() }
        ?.let(::normalizeNavigationBearing)
        ?: return null
    val remaining = (match.geometryDistanceMeters - match.progressMeters).coerceAtLeast(0.0)
    val lookAhead = navigationBearingLookAheadMeters(speedMetersPerSecond, remaining)
    if (lookAhead < 5.0) return currentBearing

    val future = navigationPointAtDistance(
        segment,
        (match.progressMeters + lookAhead).coerceAtMost(match.geometryDistanceMeters)
    ) ?: return currentBearing
    if (!future.valid || navigationHaversineMeters(match.snapped, future) < 5.0) {
        return currentBearing
    }

    val forwardBearing = navigationBearingDegrees(match.snapped, future)
    val boundedForwardBearing = navigationBoundAnticipatedBearing(
        currentBearing = currentBearing,
        futureBearing = forwardBearing
    )
    return smoothNavigationBearing(
        previous = currentBearing,
        target = boundedForwardBearing,
        factor = navigationTurnAnticipationFactor(speedMetersPerSecond)
    ) ?: currentBearing
}

internal fun navigationBoundAnticipatedBearing(
    currentBearing: Float,
    futureBearing: Float,
    maxAnticipationDegrees: Float = 70f
): Float {
    val current = normalizeNavigationBearing(currentBearing)
    val future = normalizeNavigationBearing(futureBearing)
    val delta = ((future - current + 540f) % 360f) - 180f
    val boundedDelta = delta.coerceIn(
        -maxAnticipationDegrees.coerceIn(20f, 90f),
        maxAnticipationDegrees.coerceIn(20f, 90f)
    )
    return normalizeNavigationBearing(current + boundedDelta)
}

internal fun navigationBearingLookAheadMeters(
    speedMetersPerSecond: Float?,
    remainingRouteMeters: Double?
): Double {
    val desired = when {
        speedMetersPerSecond == null -> 24.0
        speedMetersPerSecond >= 18f -> 70.0
        speedMetersPerSecond >= 11f -> 55.0
        speedMetersPerSecond >= 5f -> 38.0
        speedMetersPerSecond >= 2f -> 26.0
        else -> 14.0
    }
    return if (remainingRouteMeters == null) desired
    else minOf(desired, (remainingRouteMeters * 0.45).coerceAtLeast(0.0))
}

internal fun navigationTurnAnticipationFactor(speedMetersPerSecond: Float?): Float = when {
    speedMetersPerSecond == null -> 0.30f
    speedMetersPerSecond >= 18f -> 0.46f
    speedMetersPerSecond >= 11f -> 0.44f
    speedMetersPerSecond >= 5f -> 0.40f
    speedMetersPerSecond >= 2f -> 0.30f
    else -> 0.18f
}

internal fun smoothNavigationBearing(
    previous: Float?,
    target: Float?,
    factor: Float,
    deadbandDegrees: Float = 0.45f
): Float? {
    val normalizedTarget = target?.takeIf { it.isFinite() }?.let(::normalizeNavigationBearing)
        ?: return previous?.takeIf { it.isFinite() }?.let(::normalizeNavigationBearing)
    val normalizedPrevious = previous?.takeIf { it.isFinite() }?.let(::normalizeNavigationBearing)
        ?: return normalizedTarget
    val shortestDelta = navigationShortestBearingDelta(normalizedPrevious, normalizedTarget)
    if (abs(shortestDelta) < deadbandDegrees.coerceIn(0f, 5f)) return normalizedPrevious
    return normalizeNavigationBearing(
        normalizedPrevious + shortestDelta * factor.coerceIn(0.05f, 1f)
    )
}

internal fun navigationBearingSmoothingFactor(speedMetersPerSecond: Float?): Float = when {
    speedMetersPerSecond == null -> 0.28f
    speedMetersPerSecond >= 18f -> 0.46f
    speedMetersPerSecond >= 11f -> 0.42f
    speedMetersPerSecond >= 5f -> 0.36f
    speedMetersPerSecond >= 2f -> 0.28f
    else -> 0.20f
}

internal fun navigationShortestBearingDelta(from: Float, to: Float): Float {
    val normalizedFrom = normalizeNavigationBearing(from)
    val normalizedTo = normalizeNavigationBearing(to)
    return ((normalizedTo - normalizedFrom + 540f) % 360f) - 180f
}

internal fun navigationRotationDurationMillis(
    currentBearing: Float?,
    targetBearing: Float?,
    cadenceMillis: Long?,
    fallbackMillis: Long = 420L
): Long {
    if (currentBearing == null || !currentBearing.isFinite() ||
        targetBearing == null || !targetBearing.isFinite()
    ) {
        return fallbackMillis.coerceIn(260L, 760L)
    }
    val angle = abs(navigationShortestBearingDelta(currentBearing, targetBearing))
    val cadence = cadenceMillis
        ?.takeIf { it > 0L }
        ?: fallbackMillis
    val cadenceDuration = (cadence * 0.82)
        .toLong()
        .coerceIn(260L, 720L)
    val angleDuration = (260f + angle * 9f)
        .toLong()
        .coerceIn(300L, 900L)
    return maxOf(cadenceDuration, angleDuration)
}

internal fun navigationCameraEaseInOut(fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    return if (t < 0.5f) {
        4f * t * t * t
    } else {
        val inverse = -2f * t + 2f
        1f - (inverse * inverse * inverse) / 2f
    }
}

internal fun normalizeNavigationBearing(value: Float): Float =
    ((value % 360f) + 360f) % 360f

internal fun navigationPointAtDistance(
    segment: NavigationRouteSegment,
    targetMeters: Double
): NavigationMapPoint? {
    if (!segment.renderable) return null
    if (targetMeters <= 0.0) return segment.points.first()

    var walked = 0.0
    for (index in 0 until segment.points.lastIndex) {
        val a = segment.points[index]
        val b = segment.points[index + 1]
        val leg = navigationHaversineMeters(a, b)
        if (leg <= 0.01) continue
        if (walked + leg >= targetMeters) {
            val fraction = ((targetMeters - walked) / leg).coerceIn(0.0, 1.0)
            return NavigationMapPoint(
                latitude = a.latitude + (b.latitude - a.latitude) * fraction,
                longitude = a.longitude + (b.longitude - a.longitude) * fraction
            )
        }
        walked += leg
    }
    return segment.points.last()
}

internal fun navigationBearingChangedEnough(
    previous: Float?,
    next: Float?,
    minimumDeltaDegrees: Float = 2.5f
): Boolean {
    if (next == null) return false
    if (previous == null) return true
    val delta = abs(navigationShortestBearingDelta(previous, next))
    return delta >= minimumDeltaDegrees.coerceAtLeast(0.5f)
}

internal fun navigationCameraMovementThresholdMeters(speedMetersPerSecond: Float?): Double = when {
    speedMetersPerSecond == null -> 1.6
    speedMetersPerSecond < 1.2f -> 2.4
    speedMetersPerSecond < 4f -> 1.8
    else -> 1.2
}
