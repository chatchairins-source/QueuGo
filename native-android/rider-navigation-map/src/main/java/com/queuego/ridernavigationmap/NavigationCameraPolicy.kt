package com.queuego.ridernavigationmap

import kotlin.math.abs

private const val ROUTE_BEARING_MAX_OFF_ROUTE_METERS = 60.0

data class NavigationCameraDecision(
    val center: NavigationMapPoint,
    val zoom: Int,
    val bearingDegrees: Float?,
    val routeMatch: NavigationRouteMatch?,
    val remainingRouteMeters: Double?
)

internal fun navigationCameraDecision(
    state: RiderNavigationMapState,
    previousBearingDegrees: Float?
): NavigationCameraDecision? {
    val fix = state.rider?.takeIf { it.point.valid } ?: return null
    val activeRoute = state.styledRemainingRoutes()
        .firstOrNull { it.style == NavigationRouteStyle.ACTIVE }
    val match = activeRoute?.let { matchNavigationRoute(it, fix.point) }
    val reliableRouteMatch = match?.takeIf { it.offRouteMeters <= ROUTE_BEARING_MAX_OFF_ROUTE_METERS }

    val gpsBearing = fix.bearingDegrees
        ?.takeIf { it.isFinite() }
        ?.let(::normalizeNavigationBearing)
    val routeBearing = reliableRouteMatch?.segmentBearingDegrees
        ?.takeIf { it.isFinite() }
        ?.let(::normalizeNavigationBearing)
    val speed = fix.speedMetersPerSecond
        ?.takeIf { it.isFinite() && it >= 0f }

    // At driving speed GPS course is authoritative. At walking/standstill speed the
    // course sensor becomes noisy, so prefer the matched road direction when available.
    val desiredBearing = when {
        speed != null && speed >= 2.2f -> gpsBearing ?: routeBearing
        routeBearing != null -> routeBearing
        else -> gpsBearing
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

internal fun smoothNavigationBearing(
    previous: Float?,
    target: Float?,
    factor: Float
): Float? {
    val normalizedTarget = target?.takeIf { it.isFinite() }?.let(::normalizeNavigationBearing)
        ?: return previous?.takeIf { it.isFinite() }?.let(::normalizeNavigationBearing)
    val normalizedPrevious = previous?.takeIf { it.isFinite() }?.let(::normalizeNavigationBearing)
        ?: return normalizedTarget
    val shortestDelta = ((normalizedTarget - normalizedPrevious + 540f) % 360f) - 180f
    return normalizeNavigationBearing(normalizedPrevious + shortestDelta * factor.coerceIn(0.05f, 1f))
}

internal fun navigationBearingSmoothingFactor(speedMetersPerSecond: Float?): Float = when {
    speedMetersPerSecond == null -> 0.35f
    speedMetersPerSecond >= 18f -> 0.60f
    speedMetersPerSecond >= 11f -> 0.52f
    speedMetersPerSecond >= 5f -> 0.44f
    speedMetersPerSecond >= 2f -> 0.34f
    else -> 0.24f
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

internal fun navigationBearingChangedEnough(previous: Float?, next: Float?): Boolean {
    if (next == null) return false
    if (previous == null) return true
    val delta = abs(((next - previous + 540f) % 360f) - 180f)
    return delta >= 1.5f
}
