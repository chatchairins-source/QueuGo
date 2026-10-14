package com.queuego.ridernavigationmap

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationRouteCacheTest {
    private class FakeProvider : NavigationRouteProvider {
        var calls = 0

        override suspend fun route(
            from: NavigationMapPoint,
            stop: NavigationMapStop,
            segmentId: String
        ): NavigationRouteLeg {
            calls += 1
            return NavigationRouteLeg(
                segment = NavigationRouteSegment(
                    id = segmentId,
                    toStopId = stop.id,
                    points = listOf(from, stop.point)
                ),
                distanceMeters = navigationHaversineMeters(from, stop.point),
                durationSeconds = 120.0
            )
        }
    }

    private fun stop(
        id: String = "customer-1",
        point: NavigationMapPoint = NavigationMapPoint(13.0010, 100.0010)
    ) = NavigationMapStop(
        id = id,
        sequence = 2,
        kind = NavigationStopKind.CUSTOMER,
        point = point,
        label = "ลูกค้า"
    )

    @Test
    fun prefetchedNextLegIsReusedNearItsPlannedOrigin() = runBlocking {
        val delegate = FakeProvider()
        val cache = CachingNavigationRouteProvider(delegate)
        val plannedOrigin = NavigationMapPoint(13.0000, 100.0000)
        val nextStop = stop()

        cache.route(plannedOrigin, nextStop, "future:pickup-1>customer-1")
        val active = cache.route(
            NavigationMapPoint(13.0002, 100.0000),
            nextStop,
            "navigation:customer-1:0"
        )

        assertEquals(1, delegate.calls)
        assertEquals("navigation:customer-1:0", active.segment.id)
        assertEquals(nextStop.id, active.segment.toStopId)
    }

    @Test
    fun rerouteAlwaysBypassesPrefetchedCache() = runBlocking {
        val delegate = FakeProvider()
        val cache = CachingNavigationRouteProvider(delegate)
        val origin = NavigationMapPoint(13.0000, 100.0000)
        val nextStop = stop()

        cache.route(origin, nextStop, "future:pickup-1>customer-1")
        cache.route(origin, nextStop, "navigation:customer-1:1")

        assertEquals(2, delegate.calls)
    }

    @Test
    fun farOriginDoesNotReuseFutureLeg() = runBlocking {
        val delegate = FakeProvider()
        val cache = CachingNavigationRouteProvider(delegate)
        val nextStop = stop()

        cache.route(
            NavigationMapPoint(13.0000, 100.0000),
            nextStop,
            "future:pickup-1>customer-1"
        )
        cache.route(
            NavigationMapPoint(13.0020, 100.0000),
            nextStop,
            "navigation:customer-1:0"
        )

        assertEquals(2, delegate.calls)
    }

    @Test
    fun expiredPrefetchIsNotReused() = runBlocking {
        var now = 1_000L
        val delegate = FakeProvider()
        val cache = CachingNavigationRouteProvider(
            delegate = delegate,
            nowMillis = { now },
            ttlMillis = 5_000L
        )
        val origin = NavigationMapPoint(13.0000, 100.0000)
        val nextStop = stop()

        cache.route(origin, nextStop, "future:pickup-1>customer-1")
        now += 5_001L
        cache.route(origin, nextStop, "navigation:customer-1:0")

        assertEquals(2, delegate.calls)
    }
}
