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
        kind = NavigationStopKind.DROPOFF,
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

    @Test
    fun wallClockRollbackNeverReusesAnOlderPrefetchedRoute() = runBlocking {
        var now = 100_000L
        val delegate = FakeProvider()
        val cache = CachingNavigationRouteProvider(
            delegate = delegate,
            nowMillis = { now },
            ttlMillis = 5_000L
        )
        val origin = NavigationMapPoint(13.0000, 100.0000)
        val nextStop = stop()

        cache.route(origin, nextStop, "future:pickup-1>customer-1")
        now = 99_999L // system time corrected backwards while app stays open
        cache.route(origin, nextStop, "navigation:customer-1:0")

        assertEquals(2, delegate.calls)
        assertEquals(1, cache.size()) // the old entry was pruned, not accumulated
    }

    @Test
    fun routeCacheKeepsOriginalTtlBoundaryAfterClockGuard() = runBlocking {
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
        now = 6_000L
        cache.route(origin, nextStop, "navigation:customer-1:0")
        assertEquals(1, delegate.calls)
        now = 6_001L
        cache.route(origin, nextStop, "navigation:customer-1:0")
        assertEquals(2, delegate.calls)
    }

    @Test
    fun invalidFirstResponseIsNotCachedAndNextRetryUsesRecoveredProvider() = runBlocking {
        val origin = NavigationMapPoint(13.0, 100.0)
        val destination = stop()
        var calls = 0
        val delegate = object : NavigationRouteProvider {
            override suspend fun route(
                from: NavigationMapPoint, stop: NavigationMapStop, segmentId: String
            ): NavigationRouteLeg {
                calls += 1
                return NavigationRouteLeg(
                    segment = NavigationRouteSegment(segmentId, stop.id, listOf(from, stop.point)),
                    distanceMeters = if (calls == 1) Double.NaN else 500.0,
                    durationSeconds = 60.0
                )
            }
        }
        val cache = CachingNavigationRouteProvider(delegate)
        cache.route(origin, destination, "navigation:customer-1:0")
        assertEquals(0, cache.size())
        val recovered = cache.route(origin, destination, "navigation:customer-1:0")
        assertEquals(2, calls)
        assertEquals(500.0, recovered.distanceMeters, 0.001)
        assertEquals(1, cache.size())
    }

    @Test
    fun wrongDestinationOrOriginCannotBePrefetchedForLaterJob() = runBlocking {
        val origin = NavigationMapPoint(13.0, 100.0)
        val destination = stop()
        var calls = 0
        val delegate = object : NavigationRouteProvider {
            override suspend fun route(
                from: NavigationMapPoint, stop: NavigationMapStop, segmentId: String
            ): NavigationRouteLeg {
                calls += 1
                val segment = when (calls) {
                    1 -> NavigationRouteSegment(segmentId, "another-customer", listOf(from, stop.point))
                    2 -> NavigationRouteSegment(segmentId, stop.id,
                        listOf(NavigationMapPoint(14.0, 100.0), stop.point))
                    else -> NavigationRouteSegment(segmentId, stop.id, listOf(from, stop.point))
                }
                return NavigationRouteLeg(segment, 200.0, 30.0)
            }
        }
        val cache = CachingNavigationRouteProvider(delegate)
        val id = "future:pickup-1>customer-1"
        cache.route(origin, destination, id)
        cache.route(origin, destination, id)
        assertEquals(0, cache.size())
        cache.route(origin, destination, id)
        assertEquals(3, calls)
        assertEquals(1, cache.size())
    }

    @Test
    fun nonRenderableGeometryOrInvalidEtaMustNotPolluteCache() = runBlocking {
        val origin = NavigationMapPoint(13.0, 100.0)
        val destination = stop()
        var calls = 0
        val delegate = object : NavigationRouteProvider {
            override suspend fun route(
                from: NavigationMapPoint, stop: NavigationMapStop, segmentId: String
            ): NavigationRouteLeg {
                calls += 1
                val points = if (calls == 1) listOf(from) else listOf(from, stop.point)
                val duration = if (calls == 2) Double.POSITIVE_INFINITY else 30.0
                return NavigationRouteLeg(
                    NavigationRouteSegment(segmentId, stop.id, points), 200.0, duration
                )
            }
        }
        val cache = CachingNavigationRouteProvider(delegate)
        repeat(2) {
            cache.route(origin, destination, "navigation:customer-1:0")
            assertEquals(0, cache.size())
        }
        cache.route(origin, destination, "navigation:customer-1:0")
        assertEquals(3, calls)
        assertEquals(1, cache.size())
    }
}
