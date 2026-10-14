package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RiderServerClockTest {
    @Test fun parsesStandardHttpDateAndRejectsGarbage() {
        assertEquals(
            Instant.parse("2026-10-14T09:20:30Z").toEpochMilli(),
            riderServerEpochFromHttpDate("Wed, 14 Oct 2026 09:20:30 GMT")
        )
        assertNull(riderServerEpochFromHttpDate(null))
        assertNull(riderServerEpochFromHttpDate("not-a-date"))
    }

    @Test fun monotonicElapsedTimeKeepsServerClockStableWhenDeviceWallClockChanges() {
        val sample = RiderServerClockSample(
            serverEpochMs = 1_000_000L,
            elapsedRealtimeMs = 50_000L,
            deviceWallEpochMs = 9_000_000L
        )
        assertEquals(
            1_007_500L,
            riderServerClockNow(
                sample = sample,
                elapsedRealtimeMs = 57_500L,
                fallbackWallEpochMs = 99_000_000L
            )
        )
        assertEquals(-8_000_000L, riderServerClockSkewMs(sample))
    }

    @Test fun missingOrInvalidElapsedSampleFailsBackToDeviceWallClock() {
        assertEquals(
            123_456L,
            riderServerClockNow(null, elapsedRealtimeMs = 10L, fallbackWallEpochMs = 123_456L)
        )
        val sample = RiderServerClockSample(1_000L, 500L, 900L)
        assertEquals(
            777L,
            riderServerClockNow(sample, elapsedRealtimeMs = 499L, fallbackWallEpochMs = 777L)
        )
    }
}
