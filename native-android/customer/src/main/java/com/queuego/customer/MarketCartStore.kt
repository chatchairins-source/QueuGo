package com.queuego.customer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class PendingMarketCheckout(
    val rpc: String,
    val body: JSONObject,
    val cartSnapshot: String
) {
    fun prepared(): PreparedMarketCheckout = PreparedMarketCheckout(rpc, JSONObject(body.toString()))
}

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

    private fun serialize(lines: List<MarketCartLine>): String {
        val rows = JSONArray()
        lines.forEach {
            rows.put(JSONObject().put("productId", it.product.id).put("quantity", it.quantity))
        }
        return rows.toString()
    }

    fun save(lines: List<MarketCartLine>) {
        prefs.edit().putString("cart", serialize(lines)).apply()
    }

    internal fun pending(prepared: PreparedMarketCheckout, lines: List<MarketCartLine>) =
        PendingMarketCheckout(prepared.rpc, JSONObject(prepared.body.toString()), serialize(lines))

    internal fun journal(): CheckoutJournal<PendingMarketCheckout> = object : CheckoutJournal<PendingMarketCheckout> {
        override fun read(): PendingMarketCheckout? {
            val raw = prefs.getString("pending_checkout", null) ?: return null
            val value = JSONObject(raw)
            val rpc = value.getString("rpc")
            require(rpc in setOf("queuego_place_market_order", "queuego_add_market_order_shops"))
            val body = value.getJSONObject("body")
            when (rpc) {
                "queuego_place_market_order" -> UUID.fromString(body.getString("p_market_order_id"))
                "queuego_add_market_order_shops" -> {
                    UUID.fromString(body.getString("p_request_id"))
                    UUID.fromString(body.getString("p_market_order_id"))
                }
            }
            val cartSnapshot = value.getString("cart")
            JSONArray(cartSnapshot)
            return PendingMarketCheckout(rpc, body, cartSnapshot)
        }

        override fun write(pending: PendingMarketCheckout) {
            val value = JSONObject()
                .put("rpc", pending.rpc)
                .put("body", pending.body)
                .put("cart", pending.cartSnapshot)
            check(prefs.edit().putString("pending_checkout", value.toString()).commit()) {
                "บันทึกคำขอ Market Trip ไม่สำเร็จ"
            }
        }

        override fun clear() {
            check(prefs.edit().remove("pending_checkout").commit())
        }

        override fun complete(pending: PendingMarketCheckout) {
            val edit = prefs.edit().remove("pending_checkout")
            if (prefs.getString("cart", "[]") == pending.cartSnapshot) {
                edit.putString("cart", "[]")
            }
            check(edit.commit())
        }
    }
}
