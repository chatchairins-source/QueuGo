package com.queuego.customer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class MarketPricingTest {
    @Test fun productionDefaultShopAndDistanceBoundaries() {
        val pricing = MarketPricing.fromRows(JSONArray())
        assertEquals(listOf(0.0, 10.0, 15.0, 20.0), (1..4).map(pricing::multiShopFee))
        assertEquals(30.0, pricing.deliveryEstimate(5.0), 0.0)
        assertEquals(40.0, pricing.deliveryEstimate(5.01), 0.0)
        assertEquals(40.0, pricing.deliveryEstimate(6.0), 0.0)
        assertEquals(50.0, pricing.deliveryEstimate(6.01), 0.0)
    }
    @Test fun effectiveRuleAndCreatedAtTieBreakMatchProduction() {
        fun rule(value: Int, effective: String, created: String) = JSONObject().put("id", value.toString())
            .put("rule_key", "pricing.market_base_fee").put("value", value)
            .put("effective_from", effective).put("created_at", created)
        val rows = JSONArray().put(rule(99, "2026-11-01T00:00:00Z", "2026-10-10T00:00:00Z"))
            .put(rule(31, "2026-10-01T00:00:00Z", "2026-10-01T00:00:00Z"))
            .put(rule(35, "2026-10-01T00:00:00Z", "2026-10-02T00:00:00Z"))
            .put(rule(20, "2026-09-01T00:00:00Z", "2026-10-09T00:00:00Z"))
        assertEquals(35.0, MarketPricing.fromRows(rows, Instant.parse("2026-10-11T00:00:00Z")).base, 0.0)
    }
}
