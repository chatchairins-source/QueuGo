package com.queuego.ridernavigationmap

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class NavigationRouteMatch(
    val snapped: NavigationMapPoint,
    val offRouteMeters: Double,
    val progressMeters: Double,
    val geometryDistanceMeters: Double,
    val segmentBearingDegrees: Float?
)

internal fun matchNavigationRoute(segment: NavigationRouteSegment, point: NavigationMapPoint): NavigationRouteMatch? {
    if (!segment.renderable || !point.valid) return null
    var bestDistance = Double.POSITIVE_INFINITY
    var bestPoint: NavigationMapPoint? = null
    var bestProgress = 0.0
    var bestBearing: Float? = null
    var walked = 0.0

    for (index in 0 until segment.points.lastIndex) {
        val a = segment.points[index]
        val b = segment.points[index + 1]
        val segmentMeters = navigationHaversineMeters(a, b)
        if (segmentMeters <= 0.01) continue
        val projection = projectNavigationPoint(point, a, b)
        if (projection.distanceMeters < bestDistance) {
            bestDistance = projection.distanceMeters
            bestPoint = projection.point
            bestProgress = walked + segmentMeters * projection.fraction
            bestBearing = navigationBearingDegrees(a, b)
        }
        walked += segmentMeters
    }

    val snapped = bestPoint ?: return null
    return NavigationRouteMatch(snapped, bestDistance, bestProgress, walked, bestBearing)
}

private data class NavigationProjection(
    val point: NavigationMapPoint,
    val distanceMeters: Double,
    val fraction: Double
)

private fun projectNavigationPoint(point: NavigationMapPoint, a: NavigationMapPoint, b: NavigationMapPoint): NavigationProjection {
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
    val fraction = if (denominator <= 0.0001) 0.0 else (-(ax * dx + ay * dy) / denominator).coerceIn(0.0, 1.0)
    val projectedX = ax + dx * fraction
    val projectedY = ay + dy * fraction
    val snapped = NavigationMapPoint(
        latitude = a.latitude + (b.latitude - a.latitude) * fraction,
        longitude = a.longitude + (b.longitude - a.longitude) * fraction
    )
    return NavigationProjection(snapped, sqrt(projectedX * projectedX + projectedY * projectedY), fraction)
}

internal fun navigationHaversineMeters(a: NavigationMapPoint, b: NavigationMapPoint): Double {
    val earthRadiusMeters = 6_371_000.0
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val sinLat = sin(dLat / 2.0)
    val sinLon = sin(dLon / 2.0)
    val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon
    return earthRadiusMeters * 2.0 * atan2(sqrt(h), sqrt((1.0 - h).coerceAtLeast(0.0)))
}

internal fun navigationBearingDegrees(a: NavigationMapPoint, b: NavigationMapPoint): Float {
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
}
