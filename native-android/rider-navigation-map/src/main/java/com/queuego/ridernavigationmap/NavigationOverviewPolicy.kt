package com.queuego.ridernavigationmap

data class NavigationOverviewBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val center: NavigationMapPoint,
    val spanMeters: Double
)

fun navigationOverviewBounds(state: RiderNavigationMapState): NavigationOverviewBounds? {
    val points = buildList {
        state.rider?.point?.takeIf { it.valid }?.let(::add)
        state.remainingStops().forEach { add(it.point) }
        state.styledRemainingRoutes().forEach { route ->
            route.points.forEach { point -> if (point.valid) add(point) }
        }
    }
    if (points.isEmpty()) return null

    val south = points.minOf { it.latitude }
    val north = points.maxOf { it.latitude }
    val west = points.minOf { it.longitude }
    val east = points.maxOf { it.longitude }
    val center = NavigationMapPoint(
        latitude = (south + north) / 2.0,
        longitude = (west + east) / 2.0
    )

    val verticalMeters = navigationHaversineMeters(
        NavigationMapPoint(south, center.longitude),
        NavigationMapPoint(north, center.longitude)
    )
    val horizontalMeters = navigationHaversineMeters(
        NavigationMapPoint(center.latitude, west),
        NavigationMapPoint(center.latitude, east)
    )

    return NavigationOverviewBounds(
        south = south,
        west = west,
        north = north,
        east = east,
        center = center,
        spanMeters = maxOf(verticalMeters, horizontalMeters)
    )
}

fun navigationOverviewZoom(bounds: NavigationOverviewBounds): Int = when {
    bounds.spanMeters >= 50_000.0 -> 10
    bounds.spanMeters >= 25_000.0 -> 11
    bounds.spanMeters >= 12_000.0 -> 12
    bounds.spanMeters >= 6_000.0 -> 13
    bounds.spanMeters >= 3_000.0 -> 14
    bounds.spanMeters >= 1_500.0 -> 15
    bounds.spanMeters >= 700.0 -> 16
    else -> 17
}
