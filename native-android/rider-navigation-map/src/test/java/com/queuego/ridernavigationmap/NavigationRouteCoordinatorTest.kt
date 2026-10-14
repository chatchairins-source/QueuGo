package com.queuego.ridernavigationmap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRouteCoordinatorTest {
    private class FakeRouteProvider : NavigationRouteProvider {
        data class Call(
            val from: NavigationMapPoint,
            val stopId: String,
            val segmentId: String
        )
        val calls = mutableListOf<Call>()
        var attempts = 0
        var failNext = false
        var cancelNext = false

        override suspend fun route(
            from: NavigationMapPoint,
            stop: NavigationMapStop,
            segmentId: String
        ): NavigationRouteLeg {
            attempts += 1
            if (cancelNext) {
                cancelNext = false
                throw CancellationException("superseded route")
            }
            if (failNext) {
                failNext = false
                error("route unavailable")
            }
            calls += Call(from, stop.id, segmentId)
            val mid = NavigationMapPoint(
                latitude = (from.latitude + stop.point.latitude) / 2.0,
                longitude = (from.longitude + stop.point.longitude) / 2.0
            )
            return NavigationRouteLeg(
                segment = NavigationRouteSegment(
                    id = segmentId,
                    toStopId = stop.id,
                    points = listOf(from, mid, stop.point),
                    style = NavigationRouteStyle.ACTIVE
                ),
                distanceMeters = 1200.0,
                durationSeconds = 240.0,
                instructions = listOf(
                    NavigationRouteInstruction(0, 5, "ถนนหลัก", 600.0, 120.0),
                    NavigationRouteInstruction(1, 1, "ถนนปลายทาง", 600.0, 120.0)
                )
            )
        }
    }

    private val pickup = NavigationMapStop(
        id = "pickup-1",
        sequence = 1,
        kind = NavigationStopKind.PICKUP,
        point = NavigationMapPoint(13.0000, 100.0100)
    )
    private val customer = NavigationMapStop(
        id = "dropoff-2",
        sequence = 2,
        kind = NavigationStopKind.DROPOFF,
        point = NavigationMapPoint(13.0100, 100.0100)
    )
    private val futureRoute = NavigationRouteSegment(
        id = "future-dropoff",
        toStopId = customer.id,
        points = listOf(pickup.point, customer.point),
        style = NavigationRouteStyle.FUTURE
    )

    private fun state(
        riderPoint: NavigationMapPoint = NavigationMapPoint(13.0000, 100.0000),
        pickupCompleted: Boolean = false
    ) = RiderNavigationMapState(
        rider = NavigationMapFix(
            point = riderPoint,
            accuracyMeters = 5f,
            speedMetersPerSecond = 7f
        ),
        stops = listOf(pickup.copy(completed = pickupCompleted), customer),
        routes = listOf(futureRoute),
        activeStopId = if (pickupCompleted) customer.id else pickup.id
    )

    @Test fun initialRouteReplacesOnlyActiveLegAndPreservesFutureStop() = runBlocking {
        val provider = FakeRouteProvider()
        val coordinator = NavigationRouteCoordinator(provider, nowMillis = { 10_000L })

        val first = coordinator.synchronize(state())

        assertEquals(1, provider.calls.size)
        assertTrue(first.routeChanged)
        assertFalse(first.rerouted)
        assertEquals(pickup.id, first.activeLeg!!.segment.toStopId)
        assertEquals(
            listOf(pickup.id, customer.id),
            first.mapState.routes.map { it.toStopId }
        )
        assertEquals("future-dropoff", first.mapState.routes.last().id)

        val second = coordinator.synchronize(first.mapState)
        assertEquals(1, provider.calls.size)
        assertFalse(second.routeChanged)
        assertNotNull(second.guidance.distanceRemainingMeters)
    }

    @Test fun twoReliableOffRouteSamplesRerouteOnlyActiveLeg() = runBlocking {
        var now = 20_000L
        val provider = FakeRouteProvider()
        val coordinator = NavigationRouteCoordinator(provider, nowMillis = { now })
        val initial = coordinator.synchronize(state())

        val offRouteState = initial.mapState.copy(
            rider = NavigationMapFix(
                point = NavigationMapPoint(12.9980, 100.0050),
                accuracyMeters = 5f,
                speedMetersPerSecond = 6f
            )
        )
        val firstOff = coordinator.synchronize(offRouteState)
        assertEquals(1, provider.calls.size)
        assertTrue(firstOff.guidance.offRoute)
        assertFalse(firstOff.rerouted)

        now += 500L
        val rerouted = coordinator.synchronize(offRouteState)
        assertEquals(2, provider.calls.size)
        assertTrue(rerouted.rerouted)
        assertEquals(1, rerouted.rerouteVersion)
        assertEquals(customer.id, rerouted.mapState.routes.last().toStopId)
        assertEquals("future-dropoff", rerouted.mapState.routes.last().id)
    }

    @Test fun completingPickupPromotesNextStopAndRequestsFreshActiveRoute() = runBlocking {
        val provider = FakeRouteProvider()
        val coordinator = NavigationRouteCoordinator(provider, nowMillis = { 30_000L })
        val initial = coordinator.synchronize(state())
        assertEquals(pickup.id, initial.activeLeg!!.segment.toStopId)

        val next = coordinator.synchronize(
            initial.mapState.copy(
                stops = listOf(pickup.copy(completed = true), customer),
                activeStopId = customer.id,
                rider = NavigationMapFix(
                    point = pickup.point,
                    accuracyMeters = 5f,
                    speedMetersPerSecond = 2f
                )
            )
        )

        assertEquals(2, provider.calls.size)
        assertEquals(customer.id, next.activeLeg!!.segment.toStopId)
        assertEquals(0, next.rerouteVersion)
        assertEquals(listOf(customer.id), next.mapState.routes.map { it.toStopId })
    }

    @Test fun activeRouteShrinksForwardWithoutGrowingBackOnGpsJitter() = runBlocking {
        val provider = FakeRouteProvider()
        val coordinator = NavigationRouteCoordinator(provider, nowMillis = { 35_000L })
        val initial = coordinator.synchronize(state())
        val initialActive = initial.mapState.routes.first { it.toStopId == pickup.id }
        assertEquals(3, initialActive.points.size)

        val forward = coordinator.synchronize(
            initial.mapState.copy(
                rider = NavigationMapFix(
                    point = NavigationMapPoint(13.0000, 100.0060),
                    accuracyMeters = 5f,
                    speedMetersPerSecond = 7f
                )
            )
        )
        val shortened = forward.mapState.routes.first { it.toStopId == pickup.id }
        assertTrue(shortened.points.first().longitude > initialActive.points.first().longitude)

        val jitterBack = coordinator.synchronize(
            forward.mapState.copy(
                rider = NavigationMapFix(
                    point = NavigationMapPoint(13.0000, 100.0040),
                    accuracyMeters = 5f,
                    speedMetersPerSecond = 7f
                )
            )
        )
        val afterJitter = jitterBack.mapState.routes.first { it.toStopId == pickup.id }
        assertTrue(
            "Consumed active route grew backwards after GPS jitter",
            afterJitter.points.first().longitude >= shortened.points.first().longitude
        )
        assertEquals("future-dropoff", jitterBack.mapState.routes.last().id)
    }

    @Test fun smallProgressDoesNotChangeVisibleGeometryEveryGpsTick() = runBlocking {
        val provider = FakeRouteProvider()
        val coordinator = NavigationRouteCoordinator(provider, nowMillis = { 36_000L })
        val initial = coordinator.synchronize(state())
        val before = initial.mapState.routes.first { it.toStopId == pickup.id }

        val tinyMove = coordinator.synchronize(
            initial.mapState.copy(
                rider = NavigationMapFix(
                    point = NavigationMapPoint(13.0000, 100.00003),
                    accuracyMeters = 5f,
                    speedMetersPerSecond = 2f
                )
            )
        )
        val after = tinyMove.mapState.routes.first { it.toStopId == pickup.id }
        assertEquals(before.points, after.points)
    }

    @Test fun activeRouteDisappearsWhenOnlyArrivalStubRemains() {
        val segment = NavigationRouteSegment(
            id = "active",
            toStopId = pickup.id,
            points = listOf(
                NavigationMapPoint(13.0000, 100.0000),
                pickup.point
            ),
            style = NavigationRouteStyle.ACTIVE
        )
        val total = navigationHaversineMeters(segment.points.first(), segment.points.last())
        val trimmed = trimNavigationRouteSegment(segment, total - 1.0, pickup.point)
        assertFalse(trimmed.renderable)
        assertTrue(trimmed.points.isEmpty())
    }

    @Test fun routeFailureDoesNotDestroyExistingMapState() = runBlocking {
        val provider = FakeRouteProvider().apply { failNext = true }
        val coordinator = NavigationRouteCoordinator(provider, nowMillis = { 40_000L })
        val original = state()

        val result = coordinator.synchronize(original)

        assertEquals(original, result.mapState)
        assertEquals(null, result.activeLeg)
        assertTrue(result.routeError!!.contains("route unavailable"))
        assertFalse(result.routeChanged)
        assertEquals(1, provider.attempts)
    }

    @Test fun failedInitialRouteUsesBackoffInsteadOfRetryingEveryGpsTick() = runBlocking {
        var now = 50_000L
        val provider = FakeRouteProvider().apply { failNext = true }
        val coordinator = NavigationRouteCoordinator(provider, nowMillis = { now })
        val original = state()

        val failed = coordinator.synchronize(original)
        assertEquals(1, provider.attempts)
        assertTrue(failed.routeError!!.contains("route unavailable"))

        now += 1_000L
        val held = coordinator.synchronize(original)
        assertEquals(1, provider.attempts)
        assertEquals(original, held.mapState)
        assertEquals(null, held.activeLeg)

        now += 500L
        val recovered = coordinator.synchronize(original)
        assertEquals(2, provider.attempts)
        assertNotNull(recovered.activeLeg)
        assertTrue(recovered.routeChanged)
        assertEquals(null, recovered.routeError)
    }

    @Test fun supersededRouteRequestPropagatesCancellationImmediately() = runBlocking {
        val provider = FakeRouteProvider().apply { cancelNext = true }
        val coordinator = NavigationRouteCoordinator(provider, nowMillis = { 60_000L })

        var cancelled = false
        try {
            coordinator.synchronize(state())
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertEquals(1, provider.attempts)
    }
}
