package com.queuego.rider

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Navigation reuses the same acceptance preference and server lease; it never claims an order. */
internal object RiderNavigationDispatchReadiness {
    internal data class Binding(
        val userId: String, val sessionId: String, val profileId: String?,
        val orderId: String?, val ready: Boolean
    )
    @Volatile private var binding: Binding? = null
    private val mutex = Mutex()

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
        api.updateLocation(auth, latitude, longitude)
        // Re-read after network suspension so Stop-after-work/manual mode always wins.
        val latestAuto = RiderAcceptanceModeStore(context).autoAcceptEnabled(auth.user.id)
        val latestStop = RiderStopAfterWorkStore(context).pending(auth.user.id)
        api.syncDispatchPreference(auth, latestAuto, latestStop,
            binding == bound && riderNavigationDispatchLeaseEligible(bound.ready, latestAuto, latestStop, true,
                auth.session.expiresAtMs - System.currentTimeMillis(), fixAgeMs()))
    }
}
