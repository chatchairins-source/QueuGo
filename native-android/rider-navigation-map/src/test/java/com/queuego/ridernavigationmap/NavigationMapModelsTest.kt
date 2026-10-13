package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationMapModelsTest {
    private fun stop(id: String, sequence: Int, completed: Boolean = false) = NavigationMapStop(
        id = id,
        sequence = sequence,
        kind = if (sequence <= 2) NavigationStopKind.PICKUP else NavigationStopKind.DROPOFF,
        point = NavigationMapPoint(14.99 + sequence * 0.001, 103.10 + sequence * 0.001),
        completed = completed
    )

    private fun route(id: String, toStopId: String) = NavigationRouteSegment(
        id = id,
        toStopId = toStopId,
        points = listOf(NavigationMapPoint(15.0, 103.1), NavigationMapPoint(15.001, 103.101))
    )

    @Test fun firstIncompleteStopBecomesActiveWhenCallerDoesNotOverride() {
        val state = RiderNavigationMapState(
            stops = listOf(stop("shop-1", 1, completed = true), stop("shop-2", 2), stop("customer", 3))
        )
        assertEquals("shop-2", state.resolvedActiveStopId())
    }

    @Test fun currentLegIsDarkAndLaterLegsStayFaded() {
        val state = RiderNavigationMapState(
            stops = listOf(stop("shop-1", 1), stop("shop-2", 2), stop("customer", 3)),
            routes = listOf(route("leg-1", "shop-1"), route("leg-2", "shop-2"), route("leg-3", "customer")),
            activeStopId = "shop-1"
        )
        val styled = state.styledRemainingRoutes()
        assertEquals(NavigationRouteStyle.ACTIVE, styled.single { it.toStopId == "shop-1" }.style)
        assertTrue(styled.filter { it.toStopId != "shop-1" }.all { it.style == NavigationRouteStyle.FUTURE })
    }

    @Test fun completedStopAndItsRouteDisappearWithoutChangingLaterSequence() {
        val state = RiderNavigationMapState(
            stops = listOf(stop("shop-1", 1, completed = true), stop("shop-2", 2), stop("customer", 3)),
            routes = listOf(route("leg-1", "shop-1"), route("leg-2", "shop-2"), route("leg-3", "customer"))
        )
        assertEquals(listOf(2, 3), state.remainingStops().map { it.sequence })
        assertEquals(listOf("shop-2", "customer"), state.styledRemainingRoutes().map { it.toStopId })
        assertEquals(NavigationRouteStyle.ACTIVE, state.styledRemainingRoutes().first().style)
    }
}
