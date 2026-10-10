package com.queuego.customer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class PendingCustomerCheckout(val body: JSONObject, val cartSnapshot: String)

class CustomerCartStore(context: Context, userId: String) {
    private val prefs = context.getSharedPreferences("queuego_customer_cart_" + userId, Context.MODE_PRIVATE)

    fun load(): List<CartLine> = runCatching {
        val rows = JSONArray(prefs.getString("cart", "[]") ?: "[]")
        buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val product = CustomerProduct(
                    id = r.optString("id"),
                    shopId = r.optString("shopId"),
                    name = r.optString("name").ifBlank { "สินค้า" },
                    description = r.optString("description").takeIf { it.isNotBlank() && it != "null" },
                    price = r.optDouble("price", 0.0),
                    deliveryPrice = r.optDouble("deliveryPrice", r.optDouble("price", 0.0)),
                    image = r.optString("image").takeIf { it.isNotBlank() && it != "null" },
                    available = true,
                    variantsJson = customerVariantsJson(r.opt("variants"))
                )
                val qty = r.optInt("quantity", 1).coerceIn(1, 99)
                val rawSelections = buildList {
                    val selected = r.optJSONArray("selections") ?: JSONArray()
                    for (j in 0 until selected.length()) {
                        val item = selected.optJSONObject(j) ?: continue
                        val groupKey = item.optString("group_key").trim()
                        val optionKey = item.optString("option_key").trim()
                        if (groupKey.isNotBlank() && optionKey.isNotBlank()) {
                            add(CustomerMenuSelection(groupKey, optionKey))
                        }
                    }
                }
                val selections = runCatching {
                    customerCanonicalMenuSelections(product, rawSelections)
                }.getOrDefault(emptyList())
                if (product.id.isNotBlank() && product.shopId.isNotBlank()) {
                    add(CartLine(product, qty, selections))
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun serialize(lines: List<CartLine>): String {
        val rows = JSONArray()
        lines.forEach { line ->
            rows.put(
                JSONObject()
                    .put("id", line.product.id)
                    .put("shopId", line.product.shopId)
                    .put("name", line.product.name)
                    .put("description", line.product.description)
                    .put("price", line.product.price)
                    .put("deliveryPrice", line.product.deliveryPrice)
                    .put("image", line.product.image)
                    .put("variants", runCatching { JSONArray(line.product.variantsJson) }.getOrDefault(JSONArray()))
                    .put("selections", customerMenuSelectionPayload(customerCanonicalMenuSelections(line.product, line.selections)))
                    .put("quantity", line.quantity)
            )
        }
        return rows.toString()
    }

    fun save(lines: List<CartLine>) { prefs.edit().putString("cart", serialize(lines)).apply() }

    internal fun pending(body: JSONObject, lines: List<CartLine>) = PendingCustomerCheckout(body, serialize(lines))

    internal fun journal(): CheckoutJournal<PendingCustomerCheckout> = object : CheckoutJournal<PendingCustomerCheckout> {
        override fun read(): PendingCustomerCheckout? {
            val raw = prefs.getString("pending_checkout", null) ?: return null
            val value = JSONObject(raw)
            val body = value.getJSONObject("body")
            java.util.UUID.fromString(body.getString("p_order_id"))
            java.util.UUID.fromString(body.getString("p_shop_id"))
            return PendingCustomerCheckout(body, value.getString("cart"))
        }
        override fun write(pending: PendingCustomerCheckout) {
            check(prefs.edit().putString("pending_checkout", JSONObject().put("body", pending.body).put("cart", pending.cartSnapshot).toString()).commit()) { "บันทึกคำขอสั่งซื้อไม่สำเร็จ" }
        }
        override fun clear() { check(prefs.edit().remove("pending_checkout").commit()) }
        override fun complete(pending: PendingCustomerCheckout) {
            val edit = prefs.edit().remove("pending_checkout")
            if (prefs.getString("cart", "[]") == pending.cartSnapshot) edit.putString("cart", "[]")
            check(edit.commit())
        }
    }
}
