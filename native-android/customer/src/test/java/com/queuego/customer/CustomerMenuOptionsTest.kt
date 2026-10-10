package com.queuego.customer

import org.junit.Assert.*
import org.junit.Test

class CustomerMenuOptionsTest {
    private val variants = """
        [
          {
            "schema":"queuego.menu-options.v1",
            "key":"portion",
            "name":"ขนาด / ความพิเศษ",
            "type":"single",
            "required":true,
            "options":[
              {"key":"normal","name":"ธรรมดา","price_delta":0},
              {"key":"special","name":"พิเศษ","price_delta":15}
            ]
          },
          {
            "schema":"queuego.menu-options.v1",
            "key":"toppings",
            "name":"ท็อปปิ้ง",
            "type":"multi",
            "required":false,
            "options":[
              {"key":"egg","name":"ใส่ไข่","price_delta":10},
              {"key":"meat","name":"เพิ่มเนื้อ","price_delta":20}
            ]
          }
        ]
    """.trimIndent()

    private fun product() = CustomerProduct(
        id = "p1",
        shopId = "s1",
        name = "ข้าวผัด",
        description = null,
        price = 50.0,
        deliveryPrice = 55.0,
        image = null,
        available = true,
        variantsJson = variants
    )

    @Test fun missingRequiredPortionDefaultsToNormalForBackwardCompatibility() {
        val resolved = customerResolveMenuSelections(product(), emptyList())
        assertEquals(listOf("ธรรมดา"), resolved.map { it.optionName })
        assertEquals(55.0, customerCartLineUnitPrice(CartLine(product(), 1)), 0.0)
    }

    @Test fun specialAndToppingAddAuthoritativeClientQuoteAndStablePayload() {
        val selections = listOf(
            CustomerMenuSelection("portion", "special"),
            CustomerMenuSelection("toppings", "egg")
        )
        val line = CartLine(product(), 2, selections)
        assertEquals(80.0, customerCartLineUnitPrice(line), 0.0)
        assertEquals("พิเศษ · ใส่ไข่", customerCartLineOptionsLabel(line))
        val payload = customerMenuSelectionPayload(customerCanonicalMenuSelections(product(), selections))
        assertEquals(2, payload.length())
        assertEquals("portion", payload.getJSONObject(0).getString("group_key"))
        assertEquals("special", payload.getJSONObject(0).getString("option_key"))
        assertEquals("toppings", payload.getJSONObject(1).getString("group_key"))
        assertEquals("egg", payload.getJSONObject(1).getString("option_key"))
    }

    @Test fun differentOptionsKeepSameProductAsSeparateCartLines() {
        val normal = CartLine(product(), 1, listOf(CustomerMenuSelection("portion", "normal")))
        val special = CartLine(product(), 1, listOf(CustomerMenuSelection("portion", "special")))
        assertNotEquals(customerCartLineKey(normal), customerCartLineKey(special))
    }

    @Test fun unknownDuplicateAndMultipleSingleSelectionsFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            customerResolveMenuSelections(product(), listOf(CustomerMenuSelection("unknown", "x")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            customerResolveMenuSelections(
                product(),
                listOf(
                    CustomerMenuSelection("toppings", "egg"),
                    CustomerMenuSelection("toppings", "egg")
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            customerResolveMenuSelections(
                product(),
                listOf(
                    CustomerMenuSelection("portion", "normal"),
                    CustomerMenuSelection("portion", "special")
                )
            )
        }
    }

    @Test fun savedServerSnapshotRendersOnlyOptionNames() {
        val raw = """[
          {"group_key":"portion","option_key":"special","option_name":"พิเศษ","price_delta":15},
          {"group_key":"toppings","option_key":"egg","option_name":"ใส่ไข่","price_delta":10}
        ]"""
        assertEquals("พิเศษ · ใส่ไข่", customerSelectedOptionsLabel(raw))
    }
}
