package com.queuego.ridernavigationmap

import kotlinx.coroutines.CancellationException

private const val ROUTE_PROGRESS_RENDER_STEP_METERS = 10.0
private const val ROUTE_PROGRESS_NEAR_ARRIVAL_METERS = 25.0
private const val ROUTE_PROGRESS_HIDE_REMAINING_METERS = 3.0
private const val INITIAL_ROUTE_RETRY_BASE_MILLIS = 1_500L
private const val INITIAL_ROUTE_RETRY_MAX_MILLIS = 12_000L

data class NavigationRouteCoordinatorResult(
    val mapState: RiderNavigationMapState,
    val guidance: NavigationGuidanceState,
    val activeLeg: NavigationRouteLeg?,
    val rerouteVersion: Int,
    val routeChanged: Boolean,
    val rerouted: Boolean,
    val routeError: String? = null
)

class NavigationRouteCoordinator(
    private val routeProvider: NavigationRouteProvider,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val progressTracker = NavigationProgressTracker(nowMillis = nowMillis)
    private var activeStopId: String? = null
    private var activeStopPoint: NavigationMapPoint? = null
    private var activeLeg: NavigationRouteLeg? = null
    private var rerouteVersion: Int = 0
    private var visibleActiveSegment: NavigationRouteSegment? = null
    private var visibleProgressMeters: Double = 0.0
    private var initialRouteFailureCount: Int = 0
    private var nextInitialRouteAttemptAtMillis: Long = 0L
    private var lastInitialRouteFailureAtMillis: Long? = null
    private var lastInitialRouteError: String? = null

    fun reset() {
        activeStopId = null
        activeStopPoint = null
        activeLeg = null
        rerouteVersion = 0
        visibleActiveSegment = null
        visibleProgressMeters = 0.0
        resetInitialRouteRetry()
        progressTracker.reset()
    }

    suspend fun synchronize(state: RiderNavigationMapState): NavigationRouteCoordinatorResult {
        val activeId = state.resolvedActiveStopId()
        val activeStop = activeId?.let { id ->
            state.remainingStops().firstOrNull { it.id == id }
        }
        if (activeStop == null) {
            reset()
            return NavigationRouteCoordinatorResult(
                mapState = state,
                guidance = NavigationGuidanceState(),
                activeLeg = null,
                rerouteVersion = 0,
                routeChanged = false,
                rerouted = false
            )
        }

        // A corrected pickup/drop-off may keep its business stop ID but move to
        // another coordinate. The old route must never remain authoritative for it.
        val previousActiveStopId = activeStopId
        val activeStopChanged = previousActiveStopId != activeStop.id
        val destinationChanged = !activeStopChanged &&
            activeStopPoint != null && activeStopPoint != activeStop.point
        if (activeStopChanged || activeStopPoint != activeStop.point) {
            activeStopId = activeStop.id
            activeStopPoint = activeStop.point
            activeLeg = null
            rerouteVersion = 0
            visibleActiveSegment = null
            visibleProgressMeters = 0.0
            resetInitialRouteRetry()
            progressTracker.reset()
        }
        // A completed pickup can promote a new active stop while GPS is missing
        // or the new route API fails. Never leave the old pickup's active line
        // on screen as though it still were the current destination.
        // Preserve the new stop's previously verified FUTURE leg until rerouted.
        val safeState = when {
            destinationChanged ->
                state.copy(routes = state.routes.filterNot { it.toStopId == activeStop.id })
            activeStopChanged && previousActiveStopId != null ->
                state.copy(routes = state.routes.filterNot { it.toStopId == previousActiveStopId })
            else -> state
        }

        val fix = safeState.rider?.takeIf { it.point.valid }
            ?: return NavigationRouteCoordinatorResult(
                mapState = safeState,
                guidance = NavigationGuidanceState(),
                activeLeg = activeLeg,
                rerouteVersion = rerouteVersion,
                routeChanged = false,
                rerouted = false
            )

        var routeChanged = false
        var rerouted = false
        var routeError: String? = null

        if (activeLeg == null) {
            val now = nowMillis()
            // Retry deadlines use wall-clock time. If the device clock is corrected
            // backwards after a failure, the old deadline can be arbitrarily far
            // in the future; discard only that backoff, not the active stop.
            if (lastInitialRouteFailureAtMillis?.let { now < it } == true) {
                resetInitialRouteRetry()
            }
            if (now >= nextInitialRouteAttemptAtMillis) {
                try {
                    activeLeg = routeProvider.route(
                        from = fix.point,
                        stop = activeStop,
                        segmentId = segmentId(activeStop.id, rerouteVersion)
                    )
                    visibleActiveSegment = activeLeg?.segment
                    visibleProgressMeters = 0.0
                    resetInitialRouteRetry()
                    routeChanged = true
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    routeError = error.message ?: "คำนวณเส้นทางไม่สำเร็จ"
                    lastInitialRouteError = routeError
                    lastInitialRouteFailureAtMillis = now
                    initialRouteFailureCount += 1
                    nextInitialRouteAttemptAtMillis =
                        now + initialRouteRetryDelayMillis(initialRouteFailureCount)
                }
            } else {
                routeError = lastInitialRouteError
            }
        }

        var leg = activeLeg
        var guidance = if (leg != null) {
            progressTracker.update(fix, leg, activeStop)
        } else {
            NavigationGuidanceState()
        }

        if (leg != null && guidance.shouldReroute) {
            val nextVersion = rerouteVersion + 1
            try {
                val replacement = routeProvider.route(
                    from = fix.point,
                    stop = activeStop,
                    segmentId = segmentId(activeStop.id, nextVersion)
                )
                activeLeg = replacement
                leg = replacement
                rerouteVersion = nextVersion
                visibleActiveSegment = replacement.segment
                visibleProgressMeters = 0.0
                routeChanged = true
                rerouted = true
                // Reset route progress against the replacement geometry but retain the
                // Web-matched 20-second reroute cooldown for this active stop.
                progressTracker.reset(clearRerouteCooldown = false)
                guidance = progressTracker.update(fix, replacement, activeStop)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                routeError = error.message ?: "คำนวณเส้นทางใหม่ไม่สำเร็จ"
            }
        }

        val visibleSegment = leg?.let {
            visibleRouteForProgress(
                leg = it,
                fix = fix,
                guidance = guidance
            )
        }
        val updatedMapState = if (visibleSegment != null) {
            replaceActiveLeg(safeState, activeStop, visibleSegment)
        } else {
            safeState
        }
        return NavigationRouteCoordinatorResult(
            mapState = updatedMapState,
            guidance = guidance,
            activeLeg = leg,
            rerouteVersion = rerouteVersion,
            routeChanged = routeChanged,
            rerouted = rerouted,
            routeError = routeError
        )
    }

    private fun resetInitialRouteRetry() {
        initialRouteFailureCount = 0
        nextInitialRouteAttemptAtMillis = 0L
        lastInitialRouteFailureAtMillis = null
        lastInitialRouteError = null
    }

    private fun initialRouteRetryDelayMillis(failureCount: Int): Long {
        val exponent = (failureCount - 1).coerceIn(0, 3)
        return (INITIAL_ROUTE_RETRY_BASE_MILLIS shl exponent)
            .coerceAtMost(INITIAL_ROUTE_RETRY_MAX_MILLIS)
    }

    private fun segmentId(stopId: String, version: Int): String =
        "navigation:$stopId:$version"

    private fun visibleRouteForProgress(
        leg: NavigationRouteLeg,
        fix: NavigationMapFix,
        guidance: NavigationGuidanceState
    ): NavigationRouteSegment {
        val current = visibleActiveSegment
            ?.takeIf { it.id == leg.segment.id }
            ?: leg.segment.also {
                visibleActiveSegment = it
                visibleProgressMeters = 0.0
            }

        // The full leg remains authoritative for matching/ETA/reroute. Only the renderer copy
        // shrinks, and only from reliable on-route fixes.
        if (guidance.weakGps || guidance.offRoute) return current
        val match = matchNavigationRoute(
            segment = leg.segment,
            point = fix.point,
            bearingDegrees = navigationUsableGpsBearing(fix),
            speedMetersPerSecond = fix.speedMetersPerSecond,
            previousProgressMeters = visibleProgressMeters
        ) ?: return current
        val monotonicProgress = maxOf(visibleProgressMeters, match.progressMeters)
            .coerceIn(0.0, match.geometryDistanceMeters)
        val remaining = (match.geometryDistanceMeters - monotonicProgress).coerceAtLeast(0.0)
        val advancedEnough =
            monotonicProgress - visibleProgressMeters >= ROUTE_PROGRESS_RENDER_STEP_METERS
        val nearArrivalAdvance =
            remaining <= ROUTE_PROGRESS_NEAR_ARRIVAL_METERS &&
                monotonicProgress > visibleProgressMeters

        if (!advancedEnough && !nearArrivalAdvance) return current

        val snappedAtMonotonicProgress = match.snapped.takeIf {
            match.progressMeters + 0.5 >= monotonicProgress
        }
        val trimmed = trimNavigationRouteSegment(
            segment = leg.segment,
            progressMeters = monotonicProgress,
            snappedPoint = snappedAtMonotonicProgress
        )
        visibleProgressMeters = monotonicProgress
        visibleActiveSegment = trimmed
        return trimmed
    }

    private fun replaceActiveLeg(
        state: RiderNavigationMapState,
        activeStop: NavigationMapStop,
        activeSegment: NavigationRouteSegment
    ): RiderNavigationMapState {
        val remainingIds = state.remainingStops().mapTo(linkedSetOf()) { it.id }
        val preservedFuture = state.routes.filter { segment ->
            segment.toStopId in remainingIds && segment.toStopId != activeStop.id
        }
        return state.copy(
            routes = listOf(
                activeSegment.copy(style = NavigationRouteStyle.ACTIVE)
            ) + preservedFuture
        )
    }
}

internal fun trimNavigationRouteSegment(
    segment: NavigationRouteSegment,
    progressMeters: Double,
    snappedPoint: NavigationMapPoint? = null
): NavigationRouteSegment {
    if (!segment.renderable || progressMeters <= 0.0) return segment

    var totalMeters = 0.0
    for (index in 0 until segment.points.lastIndex) {
        totalMeters += navigationHaversineMeters(segment.points[index], segment.points[index + 1])
    }
    if (totalMeters <= 0.01) return segment

    val target = progressMeters.coerceIn(0.0, totalMeters)
    if (totalMeters - target <= ROUTE_PROGRESS_HIDE_REMAINING_METERS) {
        // Removing the renderer-facing active geometry is safe because the coordinator keeps the
        // full authoritative leg until the business stop itself is completed.
        return segment.copy(points = emptyList())
    }

    var walked = 0.0
    for (index in 0 until segment.points.lastIndex) {
        val a = segment.points[index]
        val b = segment.points[index + 1]
        val edgeMeters = navigationHaversineMeters(a, b)
        if (edgeMeters <= 0.01) continue
        if (walked + edgeMeters >= target) {
            val fraction = ((target - walked) / edgeMeters).coerceIn(0.0, 1.0)
            val interpolated = NavigationMapPoint(
                latitude = a.latitude + (b.latitude - a.latitude) * fraction,
                longitude = a.longitude + (b.longitude - a.longitude) * fraction
            )
            val start = snappedPoint?.takeIf { it.valid } ?: interpolated
            val remaining = ArrayList<NavigationMapPoint>(segment.points.size - index + 1)
            remaining += start
            if (navigationHaversineMeters(start, b) > 0.5) remaining += b
            for (nextIndex in index + 2 until segment.points.size) {
                val next = segment.points[nextIndex]
                if (remaining.isEmpty() ||
                    navigationHaversineMeters(remaining.last(), next) > 0.5
                ) {
                    remaining += next
                }
            }
            if (remaining.size < 2) {
                val end = segment.points.last()
                if (navigationHaversineMeters(start, end) > 0.5) remaining += end
            }
            return segment.copy(points = remaining)
        }
        walked += edgeMeters
    }
    return segment.copy(points = emptyList())
}
