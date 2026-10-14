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


data class NavigationOverviewViewportDecision(
    val center: NavigationMapPoint,
    val zoom: Int
)

fun navigationOverviewViewportDecision(
    bounds: NavigationOverviewBounds,
    insets: NavigationViewportInsets,
    viewportWidthPx: Int,
    viewportHeightPx: Int
): NavigationOverviewViewportDecision {
    val safe = insets.normalized()
    val width = viewportWidthPx.coerceAtLeast(1)
    val height = viewportHeightPx.coerceAtLeast(1)
    val availableWidth = (width - safe.leftPx - safe.rightPx).coerceAtLeast(1)
    val availableHeight = (height - safe.topPx - safe.bottomPx).coerceAtLeast(1)

    val pressure = maxOf(
        width.toDouble() / availableWidth.toDouble(),
        height.toDouble() / availableHeight.toDouble()
    ).coerceAtLeast(1.0)
    val zoomPenalty = kotlin.math.ceil(kotlin.math.log2(pressure))
        .toInt()
        .coerceIn(0, 3)

    val latitudeSpan = (bounds.north - bounds.south).coerceAtLeast(0.0)
    val longitudeSpan = (bounds.east - bounds.west).coerceAtLeast(0.0)
    val verticalBias =
        (safe.bottomPx - safe.topPx).toDouble() / (2.0 * availableHeight.toDouble())
    val horizontalBias =
        (safe.rightPx - safe.leftPx).toDouble() / (2.0 * availableWidth.toDouble())

    val shiftedCenter = NavigationMapPoint(
        latitude = (bounds.center.latitude - latitudeSpan * verticalBias)
            .coerceIn(-85.0, 85.0),
        longitude = normalizeOverviewLongitude(
            bounds.center.longitude + longitudeSpan * horizontalBias
        )
    )

    return NavigationOverviewViewportDecision(
        center = shiftedCenter,
        zoom = (navigationOverviewZoom(bounds) - zoomPenalty).coerceIn(4, 20)
    )
}

private fun normalizeOverviewLongitude(value: Double): Double {
    var normalized = value
    while (normalized > 180.0) normalized -= 360.0
    while (normalized < -180.0) normalized += 360.0
    return normalized
}
