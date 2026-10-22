package com.queuego.rider

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal fun riderNavigationLocationPushIntervalMs(distanceMeters: Double?): Long =
    if (distanceMeters != null && distanceMeters.isFinite() && distanceMeters <= 200.0) 1_000L else 3_000L

internal fun riderNavigationDistanceMeters(
    latitude: Double,
    longitude: Double,
    targetLatitude: Double?,
    targetLongitude: Double?
): Double? {
    val targetLat = targetLatitude?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
    val targetLng = targetLongitude?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
    if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
        return null
    }
    val radiusMeters = 6_371_000.0
    val dLat = Math.toRadians(targetLat - latitude)
    val dLng = Math.toRadians(targetLng - longitude)
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(targetLat)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * radiusMeters * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/** Navigation reuses the same acceptance preference and server lease; it never claims an order. */
internal object RiderNavigationDispatchReadiness {
    internal data class Binding(
        val userId: String, val sessionId: String, val profileId: String?,
        val orderId: String?, val ready: Boolean,
        val targetLatitude: Double? = null, val targetLongitude: Double? = null
    )
    @Volatile private var binding: Binding? = null
    private val mutex = Mutex()
    private val locationMutex = Mutex()

    fun bind(next: Binding) { binding = next }
    fun clear(userId: String) { if (binding?.userId == userId) binding = null }

    suspend fun syncForeground(context: Context, api: QueueGoApi, auth: QueueGoAuth) = mutex.withLock {
        val bound = binding ?: return@withLock
        if (bound.userId != auth.user.id || bound.sessionId != auth.session.sessionId) return@withLock
        // Read stores inside the shared lock: a background request cannot restore an older toggle.
        api.syncDispatchPreference(auth,
            RiderAcceptanceModeStore(context).autoAcceptEnabled(auth.user.id),
            RiderStopAfterWorkStore(context).pending(auth.user.id), bound.ready)
    }

    suspend fun syncLocation(
        context: Context,
        api: QueueGoApi,
        fixElapsedRealtimeNanos: Long?,
        latitude: Double?,
        longitude: Double?
    ): Long = locationMutex.withLock {
        val bound = binding ?: return@withLock 3_000L
        val lat = latitude?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return@withLock 1_000L
        val lng = longitude?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return@withLock 1_000L
        val fixAgeMs = fixElapsedRealtimeNanos?.let {
            (SystemClock.elapsedRealtimeNanos() - it) / 1_000_000L
        }
        if (fixAgeMs == null || fixAgeMs !in 0L..10_000L) return@withLock 1_000L
        val auth = SessionStore(context).load() ?: return@withLock 3_000L
        if (
            bound.userId != auth.user.id ||
            bound.sessionId != auth.session.sessionId ||
            !bound.ready ||
            bound.orderId == null
        ) return@withLock 3_000L
        api.updateLocation(auth, lat, lng)
        riderNavigationLocationPushIntervalMs(
            riderNavigationDistanceMeters(lat, lng, bound.targetLatitude, bound.targetLongitude)
        )
    }

    suspend fun syncNavigation(context: Context, api: QueueGoApi, fixElapsedRealtimeNanos: Long?, latitude: Double?, longitude: Double?) = mutex.withLock {
        fun fixAgeMs() = fixElapsedRealtimeNanos?.let { (SystemClock.elapsedRealtimeNanos() - it) / 1_000_000L }
        val bound = binding ?: return@withLock // Process recreation requires a new visible session binding.
        val auth = SessionStore(context).load() ?: return@withLock
        if (bound.userId != auth.user.id || bound.sessionId != auth.session.sessionId) return@withLock
        val auto = RiderAcceptanceModeStore(context).autoAcceptEnabled(auth.user.id)
        val stop = RiderStopAfterWorkStore(context).pending(auth.user.id)
        if (!riderNavigationDispatchLeaseEligible(bound.ready, auto, stop,
                bound.orderId != null && bound.profileId != null,
                auth.session.expiresAtMs - System.currentTimeMillis(), fixAgeMs())) {
            api.syncDispatchPreference(auth, auto, stop, false)
            return@withLock
        }
        val job = api.notificationJob(auth, bound.orderId!!, bound.profileId!!)
        if (binding != bound) return@withLock
        if (job == null || job.id != bound.orderId || job.status !in setOf("rider_assigned", "preparing", "ready", "assigned", "picked_up", "in_progress")) {
            api.syncDispatchPreference(auth, auto, stop, false)
            return@withLock
        }
        if (latitude == null || longitude == null) return@withLock
        // Re-read after network suspension so Stop-after-work/manual mode always wins.
        val latestAuto = RiderAcceptanceModeStore(context).autoAcceptEnabled(auth.user.id)
        val latestStop = RiderStopAfterWorkStore(context).pending(auth.user.id)
        api.syncDispatchPreference(auth, latestAuto, latestStop,
            binding == bound && riderNavigationDispatchLeaseEligible(bound.ready, latestAuto, latestStop, true,
                auth.session.expiresAtMs - System.currentTimeMillis(), fixAgeMs()))
    }
}
