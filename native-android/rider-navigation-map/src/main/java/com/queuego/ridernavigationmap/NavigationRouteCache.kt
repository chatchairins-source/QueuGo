package com.queuego.ridernavigationmap

private const val DEFAULT_ROUTE_CACHE_TTL_MILLIS = 8 * 60 * 1_000L
private const val DEFAULT_ROUTE_CACHE_ORIGIN_RADIUS_METERS = 75.0
private const val DEFAULT_ROUTE_CACHE_DESTINATION_RADIUS_METERS = 5.0
private const val DEFAULT_ROUTE_CACHE_MAX_ENTRIES = 24

private data class CachedNavigationRoute(
    val origin: NavigationMapPoint,
    val destination: NavigationMapPoint,
    val stopId: String,
    val createdAtMillis: Long,
    val leg: NavigationRouteLeg
)

/**
 * Small in-memory navigation cache used for two cases only:
 * 1) equivalent route requests caused by UI/session refresh, and
 * 2) the already-prefetched next leg when the Rider has just arrived at its planned origin.
 *
 * Reroutes always bypass reuse so off-route recovery remains authoritative and fresh.
 */
class CachingNavigationRouteProvider(
    private val delegate: NavigationRouteProvider,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val ttlMillis: Long = DEFAULT_ROUTE_CACHE_TTL_MILLIS,
    private val reuseOriginRadiusMeters: Double = DEFAULT_ROUTE_CACHE_ORIGIN_RADIUS_METERS,
    private val maxEntries: Int = DEFAULT_ROUTE_CACHE_MAX_ENTRIES
) : NavigationRouteProvider {
    private val lock = Any()
    private val entries = mutableListOf<CachedNavigationRoute>()

    override suspend fun route(
        from: NavigationMapPoint,
        stop: NavigationMapStop,
        segmentId: String
    ): NavigationRouteLeg {
        require(from.valid && stop.point.valid) { "Invalid navigation coordinates" }

        val now = nowMillis()
        val cached = if (shouldBypassCache(segmentId)) {
            null
        } else {
            synchronized(lock) {
                pruneLocked(now)
                entries.asReversed().firstOrNull { entry ->
                    entry.stopId == stop.id &&
                        navigationHaversineMeters(entry.destination, stop.point) <=
                            DEFAULT_ROUTE_CACHE_DESTINATION_RADIUS_METERS &&
                        navigationHaversineMeters(entry.origin, from) <=
                            reuseOriginRadiusMeters.coerceAtLeast(0.0)
                }
            }
        }

        if (cached != null) {
            return cached.leg.copy(
                segment = cached.leg.segment.copy(
                    id = segmentId,
                    toStopId = stop.id
                )
            )
        }

        val fresh = delegate.route(from, stop, segmentId)
        // The coordinator validates a route before rendering it, but a rejected
        // provider result must never enter this cache. Otherwise an invalid
        // response can be replayed for up to eight minutes on every retry,
        // even after the provider itself recovers.
        if (!routeIsSafeToCache(fresh, from, stop, segmentId)) {
            return fresh
        }
        synchronized(lock) {
            pruneLocked(nowMillis())
            entries.removeAll { entry ->
                entry.stopId == stop.id &&
                    navigationHaversineMeters(entry.origin, from) <= 5.0 &&
                    navigationHaversineMeters(entry.destination, stop.point) <=
                        DEFAULT_ROUTE_CACHE_DESTINATION_RADIUS_METERS
            }
            entries += CachedNavigationRoute(
                origin = from,
                destination = stop.point,
                stopId = stop.id,
                createdAtMillis = nowMillis(),
                leg = fresh
            )
            while (entries.size > maxEntries.coerceAtLeast(1)) {
                entries.removeAt(0)
            }
        }
        return fresh
    }

    private fun routeIsSafeToCache(
        leg: NavigationRouteLeg,
        origin: NavigationMapPoint,
        stop: NavigationMapStop,
        requestedSegmentId: String
    ): Boolean {
        if (!leg.segment.renderable ||
            leg.segment.id != requestedSegmentId ||
            leg.segment.toStopId != stop.id
        ) return false
        val points = leg.segment.points
        if (!points.all { it.valid }) return false
        if (navigationHaversineMeters(points.first(), origin) > 750.0 ||
            navigationHaversineMeters(points.last(), stop.point) > 750.0
        ) return false
        if (!leg.distanceMeters.isFinite() || leg.distanceMeters <= 0.0) return false
        if (leg.durationSeconds != null &&
            (!leg.durationSeconds.isFinite() || leg.durationSeconds < 0.0)
        ) return false
        return true
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    internal fun size(): Int = synchronized(lock) { entries.size }

    private fun pruneLocked(now: Long) {
        val ttl = ttlMillis.coerceAtLeast(1L)
        // Wall-clock time may jump backwards after network/GPS time correction.
        // A negative age must invalidate the entry rather than extending its TTL.
        entries.removeAll {
            now < it.createdAtMillis || now - it.createdAtMillis > ttl
        }
    }

    private fun shouldBypassCache(segmentId: String): Boolean {
        if (!segmentId.startsWith("navigation:")) return false
        return (segmentId.substringAfterLast(':').toIntOrNull() ?: 0) > 0
    }
}
