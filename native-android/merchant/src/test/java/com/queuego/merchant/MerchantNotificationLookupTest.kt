package com.queuego.merchant

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MerchantNotificationLookupTest {
    @Test fun currentAuthoritativeSnapshotAvoidsAnotherRead() = runBlocking {
        var refreshed = false
        val result = lookupMerchantNotificationOrder(
            referenceId = "order-1",
            current = listOf("order-1"),
            idOf = { it },
            refresh = { refreshed = true; emptyList() }
        )
        assertEquals("order-1", result.order)
        assertFalse(refreshed)
    }

    @Test fun freshShopScopedSnapshotCanResolveReference() = runBlocking {
        val result = lookupMerchantNotificationOrder(
            referenceId = "order-2",
            current = listOf("order-1"),
            idOf = { it },
            refresh = { listOf("order-2", "order-3") }
        )
        assertEquals("order-2", result.order)
        assertEquals(listOf("order-2", "order-3"), result.snapshot)
    }

    @Test fun missingIsOnlyReturnedAfterRefreshSucceeds() = runBlocking {
        val result = lookupMerchantNotificationOrder(
            referenceId = "missing",
            current = emptyList<String>(),
            idOf = { it },
            refresh = { listOf("other") }
        )
        assertNull(result.order)
        assertEquals(listOf("other"), result.snapshot)
    }

    @Test fun transportFailureRemainsRetriable() = runBlocking {
        val offline = IOException("offline")
        try {
            lookupMerchantNotificationOrder(
                referenceId = "order-4",
                current = emptyList<String>(),
                idOf = { it },
                refresh = { throw offline }
            )
            fail("transport failure must not be converted to missing")
        } catch (actual: IOException) {
            assertSame(offline, actual)
        }
    }

    @Test fun cancellationIsNeverConvertedToMissing() = runBlocking {
        try {
            lookupMerchantNotificationOrder(
                referenceId = "order-5",
                current = emptyList<String>(),
                idOf = { it },
                refresh = { throw CancellationException("session changed") }
            )
            fail("cancellation must propagate")
        } catch (_: CancellationException) {
        }
    }
}
