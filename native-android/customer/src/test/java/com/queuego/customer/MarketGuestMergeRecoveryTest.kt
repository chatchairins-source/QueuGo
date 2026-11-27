package com.queuego.customer

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MarketGuestMergeRecoveryTest {
    @Test fun destinationWriteFailureNeverClearsGuest() {
        var guestQuantity = 2
        try {
            commitMarketGuestMerge(false, { throw IOException("disk full") }, { guestQuantity = 0 })
            fail()
        } catch (_: IOException) { }
        assertEquals(2, guestQuantity)
    }
    @Test fun restartAfterGuestCleanupFailureDoesNotDoubleMerge() {
        var accountQuantity = 1
        var guestQuantity = 2
        var revisionCommitted = false
        try {
            commitMarketGuestMerge(revisionCommitted,
                { accountQuantity += guestQuantity; revisionCommitted = true },
                { throw IOException("process stopped before source cleanup") })
            fail()
        } catch (_: IOException) { }
        assertEquals(3, accountQuantity)
        assertEquals(2, guestQuantity)
        commitMarketGuestMerge(revisionCommitted,
            { fail("same guest revision must not be added again") }, { guestQuantity = 0 })
        assertEquals(3, accountQuantity)
        assertEquals(0, guestQuantity)
    }
}
