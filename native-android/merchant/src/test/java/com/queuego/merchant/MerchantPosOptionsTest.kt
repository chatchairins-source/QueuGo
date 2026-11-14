package com.queuego.merchant

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MerchantPosOptionsTest {
    private fun groups() = parsePosProductOptionGroups(JSONArray().apply {
        put(JSONObject().apply {
            put("schema", "queuego.menu-options.v1")
            put("key", "portion")
            put("name", "ขนาด")
            put("type", "single")
            put("required", true)
            put("options", JSONArray()
                .put(JSONObject().put("key", "normal").put("name", "ธรรมดา").put("price_delta", 0))
                .put(JSONObject().put("key", "special").put("name", "พิเศษ").put("price_delta", 20)))
        })
        put(JSONObject().apply {
            put("schema", "queuego.menu-options.v1")
            put("key", "toppings")
            put("name", "ท็อปปิ้ง")
            put("type", "multi")
            put("required", false)
            put("min", 0)
            put("max", 2)
            put("options", JSONArray()
                .put(JSONObject().put("key", "egg").put("name", "ไข่ดาว").put("price_delta", 5))
                .put(JSONObject().put("key", "meat").put("name", "เพิ่มเนื้อ").put("price_delta", 10)))
        })
    })

    @Test fun structuredGroupsAndLegacyVariantsMatchRpcShape() {
        val structured = groups()
        assertEquals(listOf("portion", "toppings"), structured.map { it.key })
        val payload = posProductOptionPayload(structured,
            mapOf("portion" to listOf("special"), "toppings" to listOf("meat", "egg")))
        assertEquals("special", payload.getString("portion"))
        assertEquals("egg", payload.getJSONArray("toppings").getString(0))
        assertEquals("meat", payload.getJSONArray("toppings").getString(1))
        val legacy = parsePosProductOptionGroups(JSONArray().put("หวานน้อย").put("หวานปกติ"))
        assertEquals("variant", legacy.single().key)
        assertEquals("หวานปกติ", posProductOptionPayload(legacy,
            mapOf("variant" to listOf("หวานปกติ"))).getString("variant"))
    }

    @Test fun invalidRequiredLimitsAndUnknownValuesAreRejected() {
        val groups = groups()
        assertThrows(IllegalArgumentException::class.java) {
            posProductOptionPayload(groups, mapOf("portion" to emptyList(), "toppings" to emptyList()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            posProductOptionPayload(groups, mapOf("portion" to listOf("bad"), "toppings" to emptyList()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            posProductOptionPayload(groups, mapOf("portion" to listOf("normal"), "toppings" to listOf("egg", "meat", "egg")))
        }
    }

    @Test fun offlineLinesIncludeChosenPriceAndNormalizeEquivalentSelections() {
        val product = PosProduct("product", "อาหาร", 45.0, null, true, optionGroups = groups())
        val first = posOfflineDraftItem(product,
            "{\"portion\":\"special\",\"toppings\":[\"meat\",\"egg\"]}")
        val same = posOfflineDraftItem(product,
            "{\"toppings\":[\"egg\",\"meat\"],\"portion\":\"special\"}")
        assertEquals(45.0, first.basePriceSnapshot, 0.001)
        assertEquals(80.0, first.priceSnapshot, 0.001)
        assertEquals(first.optionsJson, same.optionsJson)
        assertTrue(first.optionLabel.contains("ขนาด: พิเศษ"))
        assertTrue(first.optionLabel.contains("ท็อปปิ้ง: ไข่ดาว"))
        assertFalse(posOfflineDraftLineChanged(first, same))
        val repriced = posOfflineDraftItem(product.copy(price = 50.0), first.optionsJson)
        assertTrue(posOfflineDraftLineChanged(first, repriced))
    }

    @Test fun offlineLinesRejectMissingRequiredAndRemovedOptionKeys() {
        val product = PosProduct("product", "อาหาร", 45.0, null, true, optionGroups = groups())
        assertThrows(IllegalArgumentException::class.java) { posOfflineDraftItem(product) }
        assertThrows(IllegalArgumentException::class.java) {
            posOfflineDraftItem(product, "{\"portion\":\"normal\",\"toppings\":[\"egg\",\"egg\"]}")
        }
        assertThrows(IllegalArgumentException::class.java) {
            posOfflineDraftItem(product.copy(optionGroups = emptyList()), "{\"portion\":\"normal\"}")
        }
    }

}
