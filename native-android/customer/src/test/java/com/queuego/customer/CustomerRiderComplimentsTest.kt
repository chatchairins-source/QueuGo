package com.queuego.customer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerRiderComplimentsTest {
    @Test fun parserKeepsOnlyKnownUniqueCompliments() {
        val parsed = customerRiderComplimentsFromRpc(
            JSONObject().put(
                "compliments",
                JSONArray()
                    .put("polite")
                    .put("fast")
                    .put("polite")
                    .put("unknown")
            )
        )
        assertEquals(setOf("polite", "fast"), parsed)
        assertEquals("ดูแลสินค้า", customerRiderComplimentLabel("careful"))
    }

    @Test fun emptyBackendDoesNotInventCompliments() {
        assertEquals(emptySet<String>(), customerRiderComplimentsFromRpc(JSONObject()))
    }
}
