package com.queuego.rider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiderOfferPerformanceTest {
    @Test fun parsesRealAggregateContract() {
        val value = riderOfferPerformanceFromRpc(
            JSONObject()
                .put("days", 7)
                .put("offered", 49)
                .put("accepted", 16)
                .put("declined", 2)
                .put("expired", 31)
                .put("acceptance_rate", 32.7)
                .put("response_rate", 36.7)
                .put("decline_rate", 4.1)
                .put("timeout_rate", 63.3)
                .put("average_response_seconds", 14.3),
            7
        )
        assertEquals(49, value.offered)
        assertEquals(16, value.accepted)
        assertEquals(31, value.expired)
        assertEquals(32.7, value.acceptanceRate!!, 0.0001)
        assertEquals(14.3, value.averageResponseSeconds!!, 0.0001)
        assertEquals("32.7%", riderOfferPercent(value.acceptanceRate))
        assertEquals("14.3 วินาที", riderOfferResponseTime(value.averageResponseSeconds))
    }

    @Test fun emptyAggregateDoesNotInventRates() {
        val value = riderOfferPerformanceFromRpc(
            JSONObject()
                .put("days", 1)
                .put("offered", 0)
                .put("accepted", 0)
                .put("declined", 0)
                .put("expired", 0),
            1
        )
        assertEquals(0, value.offered)
        assertNull(value.acceptanceRate)
        assertNull(value.responseRate)
        assertNull(value.averageResponseSeconds)
    }
}
