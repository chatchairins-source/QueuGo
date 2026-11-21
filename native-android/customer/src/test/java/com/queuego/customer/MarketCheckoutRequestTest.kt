package com.queuego.customer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.io.IOException

class MarketCheckoutRequestTest {
    private val parent = "11111111-1111-4111-8111-111111111111"
    private val request = "22222222-2222-4222-8222-222222222222"
    private fun pending(add: Boolean): PendingMarketCheckout {
        val body = JSONObject().put("p_market_order_id", parent)
            .put("p_items", JSONArray().put(JSONObject().put("product_id", "product").put("qty", 2)))
            .put("p_note", "original note")
        if (add) body.put("p_request_id", request)
        else body.put("p_delivery_address", "original address").put("p_delivery_lat", 14.99).put("p_delivery_lng", 103.1)
        return PendingMarketCheckout(if (add) "queuego_add_market_order_shops" else "queuego_place_market_order", body, "original cart")
    }
    @Test fun newAndAddRequestsRetainExactPayloadAfterLostReplyAndRestart() = runBlocking {
        for (add in listOf(false, true)) {
            var disk: String? = null
            val journal = object : CheckoutJournal<PendingMarketCheckout> {
                override fun read() = disk?.let(PendingMarketCheckout::decode)
                override fun write(pending: PendingMarketCheckout) { disk = pending.encode() }
                override fun clear() { disk = null }
                override fun complete(pending: PendingMarketCheckout) { clear() }
            }
            val original = pending(add)
            try {
                CustomerCheckoutRecovery<PendingMarketCheckout, String>(journal).submit(
                    { original }, { throw IOException("lost reply") }, { null }, { false })
                fail()
            } catch (_: IOException) { }
            val receipt = CustomerCheckoutRecovery<PendingMarketCheckout, String>(journal).submit(
                { error("must not rebuild from changed cart/trip/address") },
                { assertEquals(original.encode(), it.encode()); it.body.getString("p_market_order_id") },
                { null }, { false })
            assertEquals(parent, receipt)
            assertNull(disk)
        }
    }
    @Test fun invalidJournalFailsClosed() {
        try { PendingMarketCheckout.decode("{}"); fail() } catch (_: Exception) { }
        try { PendingMarketCheckout("unapproved_rpc", pending(false).body, "cart"); fail() } catch (_: IllegalArgumentException) { }
    }
}
