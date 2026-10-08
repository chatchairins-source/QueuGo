package com.queuego.customer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class MarketCartStore(context: Context, userId: String) {
    private val prefs = context.getSharedPreferences("queuego_market_cart_" + userId, Context.MODE_PRIVATE)

    data class Saved(val productId: String, val quantity: Int)

    fun load(): List<Saved> = runCatching {
        val rows = JSONArray(prefs.getString("cart", "[]") ?: "[]")
        buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val id = r.optString("productId")
                if (id.isNotBlank()) add(Saved(id, r.optInt("quantity", 1).coerceIn(1, 99)))
            }
        }
    }.getOrDefault(emptyList())

    fun save(lines: List<MarketCartLine>) {
        val rows = JSONArray()
        lines.forEach {
            rows.put(JSONObject().put("productId", it.product.id).put("quantity", it.quantity))
        }
        prefs.edit().putString("cart", rows.toString()).apply()
    }
}
