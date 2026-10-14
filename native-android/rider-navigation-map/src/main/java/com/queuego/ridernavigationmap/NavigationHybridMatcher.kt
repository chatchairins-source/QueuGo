package com.queuego.ridernavigationmap

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

enum class NavigationMatchConfidence {
    HIGH,
    MEDIUM,
    LOW
}

data class NavigationHybridRouteMatch(
    val routeMatch: NavigationRouteMatch,
    val confidence: NavigationMatchConfidence,
    val score: Double,
    val headingDeltaDegrees: Float?
)

class NavigationHybridMatcher(
    private val maxBacktrackMeters: Double = 35.0,
    private val resetContinuityOffRouteMeters: Double = 120.0
) {
    private var lastProgressMeters: Double? = null
    private var lastElapsedRealtimeNanos: Long = 0L

    fun reset() {
        lastProgressMeters = null
        lastElapsedRealtimeNanos = 0L
    }

    fun match(
        segment: NavigationRouteSegment,
        fix: NavigationMapFix
    ): NavigationHybridRouteMatch? {
        if (!segment.renderable || !fix.point.valid) return null

        val previousProgress = lastProgressMeters
        val elapsedSeconds = elapsedSeconds(fix)
        val expectedTravelMeters = elapsedSeconds
            ?.let { seconds ->
                val speed = fix.speedMetersPerSecond
                    ?.takeIf { it.isFinite() && it >= 0f }
                    ?.toDouble()
                    ?: 0.0
                max(18.0, speed * seconds + 35.0)
            }
            ?: 70.0

        var walked = 0.0
        var best: NavigationHybridRouteMatch? = null

        for (index in 0 until segment.points.lastIndex) {
            val a = segment.points[index]
            val b = segment.points[index + 1]
            val segmentMeters = navigationHaversineMeters(a, b)
            if (segmentMeters <= 0.01) continue

            val projection = hybridProjectNavigationPoint(fix.point, a, b)
            val progress = walked + segmentMeters * projection.fraction
            val bearing = navigationBearingDegrees(a, b)
            val headingDelta = headingDeltaDegrees(
                fix = fix,
                segmentBearing = bearing
            )

            val continuityPenalty = if (
                previousProgress == null ||
                projection.distanceMeters > resetContinuityOffRouteMeters
            ) {
                0.0
            } else {
                val backtrack = (previousProgress - progress - maxBacktrackMeters).coerceAtLeast(0.0)
                val forwardJump = (progress - previousProgress - expectedTravelMeters).coerceAtLeast(0.0)
                backtrack * 3.0 + forwardJump * 0.55
            }

            val headingPenalty = (headingDelta?.toDouble() ?: 0.0) * 0.30
            val score = projection.distanceMeters + headingPenalty + continuityPenalty
            val routeMatch = NavigationRouteMatch(
                snapped = projection.point,
                offRouteMeters = projection.distanceMeters,
                progressMeters = progress,
                geometryDistanceMeters = 0.0,
                segmentBearingDegrees = bearing
            )
            val candidate = NavigationHybridRouteMatch(
                routeMatch = routeMatch,
                confidence = confidenceFor(
                    offRouteMeters = projection.distanceMeters,
                    headingDeltaDegrees = headingDelta,
                    continuityPenalty = continuityPenalty
                ),
                score = score,
                headingDeltaDegrees = headingDelta
            )
            if (best == null || candidate.score < best!!.score) {
                best = candidate
            }
            walked += segmentMeters
        }

        val chosen = best ?: return null
        val finalized = chosen.copy(
            routeMatch = chosen.routeMatch.copy(geometryDistanceMeters = walked)
        )

        if (finalized.routeMatch.offRouteMeters <= resetContinuityOffRouteMeters) {
            lastProgressMeters = finalized.routeMatch.progressMeters
        } else {
            lastProgressMeters = null
        }
        lastElapsedRealtimeNanos = fix.elapsedRealtimeNanos
        return finalized
    }

    private fun elapsedSeconds(fix: NavigationMapFix): Double? {
        val nextNanos = fix.elapsedRealtimeNanos
        val previousNanos = lastElapsedRealtimeNanos
        if (nextNanos <= 0L || previousNanos <= 0L || nextNanos <= previousNanos) return null
        return (nextNanos - previousNanos) / 1_000_000_000.0
    }

    private fun headingDeltaDegrees(
        fix: NavigationMapFix,
        segmentBearing: Float
    ): Float? {
        val speed = fix.speedMetersPerSecond
            ?.takeIf { it.isFinite() && it >= 0f }
            ?: return null
        if (speed < 2f) return null
        val bearing = navigationUsableGpsBearing(fix) ?: return null
        val b = normalizeNavigationBearing(segmentBearing)
        return abs(((bearing - b + 540f) % 360f) - 180f)
    }

    private fun confidenceFor(
        offRouteMeters: Double,
        headingDeltaDegrees: Float?,
        continuityPenalty: Double
    ): NavigationMatchConfidence {
        val headingGood = headingDeltaDegrees == null || headingDeltaDegrees <= 35f
        val headingAcceptable = headingDeltaDegrees == null || headingDeltaDegrees <= 75f
        return when {
            offRouteMeters <= 18.0 && headingGood && continuityPenalty <= 10.0 ->
                NavigationMatchConfidence.HIGH
            offRouteMeters <= 50.0 && headingAcceptable && continuityPenalty <= 70.0 ->
                NavigationMatchConfidence.MEDIUM
            else -> NavigationMatchConfidence.LOW
        }
    }
}

private data class NavigationHybridProjection(
    val point: NavigationMapPoint,
    val distanceMeters: Double,
    val fraction: Double
)

private fun hybridProjectNavigationPoint(
    point: NavigationMapPoint,
    a: NavigationMapPoint,
    b: NavigationMapPoint
): NavigationHybridProjection {
    val referenceLat = Math.toRadians(point.latitude)
    val metersPerDegreeLat = 111_320.0
    val metersPerDegreeLon = 111_320.0 * cos(referenceLat).coerceAtLeast(0.01)
    val ax = (a.longitude - point.longitude) * metersPerDegreeLon
    val ay = (a.latitude - point.latitude) * metersPerDegreeLat
    val bx = (b.longitude - point.longitude) * metersPerDegreeLon
    val by = (b.latitude - point.latitude) * metersPerDegreeLat
    val dx = bx - ax
    val dy = by - ay
    val denominator = dx * dx + dy * dy
    val fraction = if (denominator <= 0.0001) {
        0.0
    } else {
        (-(ax * dx + ay * dy) / denominator).coerceIn(0.0, 1.0)
    }
    val projectedX = ax + dx * fraction
    val projectedY = ay + dy * fraction
    val snapped = NavigationMapPoint(
        latitude = a.latitude + (b.latitude - a.latitude) * fraction,
        longitude = a.longitude + (b.longitude - a.longitude) * fraction
    )
    return NavigationHybridProjection(
        point = snapped,
        distanceMeters = sqrt(projectedX * projectedX + projectedY * projectedY),
        fraction = fraction
    )
}
