package com.queuego.customer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

internal interface CheckoutJournal<P> {
    fun read(): P?
    fun write(pending: P)
    fun clear()
    fun complete(pending: P)
}

internal class CheckoutInProgress : IllegalStateException("กำลังตรวจผลคำสั่งซื้อ กรุณารอสักครู่")

/** Persist before sending; reuse the exact request across failures and process restarts.
 * Recovery is an owner-scoped read, never a new order request. */
internal class CustomerCheckoutRecovery<P, R>(private val journal: CheckoutJournal<P>) {
    private val sending = AtomicBoolean(false)

    suspend fun submit(
        create: suspend () -> P,
        send: suspend (P) -> R,
        recover: suspend (P) -> R?,
        definitiveRejection: (Exception) -> Boolean
    ): R {
        if (!sending.compareAndSet(false, true)) throw CheckoutInProgress()
        try {
            val pending = withContext(Dispatchers.IO) { journal.read() } ?: create().also { created ->
                withContext(Dispatchers.IO) { journal.write(created) }
            }
            val receipt = try { send(pending) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val found = try { recover(pending) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                if (found != null) found
                else {
                    if (definitiveRejection(e)) withContext(Dispatchers.IO) { journal.clear() }
                    throw e
                }
            }
            // An acknowledged server order must never be reported as failed because
            // local cleanup failed. Its journal can be reconciled on the next launch.
            runCatching { withContext(Dispatchers.IO) { journal.complete(pending) } }
            return receipt
        } finally { sending.set(false) }
    }

    suspend fun reconcile(recover: suspend (P) -> R?): R? {
        if (!sending.compareAndSet(false, true)) return null
        try {
            val pending = withContext(Dispatchers.IO) { journal.read() } ?: return null
            val receipt = recover(pending) ?: return null
            runCatching { withContext(Dispatchers.IO) { journal.complete(pending) } }
            return receipt
        } finally { sending.set(false) }
    }
}
