package com.queuego.rider

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class RiderOfferLifetimeTest {
    private val deadline = Instant.parse("2026-10-12T07:00:30Z").toEpochMilli()

    @Test fun expiredAndMalformedOffersAreNeverActionable() {
        assertEquals(0, riderOfferSecondsLeft(null, deadline))
        assertEquals(0, riderOfferSecondsLeft("invalid", deadline))
        assertEquals(0, riderOfferSecondsLeft("2026-10-12T07:00:30Z", deadline))
        assertEquals(0, riderOfferSecondsLeft("2026-10-12T07:00:30Z", deadline + 1))
    }

    @Test fun countdownUsesTheServerDeadlineIncludingFractionalSeconds() {
        assertEquals(30, riderOfferSecondsLeft("2026-10-12T07:00:30Z", deadline - 30_000))
        assertEquals(1, riderOfferSecondsLeft("2026-10-12T07:00:30Z", deadline - 1))
        assertEquals(2, riderOfferSecondsLeft("2026-10-12T07:00:30Z", deadline - 1001))
        assertEquals(30, riderOfferSecondsLeft("2026-10-12T14:00:30+07:00", deadline - 30_000))
    }

    @Test fun renewedOfferDeadlineRemainsActionableAfterOldDeadlineExpires() {
        assertEquals(0, riderOfferSecondsLeft("2026-10-12T07:00:30Z", deadline + 1000))
        assertEquals(29, riderOfferSecondsLeft("2026-10-12T07:01:00Z", deadline + 1000))
    }

    @Test fun pushReferencesMatchOnlyTheExactOrderOrItsMarketParent() {
        val job = RiderJob("child-id", "0012", "completed", null, null, null,
            null, null, null, null, "parent-id", arrivedShopAt = null, arrivedCustomerAt = null)
        assertTrue(job.matchesPushReference("child-id"))
        assertTrue(job.matchesPushReference("parent-id"))
        assertFalse(job.matchesPushReference("another-order"))
        assertFalse(job.matchesPushReference(""))
        assertFalse(job.matchesPushReference("0012"))
    }
}
