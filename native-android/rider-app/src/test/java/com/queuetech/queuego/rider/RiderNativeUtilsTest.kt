package com.queuetech.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RiderNativeUtilsTest {
    @Test
    fun supabaseTimestampWithMicrosecondsParses() {
        val parsed = RiderServerTime.parseMillis("2026-10-08T08:30:12.123456+00:00")
        assertNotNull(parsed)
    }

    @Test
    fun riderActionIdIsStableForRetry() {
        val first = RiderActionIds.stable(
            "11111111-1111-1111-1111-111111111111",
            "claim",
            "22222222-2222-2222-2222-222222222222",
        )
        val second = RiderActionIds.stable(
            "11111111-1111-1111-1111-111111111111",
            "claim",
            "22222222-2222-2222-2222-222222222222",
        )
        assertEquals(first, second)
    }
}
