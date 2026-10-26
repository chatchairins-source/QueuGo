package com.queuego.ridernavigationmap

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val ROUTE_BEARING_MAX_OFF_ROUTE_METERS = 25.0

data class NavigationCameraDecision(
    val center: NavigationMapPoint,
    val zoom: Double,
    val bearingDegrees: Float?,
    val tiltDegrees: Float,
    val routeMatch: NavigationRouteMatch?,
    val remainingRouteMeters: Double?
)

internal fun navigationCameraDecision(
    state: RiderNavigationMapState,
    previousBearingDegrees: Float?,
    followPuckBearing: Boolean = false
): NavigationCameraDecision? {
    val fix = state.rider?.takeIf { it.point.valid } ?: return null
    val gpsBearing = navigationUsableGpsBearing(fix)
    val speed = fix.speedMetersPerSecond
        ?.takeIf { it.isFinite() && it >= 0f }
    val navigationActive = state.perspectiveCameraEnabled || state.remainingStops().isNotEmpty()
    if (!navigationActive) {
        val desiredFlatBearing = if (state.followRider) {
            when {
                gpsBearing != null && speed != null && speed >= 0.83f -> gpsBearing
                speed == null || speed < 0.83f -> previousBearingDegrees
                else -> gpsBearing ?: previousBearingDegrees
            }
        } else {
            0f
        }
        val flatBearing = if (state.followRider) {
            smoothNavigationBearing(
                previous = previousBearingDegrees,
                target = desiredFlatBearing,
                factor = navigationBearingSmoothingFactor(speed)
            ) ?: desiredFlatBearing ?: 0f
        } else {
            0f
        }
        val flatZoom = if (state.followRider) {
            navigationZoom(
                baseZoom = state.followZoom,
                speedMetersPerSecond = speed,
                remainingRouteMeters = null,
                accuracyMeters = fix.accuracyMeters
            )
        } else {
            state.followZoom.coerceIn(14, 20).toDouble()
        }
        return NavigationCameraDecision(
            center = fix.point,
            zoom = flatZoom,
            bearingDegrees = flatBearing,
            tiltDegrees = 0f,
            routeMatch = null,
            remainingRouteMeters = null
        )
    }

    val activeRoute = state.styledRemainingRoutes()
        .firstOrNull { it.style == NavigationRouteStyle.ACTIVE }
    val match = activeRoute?.let {
        matchNavigationRoute(
            segment = it,
            point = fix.point,
            bearingDegrees = gpsBearing,
            speedMetersPerSecond = speed
        )
    }
    val reliableRouteMatch = match?.takeIf { it.offRouteMeters <= ROUTE_BEARING_MAX_OFF_ROUTE_METERS }


    val routeBearing = if (activeRoute != null && reliableRouteMatch != null) {
        navigationRouteBearing(
            segment = activeRoute,
            match = reliableRouteMatch,
            speedMetersPerSecond = speed
        )
    } else null

    // A nearest-segment match can land on the opposite carriageway or a U-turn leg.
    // Reject that route bearing only when both the live GNSS course and the previous
    // camera course agree that the Rider is travelling the other way. This preserves
    // road snapping through one noisy GPS course while preventing 180-degree camera flips.
    val routeBearingDelta = if (gpsBearing != null && routeBearing != null) {
        abs(navigationShortestBearingDelta(gpsBearing, routeBearing))
    } else null
    val previousRouteDelta = if (
        previousBearingDegrees != null &&
        previousBearingDegrees.isFinite() &&
        routeBearing != null
    ) {
        abs(navigationShortestBearingDelta(previousBearingDegrees, routeBearing))
    } else null
    val routeBearingOpposesMotion =
        speed != null && speed >= 2f &&
            gpsBearing != null &&
            routeBearingDelta != null && (
                routeBearingDelta > 110f ||
                    (routeBearingDelta > 75f &&
                        (previousRouteDelta == null || previousRouteDelta > 75f))
                )
    val routeBearingUsable = routeBearing != null && !routeBearingOpposesMotion

    val desiredBearing = when {
        speed == null || speed < 0.83f -> previousBearingDegrees
        followPuckBearing && gpsBearing != null -> gpsBearing
        routeBearingUsable -> routeBearing
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
        accuracyMeters = fix.accuracyMeters,
        nextTurnDistanceMeters = state.nextTurnDistanceMeters
    )
    val tilt = if (state.perspectiveCameraEnabled && reliableRouteMatch == null) {
        navigationFreeDriveTilt(
            speedMetersPerSecond = speed,
            accuracyMeters = fix.accuracyMeters
        )
    } else {
        navigationTilt(
            speedMetersPerSecond = speed,
            remainingRouteMeters = remaining,
            accuracyMeters = fix.accuracyMeters
        )
    }

    val lookAheadMeters = navigationLookAheadMeters(speed, remaining)
    val center = if (
        activeRoute != null &&
        match != null &&
        match.offRouteMeters <= ROUTE_BEARING_MAX_OFF_ROUTE_METERS &&
        routeBearingUsable
    ) {
        navigationPointAtDistance(
            activeRoute,
            (match.progressMeters + lookAheadMeters).coerceAtMost(match.geometryDistanceMeters)
        ) ?: fix.point
    } else if (state.perspectiveCameraEnabled) {
        // Keep the Rider puck in the lower navigation zone even while the route is
        // unavailable/idle. The map looks ahead in the stable travel direction instead
        // of snapping the Rider back to dead-center between route updates.
        val forwardDistance = navigationFreeDriveLookAheadMeters(speed)
        navigationProjectForward(
            point = fix.point,
            bearingDegrees = smoothedBearing,
            distanceMeters = forwardDistance
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
    accuracyMeters: Float?,
    nextTurnDistanceMeters: Double? = null
): Double {
    val base = baseZoom.coerceIn(14, 20).toDouble()
    val speed = speedMetersPerSecond
        ?.takeIf { it.isFinite() && it >= 0f }
        ?.toDouble()
    var target = when {
        remainingRouteMeters != null && remainingRouteMeters <= 60.0 -> 19.0
        remainingRouteMeters != null && remainingRouteMeters <= 180.0 -> 18.0
        speed == null -> base
        speed <= 0.8 -> maxOf(base, 18.4)
        speed <= 5.0 -> 18.4 - (speed - 0.8) * (1.4 / 4.2)
        speed <= 11.0 -> 17.0 - (speed - 5.0) / 6.0
        speed <= 18.0 -> 16.0 - (speed - 11.0) / 7.0
        else -> 15.0
    }
    target = kotlin.math.round(target * 10.0) / 10.0
    if (accuracyMeters != null && accuracyMeters.isFinite() && accuracyMeters > 60f) {
        target = maxOf(14.0, target - 1.0)
    }
    // Web does not zoom from next-turn distance independently of route remaining distance.
    @Suppress("UNUSED_VARIABLE")
    val ignoredNextTurnDistanceMeters = nextTurnDistanceMeters
    return target.coerceIn(14.0, 20.0)
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
        speedMetersPerSecond >= 18f -> 52f
        speedMetersPerSecond >= 11f -> 49f
        speedMetersPerSecond >= 5f -> 45f
        speedMetersPerSecond >= 2f -> 40f
        else -> 34f
    }
    return target.coerceIn(0f, 55f)
}

internal fun navigationFreeDriveTilt(
    speedMetersPerSecond: Float?,
    accuracyMeters: Float?
): Float {
    val target = when {
        accuracyMeters != null && accuracyMeters.isFinite() && accuracyMeters > 80f -> 28f
        speedMetersPerSecond == null -> 44f
        speedMetersPerSecond >= 18f -> 53f
        speedMetersPerSecond >= 11f -> 51f
        speedMetersPerSecond >= 5f -> 48f
        speedMetersPerSecond >= 2f -> 44f
        else -> 40f
    }
    return target.coerceIn(0f, 55f)
}

internal fun smoothNavigationTilt(
    previous: Float,
    target: Float,
    maxStepDegrees: Float = 4f
): Float {
    val safePrevious = previous.takeIf { it.isFinite() } ?: 0f
    val safeTarget = target.takeIf { it.isFinite() }?.coerceIn(0f, 55f) ?: safePrevious
    val delta = safeTarget - safePrevious
    val maxStep = maxStepDegrees.coerceIn(0.5f, 12f)
    return when {
        abs(delta) <= maxStep -> safeTarget
        delta > 0f -> safePrevious + maxStep
        else -> safePrevious - maxStep
    }.coerceIn(0f, 55f)
}

internal fun navigationTiltChangedEnough(
    previous: Float?,
    next: Float,
    minimumDeltaDegrees: Float = 0.5f
): Boolean {
    if (!next.isFinite()) return false
    if (previous == null || !previous.isFinite()) return true
    return abs(next - previous) >= minimumDeltaDegrees.coerceAtLeast(0.25f)
}

internal fun navigationFreeDriveLookAheadMeters(speedMetersPerSecond: Float?): Double = when {
    speedMetersPerSecond == null -> 25.0
    speedMetersPerSecond >= 18f -> 80.0
    speedMetersPerSecond >= 11f -> 65.0
    speedMetersPerSecond >= 5f -> 50.0
    speedMetersPerSecond >= 2f -> 32.0
    else -> 15.0
}

internal fun navigationProjectForward(
    point: NavigationMapPoint,
    bearingDegrees: Float?,
    distanceMeters: Double
): NavigationMapPoint? {
    if (!point.valid || bearingDegrees == null || !bearingDegrees.isFinite() || distanceMeters <= 0.0) {
        return null
    }
    val earthRadiusMeters = 6_371_000.0
    val angularDistance = distanceMeters / earthRadiusMeters
    val bearing = Math.toRadians(normalizeNavigationBearing(bearingDegrees).toDouble())
    val lat1 = Math.toRadians(point.latitude)
    val lon1 = Math.toRadians(point.longitude)
    val sinLat1 = sin(lat1)
    val cosLat1 = cos(lat1)
    val sinDistance = sin(angularDistance)
    val cosDistance = cos(angularDistance)
    val lat2 = asin(
        sinLat1 * cosDistance + cosLat1 * sinDistance * cos(bearing)
    )
    val lon2 = lon1 + atan2(
        sin(bearing) * sinDistance * cosLat1,
        cosDistance - sinLat1 * sin(lat2)
    )
    return NavigationMapPoint(
        latitude = Math.toDegrees(lat2),
        longitude = ((Math.toDegrees(lon2) + 540.0) % 360.0) - 180.0
    ).takeIf { it.valid }
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
    speedMetersPerSecond == null -> 0.32f
    speedMetersPerSecond >= 18f -> 0.62f
    speedMetersPerSecond >= 11f -> 0.58f
    speedMetersPerSecond >= 5f -> 0.50f
    speedMetersPerSecond >= 2f -> 0.36f
    else -> 0.22f
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
    speedMetersPerSecond == null -> 0.30f
    speedMetersPerSecond >= 18f -> 0.68f
    speedMetersPerSecond >= 11f -> 0.62f
    speedMetersPerSecond >= 5f -> 0.52f
    speedMetersPerSecond >= 2f -> 0.38f
    else -> 0.24f
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
    fallbackMillis: Long = 520L,
    speedMetersPerSecond: Float? = null
): Long {
    val speed = speedMetersPerSecond?.takeIf { it.isFinite() && it >= 0f }
    val minDuration: Long
    val maxDuration: Long
    val cadenceFactor: Double
    val angleBase: Float
    val angleGain: Float
    when {
        speed == null -> { minDuration = 420L; maxDuration = 900L; cadenceFactor = 0.90; angleBase = 340f; angleGain = 6.2f }
        speed >= 18f -> { minDuration = 220L; maxDuration = 460L; cadenceFactor = 0.58; angleBase = 170f; angleGain = 3.7f }
        speed >= 11f -> { minDuration = 260L; maxDuration = 540L; cadenceFactor = 0.64; angleBase = 200f; angleGain = 4.1f }
        speed >= 5f -> { minDuration = 310L; maxDuration = 660L; cadenceFactor = 0.72; angleBase = 240f; angleGain = 4.8f }
        speed >= 2f -> { minDuration = 360L; maxDuration = 760L; cadenceFactor = 0.80; angleBase = 290f; angleGain = 5.4f }
        else -> { minDuration = 420L; maxDuration = 900L; cadenceFactor = 0.90; angleBase = 340f; angleGain = 6.2f }
    }
    if (currentBearing == null || !currentBearing.isFinite() ||
        targetBearing == null || !targetBearing.isFinite()
    ) {
        return fallbackMillis.coerceIn(minDuration, maxDuration)
    }
    val angle = abs(navigationShortestBearingDelta(currentBearing, targetBearing))
    val cadence = cadenceMillis?.takeIf { it > 0L } ?: fallbackMillis
    val cadenceDuration = (cadence * cadenceFactor).toLong().coerceIn(minDuration, maxDuration)
    val angleDuration = (angleBase + angle * angleGain).toLong().coerceIn(minDuration, maxDuration)
    return maxOf(cadenceDuration, angleDuration).coerceIn(minDuration, maxDuration)
}

internal fun navigationCameraEaseInOut(fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    val cubic = if (t < 0.5f) {
        4f * t * t * t
    } else {
        val inverse = -2f * t + 2f
        1f - (inverse * inverse * inverse) / 2f
    }
    // Retain a small linear component so consecutive 1 Hz GPS samples do not
    // decelerate the camera all the way to zero between every update.
    return 0.24f * t + 0.76f * cubic
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

internal fun navigationCameraMovementThresholdMeters(speedMetersPerSecond: Float?): Double {
    @Suppress("UNUSED_VARIABLE")
    val ignoredSpeedMetersPerSecond = speedMetersPerSecond
    return 0.8
}

internal fun navigationScreenAnchorRatio(speedMetersPerSecond: Float?): Double = when {
    speedMetersPerSecond == null -> 0.68
    speedMetersPerSecond >= 18f -> 0.76
    speedMetersPerSecond >= 11f -> 0.74
    speedMetersPerSecond >= 5f -> 0.72
    speedMetersPerSecond >= 2f -> 0.69
    else -> 0.66
}

internal fun navigationFollowCameraPadding(
    insets: NavigationViewportInsets,
    viewportHeightPx: Int,
    speedMetersPerSecond: Float?
): DoubleArray {
    val safe = insets.normalized()
    val height = viewportHeightPx.coerceAtLeast(1).toDouble()
    val inset = safe.bottomPx.toDouble().coerceIn(0.0, height * 0.6)
    val desiredY = maxOf(
        height * 0.46,
        minOf(height * navigationScreenAnchorRatio(speedMetersPerSecond), height - inset - 56.0)
    )
    val requiredDifference = 2.0 * desiredY - height
    val baseDifference = safe.topPx.toDouble() - safe.bottomPx.toDouble()
    val top: Double
    val bottom: Double
    if (requiredDifference >= baseDifference) {
        bottom = safe.bottomPx.toDouble()
        top = bottom + requiredDifference
    } else {
        top = safe.topPx.toDouble()
        bottom = top - requiredDifference
    }
    return doubleArrayOf(
        safe.leftPx.toDouble(),
        top.coerceAtLeast(safe.topPx.toDouble()),
        safe.rightPx.toDouble(),
        bottom.coerceAtLeast(safe.bottomPx.toDouble())
    )
}
