package com.queuego.customer

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.io.IOException

class LaundryCheckoutRequestTest {
    private fun pending() = PendingLaundryCheckout(JSONObject()
        .put("p_request_id", "11111111-1111-4111-8111-111111111111")
        .put("p_hub_id", "22222222-2222-4222-8222-222222222222")
        .put("p_service_id", "33333333-3333-4333-8333-333333333333")
        .put("p_pickup_address", "original address").put("p_pickup_latitude", 14.99)
        .put("p_pickup_longitude", 103.1).put("p_estimated_quantity", JSONObject.NULL)
        .put("p_note", "original note"))
    @Test fun lostReplyAndRestartReuseOriginalRequestIncludingAddressAndNote() = runBlocking {
        var disk: String? = null
        val journal = object : CheckoutJournal<PendingLaundryCheckout> {
            override fun read() = disk?.let(PendingLaundryCheckout::decode)
            override fun write(pending: PendingLaundryCheckout) { disk = pending.encode() }
            override fun clear() { disk = null }
            override fun complete(pending: PendingLaundryCheckout) { clear() }
        }
        val original = pending()
        try {
            CustomerCheckoutRecovery<PendingLaundryCheckout, String>(journal).submit(
                { original }, { throw IOException("lost response") }, { null }, { false })
            fail()
        } catch (_: IOException) { }
        val result = CustomerCheckoutRecovery<PendingLaundryCheckout, String>(journal).submit(
            { error("must not recreate from another hub or edited location") },
            { assertEquals(original.encode(), it.encode()); "receipt" }, { null }, { false })
        assertEquals("receipt", result)
        assertNull(disk)
    }
    @Test fun invalidQuantityCoordinatesAndIncompleteJournalFailBeforeSend() {
        for (raw in listOf("{}", pending().body.put("p_estimated_quantity", 1001).toString(),
            pending().body.put("p_pickup_latitude", 91).toString())) {
            try { PendingLaundryCheckout.decode(raw); fail() } catch (_: Exception) { }
        }
    }
}
