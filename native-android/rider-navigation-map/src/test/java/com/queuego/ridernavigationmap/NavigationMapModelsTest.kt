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

    private fun route(id: String, destination: NavigationMapStop) = NavigationRouteSegment(
        id = id,
        toStopId = destination.id,
        points = listOf(NavigationMapPoint(15.0, 103.1), destination.point)
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
            routes = listOf(route("leg-1", stop("shop-1", 1)), route("leg-2", stop("shop-2", 2)), route("leg-3", stop("customer", 3))),
            activeStopId = "shop-1"
        )
        val styled = state.styledRemainingRoutes()
        assertEquals(NavigationRouteStyle.ACTIVE, styled.single { it.toStopId == "shop-1" }.style)
        assertTrue(styled.filter { it.toStopId != "shop-1" }.all { it.style == NavigationRouteStyle.FUTURE })
    }

    @Test fun completedStopAndItsRouteDisappearWithoutChangingLaterSequence() {
        val state = RiderNavigationMapState(
            stops = listOf(stop("shop-1", 1, completed = true), stop("shop-2", 2), stop("customer", 3)),
            routes = listOf(route("leg-1", stop("shop-1", 1)), route("leg-2", stop("shop-2", 2)), route("leg-3", stop("customer", 3)))
        )
        assertEquals(listOf(2, 3), state.remainingStops().map { it.sequence })
        assertEquals(listOf("shop-2", "customer"), state.styledRemainingRoutes().map { it.toStopId })
        assertEquals(NavigationRouteStyle.ACTIVE, state.styledRemainingRoutes().first().style)
    }
    @Test fun stopMarkerTitlesExposeNumberAndRole() {
        val shop = NavigationMapStop(
            id = "shop",
            sequence = 1,
            kind = NavigationStopKind.PICKUP,
            point = NavigationMapPoint(15.0, 103.1),
            label = "ร้านทดสอบ"
        )
        val customer = NavigationMapStop(
            id = "customer",
            sequence = 2,
            kind = NavigationStopKind.DROPOFF,
            point = NavigationMapPoint(15.01, 103.11),
            label = "ลูกค้า QT-1234"
        )
        assertEquals("จุด 1 · ร้านค้า · ร้านทดสอบ", navigationStopMarkerTitle(shop))
        assertEquals("จุด 2 · ลูกค้า · ลูกค้า QT-1234", navigationStopMarkerTitle(customer))
    }

    @Test fun demandZonesFailClosedAndDeduplicateByStableIdentity() {
        val state = RiderNavigationMapState(
            demandZones = listOf(
                NavigationDemandZone("cell-a", NavigationMapPoint(15.0, 103.0), 1, 24),
                NavigationDemandZone("cell-a", NavigationMapPoint(15.01, 103.01), 3, 24),
                NavigationDemandZone("", NavigationMapPoint(15.02, 103.02), 2, 24),
                NavigationDemandZone("bad-level", NavigationMapPoint(15.03, 103.03), 9, 24)
            )
        )
        val zones = state.renderableDemandZones()
        assertEquals(1, zones.size)
        assertEquals("cell-a", zones.single().id)
        assertEquals(1, zones.single().level)
    }

    @Test fun correctedFutureDestinationHidesStaleRouteButKeepsCurrentPickup() {
        val pickup = stop("shop-1", 1)
        val future = stop("customer", 3)
        val original = RiderNavigationMapState(
            stops = listOf(pickup, future),
            routes = listOf(route("pickup-line", pickup), route("future-line", future)),
            activeStopId = pickup.id
        )
        assertEquals(2, original.styledRemainingRoutes().size)
        val corrected = original.copy(
            stops = listOf(pickup, future.copy(point = NavigationMapPoint(15.50, 103.50)))
        )
        assertEquals(listOf("pickup-line"), corrected.styledRemainingRoutes().map { it.id })
        assertEquals(NavigationRouteStyle.ACTIVE, corrected.styledRemainingRoutes().single().style)
    }

    @Test fun validRoadSnapNearCorrectedStopRemainsVisible() {
        val pickup = stop("shop-1", 1)
        val future = stop("customer", 3)
        val state = RiderNavigationMapState(
            stops = listOf(pickup, future.copy(point = NavigationMapPoint(
                future.point.latitude + 0.002, future.point.longitude
            ))),
            routes = listOf(route("future-line", future)),
            activeStopId = pickup.id
        )
        assertEquals(listOf("future-line"), state.styledRemainingRoutes().map { it.id })
        assertEquals(NavigationRouteStyle.FUTURE, state.styledRemainingRoutes().single().style)
    }
}
