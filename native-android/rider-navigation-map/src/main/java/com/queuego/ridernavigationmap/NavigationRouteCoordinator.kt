package com.queuego.ridernavigationmap

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
    nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val progressTracker = NavigationProgressTracker(nowMillis = nowMillis)
    private var activeStopId: String? = null
    private var activeLeg: NavigationRouteLeg? = null
    private var rerouteVersion: Int = 0

    fun reset() {
        activeStopId = null
        activeLeg = null
        rerouteVersion = 0
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

        if (activeStopId != activeStop.id) {
            activeStopId = activeStop.id
            activeLeg = null
            rerouteVersion = 0
            progressTracker.reset()
        }

        val fix = state.rider?.takeIf { it.point.valid }
            ?: return NavigationRouteCoordinatorResult(
                mapState = state,
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
            try {
                activeLeg = routeProvider.route(
                    from = fix.point,
                    stop = activeStop,
                    segmentId = segmentId(activeStop.id, rerouteVersion)
                )
                routeChanged = true
            } catch (error: Throwable) {
                routeError = error.message ?: "คำนวณเส้นทางไม่สำเร็จ"
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
                routeChanged = true
                rerouted = true
                progressTracker.reset()
                guidance = progressTracker.update(fix, replacement, activeStop)
            } catch (error: Throwable) {
                routeError = error.message ?: "คำนวณเส้นทางใหม่ไม่สำเร็จ"
            }
        }

        val updatedMapState = leg?.let { replaceActiveLeg(state, activeStop, it) } ?: state
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

    private fun segmentId(stopId: String, version: Int): String =
        "navigation:$stopId:$version"

    private fun replaceActiveLeg(
        state: RiderNavigationMapState,
        activeStop: NavigationMapStop,
        leg: NavigationRouteLeg
    ): RiderNavigationMapState {
        val remainingIds = state.remainingStops().mapTo(linkedSetOf()) { it.id }
        val preservedFuture = state.routes.filter { segment ->
            segment.toStopId in remainingIds && segment.toStopId != activeStop.id
        }
        return state.copy(
            routes = listOf(
                leg.segment.copy(style = NavigationRouteStyle.ACTIVE)
            ) + preservedFuture
        )
    }
}
