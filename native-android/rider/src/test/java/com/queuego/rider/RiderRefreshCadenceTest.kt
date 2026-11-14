package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderRefreshCadenceTest {
    @Test fun activeStatesStayCriticalWhileOnlineIdleBacksOff() {
        assertEquals(
            RIDER_REFRESH_ONLINE_IDLE_MS,
            riderSnapshotPollIntervalMs(
                networkAvailable = true,
                online = true,
                hasActiveWork = false,
                hasOffer = false
            )
        )
        assertEquals(
            RIDER_REFRESH_CRITICAL_MS,
            riderSnapshotPollIntervalMs(
                networkAvailable = true,
                online = false,
                hasActiveWork = true,
                hasOffer = false
            )
        )
        assertEquals(
            RIDER_REFRESH_CRITICAL_MS,
            riderSnapshotPollIntervalMs(
                networkAvailable = true,
                online = false,
                hasActiveWork = false,
                hasOffer = true
            )
        )
    }

    @Test fun offlineIdleAndNoNetworkBackOffWithoutBecomingLongLived() {
        assertEquals(
            RIDER_REFRESH_OFFLINE_IDLE_MS,
            riderSnapshotPollIntervalMs(
                networkAvailable = true,
                online = false,
                hasActiveWork = false,
                hasOffer = false
            )
        )
        assertEquals(
            RIDER_REFRESH_NO_NETWORK_MS,
            riderSnapshotPollIntervalMs(
                networkAvailable = false,
                online = true,
                hasActiveWork = true,
                hasOffer = true
            )
        )
        assertTrue(RIDER_REFRESH_ONLINE_IDLE_MS in 5_000L..10_000L)
        assertTrue(RIDER_REFRESH_NO_NETWORK_MS <= 15_000L)
        assertTrue(RIDER_REFRESH_OFFLINE_IDLE_MS <= 15_000L)
    }
}
