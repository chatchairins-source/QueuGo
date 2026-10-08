package com.queuego.customer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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
                    available = true
                )
                val qty = r.optInt("quantity", 1).coerceIn(1, 99)
                if (product.id.isNotBlank() && product.shopId.isNotBlank()) add(CartLine(product, qty))
            }
        }
    }.getOrDefault(emptyList())

    fun save(lines: List<CartLine>) {
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
                    .put("quantity", line.quantity)
            )
        }
        prefs.edit().putString("cart", rows.toString()).apply()
    }
}
