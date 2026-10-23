package com.queuego.ridernavigationmap

import kotlin.math.max

private const val WEAK_GPS_ACCURACY_METERS = 35f
private const val BASE_OFF_ROUTE_THRESHOLD_METERS = 30.0
private const val MISSED_MANEUVER_WINDOW_METERS = 90.0
private const val MISSED_MANEUVER_MIN_SPEED_METERS_PER_SECOND = 2.0f
private const val MISSED_MANEUVER_REROUTE_COOLDOWN_MILLIS = 3_000L
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
    val followingInstruction: NavigationRouteInstruction? = null,
    val distanceToFollowingInstructionMeters: Double? = null,
    val arrived: Boolean = false,
    val shouldReroute: Boolean = false
)

class NavigationProgressTracker(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val requiredOffRouteMillis: Long = 3_000L,
    private val rerouteCooldownMillis: Long = 20_000L,
    private val routeMatcher: NavigationHybridMatcher = NavigationHybridMatcher()
) {
    private var offRouteSinceMillis: Long? = null
    private var lastRerouteAtMillis: Long? = null

    fun reset() {
        offRouteSinceMillis = null
        lastRerouteAtMillis = null
        routeMatcher.reset()
    }

    fun update(
        fix: NavigationMapFix,
        leg: NavigationRouteLeg,
        stop: NavigationMapStop
    ): NavigationGuidanceState {
        if (!fix.point.valid || !stop.point.valid || !leg.segment.renderable) {
            offRouteSinceMillis = null
            return NavigationGuidanceState()
        }

        val accuracy = fix.accuracyMeters?.takeIf { it.isFinite() && it >= 0f }
        val weakGps = accuracy != null && accuracy > WEAK_GPS_ACCURACY_METERS
        val hybridMatch = routeMatcher.match(leg.segment, fix)
            ?: return NavigationGuidanceState(weakGps = weakGps)
        val match = hybridMatch.routeMatch

        val geometryLength = match.geometryDistanceMeters.coerceAtLeast(1.0)
        val progressRatio = (match.progressMeters / geometryLength).coerceIn(0.0, 1.0)
        val traveledServiceMeters = leg.distanceMeters.coerceAtLeast(0.0) * progressRatio
        val remaining = (leg.distanceMeters - traveledServiceMeters).coerceAtLeast(0.0)
        val eta = leg.durationSeconds
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { total -> (total * (1.0 - progressRatio)).coerceAtLeast(0.0) }

        val upcoming = navigationUpcomingInstructions(leg, traveledServiceMeters)
        val next = upcoming.getOrNull(0)
        val following = upcoming.getOrNull(1)

        val offRouteThreshold = BASE_OFF_ROUTE_THRESHOLD_METERS
        val offRoute = !weakGps && match.offRouteMeters > offRouteThreshold
        val nextTurnCode = next?.first?.turnCode
        val nextRequiresCourseChange = nextTurnCode != null &&
            nextTurnCode != 5 &&
            nextTurnCode != 9
        val movingSpeed = fix.speedMetersPerSecond
            ?.takeIf { it.isFinite() && it >= 0f }
            ?: 0f
        val missedManeuver = offRoute &&
            movingSpeed >= MISSED_MANEUVER_MIN_SPEED_METERS_PER_SECOND &&
            nextRequiresCourseChange &&
            (next?.second ?: Double.POSITIVE_INFINITY) <= MISSED_MANEUVER_WINDOW_METERS

        val now = nowMillis()
        if (offRoute) {
            if (offRouteSinceMillis == null) offRouteSinceMillis = now
        } else {
            offRouteSinceMillis = null
        }
        val requiredCooldown = if (missedManeuver) {
            MISSED_MANEUVER_REROUTE_COOLDOWN_MILLIS
        } else {
            rerouteCooldownMillis
        }
        val cooldownSatisfied = lastRerouteAtMillis?.let { now - it >= requiredCooldown } ?: true
        val sustainedOffRoute = offRouteSinceMillis?.let { since ->
            now - since >= requiredOffRouteMillis.coerceAtLeast(0L)
        } ?: false
        val shouldReroute = offRoute && cooldownSatisfied && (missedManeuver || sustainedOffRoute)
        if (shouldReroute) {
            offRouteSinceMillis = null
            lastRerouteAtMillis = now
        }
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
            nextInstruction = next?.first,
            distanceToInstructionMeters = next?.second,
            followingInstruction = following?.first,
            distanceToFollowingInstructionMeters = following?.second,
            arrived = arrived,
            shouldReroute = shouldReroute
        )
    }
}

internal fun navigationNextInstruction(
    leg: NavigationRouteLeg,
    traveledMeters: Double
): Pair<NavigationRouteInstruction?, Double?> {
    val next = navigationUpcomingInstructions(leg, traveledMeters).firstOrNull()
    return next?.let { it.first to it.second } ?: (null to null)
}

internal fun navigationUpcomingInstructions(
    leg: NavigationRouteLeg,
    traveledMeters: Double,
    limit: Int = 2
): List<Pair<NavigationRouteInstruction, Double>> {
    val wanted = limit.coerceIn(1, 4)
    var actionAt = 0.0
    val traveled = traveledMeters.coerceAtLeast(0.0)
    val upcoming = ArrayList<Pair<NavigationRouteInstruction, Double>>(wanted)
    for (index in leg.instructions.indices) {
        val instruction = leg.instructions[index]
        if (
            index > 0 &&
            navigationTurnPhrase(instruction.turnCode) != null &&
            actionAt + 15.0 >= traveled
        ) {
            upcoming += instruction to (actionAt - traveled).coerceAtLeast(0.0)
            if (upcoming.size >= wanted) return upcoming
        }
        actionAt += instruction.sectionDistanceMeters.coerceAtLeast(0.0)
    }
    if (upcoming.isEmpty()) {
        val arrival = NavigationRouteInstruction(
            sequence = leg.instructions.size,
            turnCode = 9,
            roadName = "",
            sectionDistanceMeters = 0.0,
            sectionDurationSeconds = 0.0
        )
        upcoming += arrival to (leg.distanceMeters - traveled).coerceAtLeast(0.0)
    }
    return upcoming
}

fun navigationTurnPhrase(turnCode: Int): String? = when (turnCode) {
    0 -> "เลี้ยวซ้าย"
    1 -> "เลี้ยวขวา"
    2 -> "เบี่ยงซ้าย"
    3 -> "เบี่ยงขวา"
    4 -> "กลับรถ"
    5 -> "ตรงไป"
    6 -> "ขึ้นทางด่วน"
    9 -> "ถึงจุดหมายแล้ว"
    11 -> "ขึ้นเรือ"
    else -> null
}
