package com.queuego.ridernavigationmap

data class NavigationRouteInstruction(
    val sequence: Int,
    val turnCode: Int,
    val roadName: String,
    val sectionDistanceMeters: Double,
    val sectionDurationSeconds: Double
)

data class NavigationSpeedLimitInterval(
    val startGeometryMeters: Double,
    val endGeometryMeters: Double,
    val maxspeedKph: Double,
    val maxspeedRaw: String,
    val source: String,
    val osmWayId: Long? = null
)

data class NavigationRouteLeg(
    val segment: NavigationRouteSegment,
    val distanceMeters: Double,
    val durationSeconds: Double?,
    val instructions: List<NavigationRouteInstruction> = emptyList(),
    val speedLimitIntervals: List<NavigationSpeedLimitInterval> = emptyList()
)

interface NavigationRouteProvider {
    suspend fun route(
        from: NavigationMapPoint,
        stop: NavigationMapStop,
        segmentId: String
    ): NavigationRouteLeg
}
