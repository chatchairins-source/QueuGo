package com.queuego.ridernavigationmap

data class NavigationMapPoint(val latitude: Double, val longitude: Double) {
    val valid: Boolean get() = latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0
}

data class NavigationMapFix(
    val point: NavigationMapPoint,
    val bearingDegrees: Float? = null,
    val accuracyMeters: Float? = null,
    val elapsedRealtimeNanos: Long = 0L
)

enum class NavigationStopKind { PICKUP, DROPOFF }

data class NavigationMapStop(
    val id: String,
    val sequence: Int,
    val kind: NavigationStopKind,
    val point: NavigationMapPoint,
    val completed: Boolean = false,
    val label: String = ""
)

enum class NavigationRouteStyle { ACTIVE, FUTURE }

data class NavigationRouteSegment(
    val id: String,
    val toStopId: String,
    val points: List<NavigationMapPoint>,
    val style: NavigationRouteStyle = NavigationRouteStyle.FUTURE
) {
    val renderable: Boolean get() = id.isNotBlank() && toStopId.isNotBlank() &&
        points.size > 1 && points.all { it.valid }
}

data class NavigationMapCamera(
    val point: NavigationMapPoint,
    val zoom: Int,
    val bearingDegrees: Float? = null
)

data class RiderNavigationMapState(
    val rider: NavigationMapFix? = null,
    val stops: List<NavigationMapStop> = emptyList(),
    val routes: List<NavigationRouteSegment> = emptyList(),
    val activeStopId: String? = null,
    val followRider: Boolean = true,
    val followZoom: Int = 17
)

data class NavigationMapDiagnostics(
    val viewCreatedCount: Int,
    val mapCreatedCount: Int,
    val routeLayerRebuildCount: Int,
    val stopMutationCount: Int,
    val renderPassCount: Int = 0
)

internal fun RiderNavigationMapState.remainingStops(): List<NavigationMapStop> =
    stops.asSequence()
        .filter { !it.completed && it.point.valid && it.id.isNotBlank() && it.sequence > 0 }
        .sortedWith(compareBy<NavigationMapStop> { it.sequence }.thenBy { it.id })
        .toList()

internal fun RiderNavigationMapState.resolvedActiveStopId(): String? {
    val remaining = remainingStops()
    return activeStopId?.takeIf { requested -> remaining.any { it.id == requested } }
        ?: remaining.firstOrNull()?.id
}

internal fun RiderNavigationMapState.styledRemainingRoutes(): List<NavigationRouteSegment> {
    val remainingStopIds = remainingStops().mapTo(linkedSetOf()) { it.id }
    val active = resolvedActiveStopId()
    return routes.asSequence()
        .filter { it.renderable && it.toStopId in remainingStopIds }
        .map { segment ->
            segment.copy(
                style = if (segment.toStopId == active) NavigationRouteStyle.ACTIVE
                else NavigationRouteStyle.FUTURE
            )
        }
        .toList()
}
