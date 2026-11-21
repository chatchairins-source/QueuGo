package com.queuego.customer

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CustomerCheckoutRecoveryTest {
    private class Journal : CheckoutJournal<String> {
        var pending: String? = null
        var canWrite = true
        var cleanupFails = false
        override fun read() = pending
        override fun write(pending: String) { check(canWrite); this.pending = pending }
        override fun clear() { pending = null }
        override fun complete(pending: String) { check(!cleanupFails); clear() }
    }

    @Test fun lostReplyRecoversCommittedOrderWithoutReportingAnError() = runBlocking {
        val journal = Journal()
        val engine = CustomerCheckoutRecovery<String, String>(journal)
        var committed: String? = null
        val receipt = engine.submit({ "exact-request" }, { committed = it; throw IOException("reply lost") }, { committed }, { false })
        assertEquals("exact-request", receipt)
        assertNull(journal.pending)
    }

    @Test fun processRestartAndEditedCartReuseOriginalRequestWhenBothSendAndReadFailed() = runBlocking {
        val journal = Journal()
        val first = CustomerCheckoutRecovery<String, String>(journal)
        try { first.submit({ "original-id:cart:address" }, { throw IOException() }, { throw IOException() }, { false }); fail() }
        catch (_: IOException) { }
        val restarted = CustomerCheckoutRecovery<String, String>(journal)
        var sent: String? = null
        val receipt = restarted.submit({ error("must not create from edited cart") }, { sent = it; it }, { null }, { false })
        assertEquals("original-id:cart:address", sent)
        assertEquals(sent, receipt)
        assertNull(journal.pending)
    }

    @Test fun concurrentTapCannotSendSecondOrder() = runBlocking {
        val journal = Journal()
        val engine = CustomerCheckoutRecovery<String, String>(journal)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var sends = 0
        val first = async { engine.submit({ "id" }, { sends++; started.complete(Unit); finish.await(); it }, { null }, { false }) }
        started.await()
        try { engine.submit({ "second" }, { sends++; it }, { null }, { false }); fail() }
        catch (_: CheckoutInProgress) { }
        finish.complete(Unit)
        assertEquals("id", first.await())
        assertEquals(1, sends)
    }

    @Test fun cancellationRetainsDurablePendingForRecoveryOnNextLaunch() = runBlocking {
        val journal = Journal()
        val engine = CustomerCheckoutRecovery<String, String>(journal)
        try { engine.submit({ "id" }, { throw CancellationException() }, { null }, { true }); fail() }
        catch (_: CancellationException) { }
        assertEquals("id", journal.pending)
        assertEquals("confirmed", engine.reconcile { "confirmed" })
        assertNull(journal.pending)
    }

    @Test fun failedJournalWriteNeverSendsAndLocalCleanupFailureNeverChangesServerSuccess() = runBlocking {
        val journal = Journal()
        val engine = CustomerCheckoutRecovery<String, String>(journal)
        journal.canWrite = false
        var sent = false
        try { engine.submit({ "id" }, { sent = true; it }, { null }, { false }); fail() }
        catch (_: IllegalStateException) { }
        assertFalse(sent)
        journal.canWrite = true
        journal.cleanupFails = true
        assertEquals("id", engine.submit({ "id" }, { it }, { null }, { false }))
        assertEquals("id", journal.pending)
    }

    @Test fun definitiveRejectionClearsRequestOnlyWhenNoReceiptWasRecovered() = runBlocking {
        val journal = Journal()
        val engine = CustomerCheckoutRecovery<String, String>(journal)
        try { engine.submit({ "rejected" }, { error("rejected") }, { null }, { true }); fail() }
        catch (_: IllegalStateException) { }
        assertNull(journal.pending)
        assertEquals("confirmed", engine.submit({ "committed" }, { error("response error") }, { "confirmed" }, { true }))
        assertNull(journal.pending)
    }
}
