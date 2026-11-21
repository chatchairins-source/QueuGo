package com.queuego.customer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CustomerNotificationLookupTest {
    @Test fun directOrderOutsideRecentListDoesNotRequireTheList() = runBlocking {
        val result = lookupCustomerNotification<String>(listOf(
            { "old-order" }, { error("must not read another domain") }
        ))
        assertEquals("old-order", result)
    }

    @Test fun marketAndLaundryReferencesResolveAfterNormalOrderIsAbsent() = runBlocking {
        assertEquals("market", lookupCustomerNotification<String>(listOf({ null }, { "market" }, { null })))
        assertEquals("laundry", lookupCustomerNotification<String>(listOf({ null }, { null }, { "laundry" })))
    }

    @Test fun aFailedDomainDoesNotBlockAnAuthoritativeMatch() = runBlocking {
        assertEquals("laundry", lookupCustomerNotification<String>(listOf(
            { throw IOException("normal unavailable") }, { null }, { "laundry" }
        )))
    }

    @Test fun transportFailureIsRetriableRatherThanMissing() = runBlocking {
        val error = IOException("offline")
        try {
            lookupCustomerNotification<String>(listOf({ null }, { throw error }, { null }))
            fail("must retain the failure")
        } catch (actual: IOException) { assertSame(error, actual) }
        assertEquals("reconnected", lookupCustomerNotification<String>(listOf({ null }, { "reconnected" })))
    }

    @Test fun missingRequiresAllReadsToSucceed() = runBlocking {
        assertNull(lookupCustomerNotification<String>(listOf({ null }, { null }, { null })))
    }

    @Test fun cancellationStopsBeforeAnotherAccountOrDomainRead() = runBlocking {
        var nextRead = false
        try {
            lookupCustomerNotification<String>(listOf(
                { throw CancellationException("account changed") },
                { nextRead = true; "stale" }
            ))
            fail("must cancel")
        } catch (_: CancellationException) { }
        assertFalse(nextRead)
    }
}
