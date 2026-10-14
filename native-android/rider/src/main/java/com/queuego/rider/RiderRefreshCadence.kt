package com.queuego.rider

internal const val RIDER_REFRESH_CRITICAL_MS = 3_000L
internal const val RIDER_REFRESH_NO_NETWORK_MS = 12_000L
internal const val RIDER_REFRESH_OFFLINE_IDLE_MS = 15_000L

internal fun riderSnapshotPollIntervalMs(
    networkAvailable: Boolean,
    online: Boolean,
    hasActiveWork: Boolean,
    hasOffer: Boolean
): Long = when {
    !networkAvailable -> RIDER_REFRESH_NO_NETWORK_MS
    hasActiveWork || hasOffer -> RIDER_REFRESH_CRITICAL_MS
    online -> RIDER_REFRESH_CRITICAL_MS
    else -> RIDER_REFRESH_OFFLINE_IDLE_MS
}
