package com.queuego.merchant

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MerchantPosOptionsTest {
    private fun groups() = parsePosProductOptionGroups(JSONArray().put(
        JSONObject().put("schema", "queuego.menu-options.v1").put("key", "portion").put("name", "ขนาด")
            .put("type", "single").put("required", true).put("options", JSONArray()
                .put(JSONObject().put("key", "normal").put("name", "ธรรมดา").put("price_delta", 0))
                .put(JSONObject().put("key", "special").put("name", "พิเศษ").put("price_delta", 20)))
    ).put(JSONObject().put("schema", "queuego.menu-options.v1").put("key", "toppings").put("name", "ท็อปปิ้ง")
        .put("type", "multi").put("required", false).put("min", 0).put("max", 2).put("options", JSONArray()
            .put(JSONObject().put("key", "egg").put("name", "ไข่ดาว").put("price_delta", 5))
            .put(JSONObject().put("key", "meat").put("name", "เพิ่มเนื้อ").put("price_delta", 10))))

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
}
