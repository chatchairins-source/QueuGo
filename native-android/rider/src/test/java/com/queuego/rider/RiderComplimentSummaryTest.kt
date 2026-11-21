package com.queuego.rider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RiderComplimentSummaryTest {
    @Test fun summaryUsesOnlyServerAggregateAndKnownKeys() {
        val parsed = riderComplimentSummaryFromRpc(
            JSONObject()
                .put("total", 9)
                .put(
                    "items",
                    JSONArray()
                        .put(JSONObject().put("key", "polite").put("count", 7))
                        .put(JSONObject().put("key", "fast").put("count", 4))
                        .put(JSONObject().put("key", "unknown").put("count", 99))
                )
        )
        assertEquals(9, parsed.total)
        assertEquals(listOf("polite", "fast"), parsed.items.map { it.key })
        assertEquals(listOf(7, 4), parsed.items.map { it.count })
        assertEquals("สุภาพ", riderComplimentLabel("polite"))
    }

    @Test fun emptySummaryDoesNotInventBadges() {
        val parsed = riderComplimentSummaryFromRpc(JSONObject())
        assertEquals(0, parsed.total)
        assertEquals(emptyList<RiderComplimentCount>(), parsed.items)
    }
}
