package com.queuetech.queuego.customer

import android.content.Context
import com.queuetech.queuego.core.model.QueueGoCartItem
import com.queuetech.queuego.core.model.QueueGoCustomerCart
import com.queuetech.queuego.core.model.QueueGoProduct
import org.json.JSONArray
import org.json.JSONObject

class CustomerCartStore(context: Context, userId: String) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "queuego_customer_cart_v1_$userId",
        Context.MODE_PRIVATE,
    )

    fun read(): QueueGoCustomerCart = runCatching {
        val raw = prefs.getString(KEY_CART, null) ?: return QueueGoCustomerCart()
        val root = JSONObject(raw)
        val itemsJson = root.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (index in 0 until itemsJson.length()) {
                val item = itemsJson.getJSONObject(index)
                val quantity = item.optInt("quantity", 0)
                val price = item.optDouble("price", Double.NaN)
                if (quantity !in 1..99 || !price.isFinite() || price < 0) continue
                val productId = item.optString("productId")
                val shopId = item.optString("shopId")
                if (productId.isBlank() || shopId.isBlank()) continue
                add(
                    QueueGoCartItem(
                        productId = productId,
                        shopId = shopId,
                        name = item.optString("name").ifBlank { "สินค้า" },
                        price = price,
                        imageUrl = item.optString("imageUrl"),
                        quantity = quantity,
                    )
                )
            }
        }
        val shopId = root.optString("shopId").takeIf(String::isNotBlank)
        if (items.isEmpty() || shopId == null) QueueGoCustomerCart()
        else QueueGoCustomerCart(shopId = shopId, items = items.filter { it.shopId == shopId })
    }.getOrElse {
        clear()
        QueueGoCustomerCart()
    }

    fun add(current: QueueGoCustomerCart, product: QueueGoProduct): QueueGoCustomerCart {
        require(product.available && product.deliveryAvailable) { "สินค้านี้ยังสั่งไม่ได้" }
        require(current.shopId == null || current.shopId == product.shopId) {
            "ตะกร้ามีสินค้าจากร้านอื่น กรุณาล้างตะกร้าก่อน"
        }
        val existing = current.items.firstOrNull { it.productId == product.id }
        val items = if (existing == null) {
            current.items + QueueGoCartItem(
                productId = product.id,
                shopId = product.shopId,
                name = product.name,
                price = product.effectivePrice,
                imageUrl = product.imageUrl,
                quantity = 1,
            )
        } else {
            current.items.map {
                if (it.productId == product.id) it.copy(
                    quantity = (it.quantity + 1).coerceAtMost(99),
                    price = product.effectivePrice,
                ) else it
            }
        }
        return QueueGoCustomerCart(shopId = product.shopId, items = items).also(::save)
    }

    fun changeQuantity(
        current: QueueGoCustomerCart,
        productId: String,
        delta: Int,
    ): QueueGoCustomerCart {
        val updated = current.items.mapNotNull { item ->
            if (item.productId != productId) item
            else (item.quantity + delta).let { next ->
                if (next <= 0) null else item.copy(quantity = next.coerceAtMost(99))
            }
        }
        val cart = if (updated.isEmpty()) QueueGoCustomerCart()
        else current.copy(items = updated)
        save(cart)
        return cart
    }

    fun clear(): QueueGoCustomerCart {
        prefs.edit().remove(KEY_CART).apply()
        return QueueGoCustomerCart()
    }

    private fun save(cart: QueueGoCustomerCart) {
        if (cart.items.isEmpty() || cart.shopId.isNullOrBlank()) {
            prefs.edit().remove(KEY_CART).apply()
            return
        }
        val items = JSONArray()
        cart.items.forEach { item ->
            items.put(
                JSONObject()
                    .put("productId", item.productId)
                    .put("shopId", item.shopId)
                    .put("name", item.name)
                    .put("price", item.price)
                    .put("imageUrl", item.imageUrl)
                    .put("quantity", item.quantity)
            )
        }
        prefs.edit().putString(
            KEY_CART,
            JSONObject().put("shopId", cart.shopId).put("items", items).toString(),
        ).apply()
    }

    private companion object {
        const val KEY_CART = "cart"
    }
}
