package com.queuego.customer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CustomerRideRecoveryTest {
    private class Journal : CheckoutJournal<PendingRide> {
        var value: PendingRide? = null
        var writable = true
        override fun read() = value
        override fun write(pending: PendingRide) { check(writable);value=pending }
        override fun clear() { value=null }
        override fun complete(pending: PendingRide) { if(value==pending) clear() }
    }
    @Test fun processDeathKeepsTheOriginalQuoteAndRequestId() = runBlocking {
        val journal=Journal()
        val request=PendingRide("00000000-0000-4000-8000-000000000001","00000000-0000-4000-8000-000000000002")
        try { CustomerCheckoutRecovery<PendingRide,String>(journal).submit({request},{throw IOException()},{throw IOException()},{false});fail() }
        catch (_: IOException) {}
        var sent: PendingRide?=null
        val receipt=CustomerCheckoutRecovery<PendingRide,String>(journal).submit({error("cannot make a second trip")},{sent=it;it.requestId},{null},{false})
        assertEquals(request,sent);assertEquals(request.requestId,receipt);assertNull(journal.value)
    }
    @Test fun requestCannotReachTheServerIfPersistenceFails() = runBlocking {
        val journal=Journal().apply { writable=false };var called=false
        try { CustomerCheckoutRecovery<PendingRide,String>(journal).submit({PendingRide("request","quote")},{called=true;"wrong"},{null},{false});fail() }
        catch (_: IllegalStateException) {}
        assertFalse(called)
    }
}
