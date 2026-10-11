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

    private fun serialize(lines: List<MarketCartLine>): String {
        val rows = JSONArray()
        lines.forEach {
            rows.put(JSONObject().put("productId", it.product.id).put("quantity", it.quantity))
        }
        return rows.toString()
    }
    fun save(lines: List<MarketCartLine>) { prefs.edit().putString("cart", serialize(lines)).apply() }

    internal fun hasPendingCheckout(): Boolean = prefs.contains("pending_checkout")
    internal fun pending(rpc: String, body: JSONObject, lines: List<MarketCartLine>) =
        PendingMarketCheckout(rpc, JSONObject(body.toString()), serialize(lines))

    internal fun journal(): CheckoutJournal<PendingMarketCheckout> = object : CheckoutJournal<PendingMarketCheckout> {
        override fun read(): PendingMarketCheckout? = prefs.getString("pending_checkout", null)?.let {
            PendingMarketCheckout.decode(it)
        }
        override fun write(pending: PendingMarketCheckout) {
            check(prefs.edit().putString("pending_checkout", pending.encode()).putString("cart", pending.cartSnapshot).commit()) { "บันทึกคำขอตลาดสดไม่สำเร็จ" }
        }
        override fun clear() { check(prefs.edit().remove("pending_checkout").commit()) }
        override fun complete(pending: PendingMarketCheckout) {
            val edit = prefs.edit().remove("pending_checkout")
            if (prefs.getString("cart", "[]") == pending.cartSnapshot) edit.putString("cart", "[]")
            check(edit.commit())
        }
    }
}
