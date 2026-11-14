package com.queuego.customer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val MARKET_CART_MAX_JSON_CHARS = 512 * 1024
private const val MARKET_PENDING_CHECKOUT_MAX_JSON_CHARS = 2 * 1024 * 1024

class MarketCartStore(context: Context, userId: String) {
    private val prefs = context.getSharedPreferences("queuego_market_cart_" + userId, Context.MODE_PRIVATE)

    private fun cartRaw(): String {
        val raw = prefs.getString("cart", "[]") ?: "[]"
        if (raw.length <= MARKET_CART_MAX_JSON_CHARS) return raw
        prefs.edit()
            .remove("cart")
            .remove("cart_revision")
            .remove("merged_guest_revision")
            .apply()
        return "[]"
    }

    private fun pendingRaw(): String? {
        val raw = prefs.getString("pending_checkout", null) ?: return null
        if (raw.length <= MARKET_PENDING_CHECKOUT_MAX_JSON_CHARS) return raw
        prefs.edit().remove("pending_checkout").apply()
        return null
    }

    data class Saved(val productId: String, val quantity: Int)

    fun load(): List<Saved> = runCatching {
        val rows = JSONArray(cartRaw())
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
        prefs.edit().putString("cart", serialize(lines)).putString("cart_revision", UUID.randomUUID().toString()).apply()
    }
    internal fun revision(): String {
        val raw = cartRaw()
        return prefs.getString("cart_revision", null)
            ?: UUID.nameUUIDFromBytes(raw.toByteArray(Charsets.UTF_8)).toString()
    }
    internal fun revisionForMerge(): String {
        val current = revision()
        check(prefs.edit().putString("cart", cartRaw())
            .putString("cart_revision", current).commit()) { "บันทึกตะกร้าก่อนเข้าสู่ระบบไม่สำเร็จ" }
        return current
    }
    internal fun hasMergedGuest(revision: String) = prefs.getString("merged_guest_revision", null) == revision
    internal fun persistGuestMerge(lines: List<MarketCartLine>, guestRevision: String) {
        check(prefs.edit().putString("cart", serialize(lines)).putString("merged_guest_revision", guestRevision)
            .putString("cart_revision", UUID.randomUUID().toString()).commit()) { "บันทึกตะกร้าหลังเข้าสู่ระบบไม่สำเร็จ" }
    }
    internal fun clearMergedGuest() {
        check(prefs.edit().putString("cart", "[]").putString("cart_revision", UUID.randomUUID().toString()).commit())
    }

    internal fun hasPendingCheckout(): Boolean = pendingRaw() != null
    internal fun pending(rpc: String, body: JSONObject, lines: List<MarketCartLine>) =
        PendingMarketCheckout(rpc, JSONObject(body.toString()), serialize(lines))

    internal fun journal(): CheckoutJournal<PendingMarketCheckout> = object : CheckoutJournal<PendingMarketCheckout> {
        override fun read(): PendingMarketCheckout? = pendingRaw()?.let {
            PendingMarketCheckout.decode(it)
        }
        override fun write(pending: PendingMarketCheckout) {
            check(prefs.edit().putString("pending_checkout", pending.encode()).putString("cart", pending.cartSnapshot).commit()) { "บันทึกคำขอตลาดสดไม่สำเร็จ" }
        }
        override fun clear() { check(prefs.edit().remove("pending_checkout").commit()) }
        override fun complete(pending: PendingMarketCheckout) {
            val edit = prefs.edit().remove("pending_checkout")
            if (cartRaw() == pending.cartSnapshot) edit.putString("cart", "[]")
            check(edit.commit())
        }
    }
}
