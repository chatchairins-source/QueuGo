package com.queuego.ridernavigationmap

data class NavigationRouteInstruction(
    val sequence: Int,
    val turnCode: Int,
    val roadName: String,
    val sectionDistanceMeters: Double,
    val sectionDurationSeconds: Double
)

data class NavigationRouteLeg(
    val segment: NavigationRouteSegment,
    val distanceMeters: Double,
    val durationSeconds: Double?,
    val instructions: List<NavigationRouteInstruction> = emptyList()
)

interface NavigationRouteProvider {
    suspend fun route(
        from: NavigationMapPoint,
        stop: NavigationMapStop,
        segmentId: String
    ): NavigationRouteLeg
}
