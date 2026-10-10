package com.queuego.customer

import com.queuego.shared.NativeAuth
import com.queuego.shared.NativeSession
import com.queuego.shared.NativeUser
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Serialize the actual client body without calling Auth, checkout, or Production. */
class CustomerCheckoutRpcContractTest {
    private val api = CustomerApi()
    private val auth = NativeAuth(
        NativeSession("unit-test-user", "unused", null, 0L, "unit-test-session"),
        NativeUser("unit-test-user", "unit-test-user", "", "customer", "active")
    )
    private val shop = CustomerShop("shop", "", "food", null, null, null, 15.0, 103.0, true)
    private val product = CustomerProduct("product", "shop", "", null, 50.0, 50.0, null, true)
    private fun body(note: String?) = api.checkoutBody(
        auth, "request", shop, listOf(CartLine(product, 2)), CustomerLocation(15.0, 103.0, "address"), note
    )

    @Test fun absentAndBlankNotesKeepRequiredNamedArgumentAfterWireSerialization() {
        for (note in listOf(null, "", "   ")) {
            val payload = JSONObject(body(note).toString())
            assertEquals(setOf("p_order_id", "p_shop_id", "p_items", "p_delivery_lat", "p_delivery_lng",
                "p_delivery_address", "p_expected_subtotal", "p_expected_delivery_fee", "p_note"), payload.keySet())
            assertTrue(payload.has("p_note"))
            assertTrue(payload.isNull("p_note"))
            assertEquals("request", payload.getString("p_order_id"))
            assertEquals(2, payload.getJSONArray("p_items").getJSONObject(0).getInt("qty"))
            assertEquals(100.0, payload.getDouble("p_expected_subtotal"), 0.0)
        }
    }

    @Test fun enteredNoteSurvivesSerializationAndTrimming() {
        assertEquals("ไม่เผ็ด", JSONObject(body("  ไม่เผ็ด  ").toString()).getString("p_note"))
    }
}
