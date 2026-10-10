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
                "p_delivery_address", "p_expected_subtotal", "p_expected_delivery_fee", "p_note"), payload.keys().asSequence().toSet())
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
    @Test fun optionSelectionsAreSerializedAndIncludedInExpectedSubtotal() {
        val variants = """[
          {"key":"portion","name":"ขนาด","type":"single","required":true,
           "options":[{"key":"normal","name":"ธรรมดา","price_delta":0},{"key":"special","name":"พิเศษ","price_delta":15}]},
          {"key":"toppings","name":"ท็อปปิ้ง","type":"multi","required":false,
           "options":[{"key":"egg","name":"ใส่ไข่","price_delta":10}]}
        ]"""
        val configured = product.copy(variantsJson = variants)
        val selections = listOf(
            CustomerMenuSelection("portion", "special"),
            CustomerMenuSelection("toppings", "egg")
        )
        val payload = api.checkoutBody(
            auth,
            "request-options",
            shop,
            listOf(CartLine(configured, 2, selections)),
            CustomerLocation(15.0, 103.0, "address"),
            null
        )
        assertEquals(150.0, payload.getDouble("p_expected_subtotal"), 0.0)
        val item = payload.getJSONArray("p_items").getJSONObject(0)
        assertEquals(2, item.getJSONArray("options").length())
        assertEquals("special", item.getJSONArray("options").getJSONObject(0).getString("option_key"))
        assertEquals("egg", item.getJSONArray("options").getJSONObject(1).getString("option_key"))
    }

}
