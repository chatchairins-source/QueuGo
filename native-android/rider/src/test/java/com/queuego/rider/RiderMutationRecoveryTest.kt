package com.queuego.rider

import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class RiderMutationRecoveryTest {
    @Test fun lostResponseUsesExactReceiptWithoutSubmittingTwice() {
        var writes = 0
        val result = riderRecoverMutation(
            mutate = { writes++; throw SocketTimeoutException("response lost") },
            readCommitted = { "in_progress" }
        )
        assertEquals("in_progress", result)
        assertEquals(1, writes)
    }

    @Test fun missingReceiptKeepsOriginalFailure() {
        val failure = SocketTimeoutException("not committed")
        try {
            riderRecoverMutation<String>({ throw failure }, { null })
            fail("must not invent success")
        } catch (caught: SocketTimeoutException) { assertSame(failure, caught) }
    }

    @Test fun failedReconcileNeverInventsCommit() {
        val failure = SocketTimeoutException("ambiguous")
        try {
            riderRecoverMutation<String>({ throw failure }, { throw java.io.IOException("offline") })
            fail("must retain pending work")
        } catch (caught: SocketTimeoutException) {
            assertSame(failure, caught)
            assertEquals(1, caught.suppressed.size)
        }
    }

    @Test fun authorizationAndBusinessErrorsDoNotReconcile() {
        var reads = 0
        try {
            riderRecoverMutation<String>({ throw RiderHttpException(403, "denied") }, { reads++; "completed" })
            fail("must not suppress denial")
        } catch (_: RiderHttpException) { assertEquals(0, reads) }
    }

    @Test fun successfulWriteDoesNotAddReadOrRetry() {
        assertEquals("shop", riderRecoverMutation({ "shop" }, { error("unexpected read") }))
    }

    @Test fun coroutineCancellationPropagates() {
        try {
            riderRecoverMutation<String>({ throw SocketTimeoutException() }, { throw CancellationException("stopped") })
            fail("must propagate lifecycle cancellation")
        } catch (_: CancellationException) { }
    }
}
