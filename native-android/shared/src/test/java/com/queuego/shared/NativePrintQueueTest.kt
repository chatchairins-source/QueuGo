package com.queuego.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePrintQueueTest {
    @Test fun duplicateJobsPrintOnceAndDifferentJobsAreNotDropped() = runBlocking {
        val queue = NativePrintQueue()
        val completed = mutableSetOf<String>()
        val sent = mutableListOf<String>()
        val jobs = listOf("kitchen:a:1", "kitchen:a:1", "receipt:a", "kitchen:b:1")
        jobs.map { key -> async {
            queue.submit(key, wasPrinted = completed::contains, markPrinted = { completed.add(it) }) {
                sent.add(key)
            }
        } }.awaitAll()
        assertEquals(3, sent.size)
        assertEquals(3, completed.size)
        assertEquals(1, sent.count { it == "kitchen:a:1" })
    }

    @Test fun failedBridgeDoesNotMarkPrintedAndQueueStillAcceptsExplicitRetry() = runBlocking {
        val queue = NativePrintQueue()
        val completed = mutableSetOf<String>()
        try {
            queue.submit("receipt:a", wasPrinted = completed::contains, markPrinted = { completed.add(it) }) {
                error("Bridge disconnected")
            }
        } catch (_: IllegalStateException) { }
        assertTrue(completed.isEmpty())
        assertTrue(queue.submit("receipt:a", wasPrinted = completed::contains, markPrinted = { completed.add(it) }) {})
        assertFalse(queue.submit("receipt:a", wasPrinted = completed::contains, markPrinted = { completed.add(it) }) {
            error("Completed job must not be sent again")
        })
    }

    @Test fun cancellationReleasesQueueWithoutMarkingUnacknowledgedPrint() = runBlocking {
        val queue = NativePrintQueue()
        val started = CompletableDeferred<Unit>()
        val completed = mutableSetOf<String>()
        val job = launch {
            queue.submit("kitchen:a:1", wasPrinted = completed::contains, markPrinted = { completed.add(it) }) {
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(completed.isEmpty())
        assertTrue(queue.submit("kitchen:b:1", wasPrinted = completed::contains, markPrinted = { completed.add(it) }) {})
    }

    @Test fun forcedReprintAndNextKitchenBatchRemainAvailable() = runBlocking {
        val queue = NativePrintQueue()
        val completed = mutableSetOf("kitchen:a:1")
        var sent = 0
        assertTrue(queue.submit("kitchen:a:1", force = true, wasPrinted = completed::contains, markPrinted = { completed.add(it) }) { sent++ })
        assertTrue(queue.submit("kitchen:a:2", wasPrinted = completed::contains, markPrinted = { completed.add(it) }) { sent++ })
        assertEquals(2, sent)
    }
}
