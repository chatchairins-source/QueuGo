package com.queuego.customer

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.ceil

internal data class MarketPricing(val base: Double, val distanceStep: Double, val secondShop: Double, val additionalShop: Double) {
    fun multiShopFee(shops: Int): Double = when {
        shops <= 1 -> 0.0
        else -> secondShop + (shops - 2).coerceAtLeast(0) * additionalShop
    }
    fun deliveryEstimate(distanceKm: Double): Double {
        require(distanceKm.isFinite() && distanceKm >= 0)
        return base + ceil((distanceKm - 5).coerceAtLeast(0.0)) * distanceStep
    }
    companion object {
        fun fromRows(rows: JSONArray, now: Instant = Instant.now()): MarketPricing {
            fun timestamp(row: JSONObject, key: String) = runCatching {
                OffsetDateTime.parse(row.getString(key)).toInstant()
            }.getOrNull()
            val current = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
                .filter { timestamp(it, "effective_from")?.let { at -> at <= now } == true }
                .sortedWith(compareByDescending<JSONObject> { timestamp(it, "effective_from") }
                    .thenByDescending { timestamp(it, "created_at") }.thenByDescending { it.optString("id") })
            fun value(key: String, fallback: Double): Double = current.firstOrNull { it.optString("rule_key") == key }
                ?.opt("value")?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: fallback
            // Missing-rule defaults match current Production queuego_rule_numeric calls.
            return MarketPricing(value("pricing.market_base_fee", 30.0), value("pricing.market_distance_step_fee", 10.0),
                value("pricing.market_second_shop_fee", 10.0), value("pricing.market_additional_shop_fee", 5.0))
        }
    }
}
