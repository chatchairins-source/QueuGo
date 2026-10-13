package com.queuego.ridernavigationmap

import kotlin.math.max

private const val WEAK_GPS_ACCURACY_METERS = 75f
private const val BASE_OFF_ROUTE_THRESHOLD_METERS = 55.0
private const val OFF_ROUTE_ACCURACY_MULTIPLIER = 1.8
private const val DEFAULT_ARRIVAL_METERS = 28.0
private const val ARRIVAL_ACCURACY_MULTIPLIER = 1.4
private const val MAX_ARRIVAL_METERS = 45.0

data class NavigationGuidanceState(
    val snappedPoint: NavigationMapPoint? = null,
    val headingDegrees: Float? = null,
    val weakGps: Boolean = false,
    val offRoute: Boolean = false,
    val offRouteMeters: Double? = null,
    val progressMeters: Double? = null,
    val distanceRemainingMeters: Double? = null,
    val etaSeconds: Double? = null,
    val nextInstruction: NavigationRouteInstruction? = null,
    val distanceToInstructionMeters: Double? = null,
    val arrived: Boolean = false,
    val shouldReroute: Boolean = false
)

class NavigationProgressTracker(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val requiredOffRouteSamples: Int = 2,
    private val rerouteCooldownMillis: Long = 8_000L
) {
    private var consecutiveOffRouteSamples = 0
    private var lastRerouteAtMillis: Long? = null

    fun reset() {
        consecutiveOffRouteSamples = 0
        lastRerouteAtMillis = null
    }

    fun update(
        fix: NavigationMapFix,
        leg: NavigationRouteLeg,
        stop: NavigationMapStop
    ): NavigationGuidanceState {
        if (!fix.point.valid || !stop.point.valid || !leg.segment.renderable) {
            consecutiveOffRouteSamples = 0
            return NavigationGuidanceState()
        }

        val accuracy = fix.accuracyMeters?.takeIf { it.isFinite() && it >= 0f }
        val weakGps = accuracy != null && accuracy > WEAK_GPS_ACCURACY_METERS
        val match = matchNavigationRoute(leg.segment, fix.point) ?: return NavigationGuidanceState(
            weakGps = weakGps
        )

        val geometryLength = match.geometryDistanceMeters.coerceAtLeast(1.0)
        val progressRatio = (match.progressMeters / geometryLength).coerceIn(0.0, 1.0)
        val traveledServiceMeters = leg.distanceMeters.coerceAtLeast(0.0) * progressRatio
        val remaining = (leg.distanceMeters - traveledServiceMeters).coerceAtLeast(0.0)
        val eta = leg.durationSeconds
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { total -> (total * (1.0 - progressRatio)).coerceAtLeast(0.0) }

        val offRouteThreshold = max(
            BASE_OFF_ROUTE_THRESHOLD_METERS,
            (accuracy?.toDouble() ?: 15.0) * OFF_ROUTE_ACCURACY_MULTIPLIER
        )
        val offRoute = !weakGps && match.offRouteMeters > offRouteThreshold
        consecutiveOffRouteSamples = if (offRoute) consecutiveOffRouteSamples + 1 else 0

        val now = nowMillis()
        val cooldownSatisfied = lastRerouteAtMillis?.let { now - it >= rerouteCooldownMillis } ?: true
        val shouldReroute = offRoute &&
            consecutiveOffRouteSamples >= requiredOffRouteSamples.coerceAtLeast(1) &&
            cooldownSatisfied
        if (shouldReroute) {
            consecutiveOffRouteSamples = 0
            lastRerouteAtMillis = now
        }

        val next = navigationNextInstruction(leg, traveledServiceMeters)
        val directToStop = navigationHaversineMeters(fix.point, stop.point)
        val arrivalThreshold = max(
            DEFAULT_ARRIVAL_METERS,
            ((accuracy?.toDouble() ?: 8.0) * ARRIVAL_ACCURACY_MULTIPLIER)
                .coerceAtMost(MAX_ARRIVAL_METERS)
        )
        val arrived = !weakGps &&
            directToStop <= arrivalThreshold &&
            remaining <= max(60.0, arrivalThreshold * 1.5)

        return NavigationGuidanceState(
            snappedPoint = match.snapped,
            headingDegrees = fix.bearingDegrees
                ?.takeIf { it.isFinite() }
                ?.let(::normalizeNavigationBearing)
                ?: match.segmentBearingDegrees,
            weakGps = weakGps,
            offRoute = offRoute,
            offRouteMeters = match.offRouteMeters,
            progressMeters = traveledServiceMeters,
            distanceRemainingMeters = remaining,
            etaSeconds = eta,
            nextInstruction = next.first,
            distanceToInstructionMeters = next.second,
            arrived = arrived,
            shouldReroute = shouldReroute
        )
    }
}

internal fun navigationNextInstruction(
    leg: NavigationRouteLeg,
    traveledMeters: Double
): Pair<NavigationRouteInstruction?, Double?> {
    var actionAt = 0.0
    val traveled = traveledMeters.coerceAtLeast(0.0)
    for (index in leg.instructions.indices) {
        val instruction = leg.instructions[index]
        if (index > 0 &&
            navigationTurnPhrase(instruction.turnCode) != null &&
            actionAt + 15.0 >= traveled
        ) {
            return instruction to (actionAt - traveled).coerceAtLeast(0.0)
        }
        actionAt += instruction.sectionDistanceMeters.coerceAtLeast(0.0)
    }
    val arrival = NavigationRouteInstruction(
        sequence = leg.instructions.size,
        turnCode = 9,
        roadName = "",
        sectionDistanceMeters = 0.0,
        sectionDurationSeconds = 0.0
    )
    return arrival to (leg.distanceMeters - traveled).coerceAtLeast(0.0)
}

fun navigationTurnPhrase(turnCode: Int): String? = when (turnCode) {
    0 -> "เลี้ยวซ้าย"
    1 -> "เลี้ยวขวา"
    2 -> "เบี่ยงซ้าย"
    3 -> "เบี่ยงขวา"
    5 -> "ตรงไป"
    6 -> "ขึ้นทางด่วน"
    9 -> "ถึงจุดหมายแล้ว"
    11 -> "ขึ้นเรือ"
    else -> null
}
