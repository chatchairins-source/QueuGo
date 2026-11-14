package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPuckFreshnessTest {
    private val fix = NavigationMapFix(
        point = NavigationMapPoint(13.0, 100.0),
        elapsedRealtimeNanos = 10_000_000_000L
    )

    @Test
    fun freshBeforeTimeout() {
        val state = navigationPuckFreshness(
            fix = fix,
            nowElapsedRealtimeNanos = 13_000_000_000L
        )
        assertEquals(NavigationPuckFreshness.FRESH, state.freshness)
        assertEquals(3_000L, state.ageMillis)
    }

    @Test
    fun staleAfterFiveSecondsAndLostAfterFifteen() {
        val stale = navigationPuckFreshness(
            fix = fix,
            nowElapsedRealtimeNanos = 16_000_000_000L
        )
        val lost = navigationPuckFreshness(
            fix = fix,
            nowElapsedRealtimeNanos = 26_000_000_000L
        )

        assertEquals(NavigationPuckFreshness.STALE, stale.freshness)
        assertEquals(NavigationPuckFreshness.LOST, lost.freshness)
    }

    @Test
    fun unknownWhenFixHasNoMonotonicTimestamp() {
        val state = navigationPuckFreshness(
            fix = fix.copy(elapsedRealtimeNanos = 0L),
            nowElapsedRealtimeNanos = 20_000_000_000L
        )
        assertEquals(NavigationPuckFreshness.UNKNOWN, state.freshness)
    }
}
