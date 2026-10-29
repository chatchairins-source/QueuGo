package com.queuego.customer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal data class CachedCustomerFavorites(
    val favorites: List<ShopFavorite>,
    val products: List<CustomerProduct>,
    val fresh: Boolean
)

/**
 * Short-lived durable snapshot for the Customer Favorites page.
 *
 * Favorite mutations always invalidate this snapshot. Shop metadata continues to use
 * the existing CustomerContentCache home snapshot so we do not duplicate the full
 * shop list in another cache file.
 */
internal class CustomerFavoritesCache(context: Context) {
    companion object {
        private const val FRESH_MS = 60L * 1000L
        private const val MAX_FILE_BYTES = 4L * 1024L * 1024L
        private const val MAX_FAVORITES = 500
        private const val MAX_PRODUCTS = 500
    }

    private val root = File(context.noBackupFilesDir, "customer_favorites_cache_v1").also {
        runCatching { it.mkdirs() }
    }

    fun read(userId: String): CachedCustomerFavorites? {
        if (userId.isBlank()) return null
        val file = fileFor(userId)
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES) return null
        val json = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .onFailure { runCatching { file.delete() } }
            .getOrNull() ?: return null
        if (json.optString("userId") != userId) return null

        return runCatching {
            val favoriteRows = json.optJSONArray("favorites") ?: JSONArray()
            val favorites = buildList {
                for (index in 0 until minOf(favoriteRows.length(), MAX_FAVORITES)) {
                    val row = favoriteRows.optJSONObject(index) ?: continue
                    val id = row.optString("id")
                    val shop = row.optString("shop")
                    val product = row.optString("product")
                    if (id.isBlank() || (shop.isBlank() && product.isBlank())) continue
                    add(ShopFavorite(id = id, shop = shop, product = product))
                }
            }
            val productRows = json.optJSONArray("products") ?: JSONArray()
            val products = buildList {
                for (index in 0 until minOf(productRows.length(), MAX_PRODUCTS)) {
                    val row = productRows.optJSONObject(index) ?: continue
                    val id = row.optString("id")
                    val shopId = row.optString("shopId")
                    if (id.isBlank() || shopId.isBlank()) continue
                    add(
                        CustomerProduct(
                            id = id,
                            shopId = shopId,
                            name = row.optString("name").ifBlank { "สินค้า" },
                            description = row.optString("description").takeIf { row.has("description") && !row.isNull("description") },
                            price = row.optDouble("price", 0.0),
                            deliveryPrice = row.optDouble("deliveryPrice", row.optDouble("price", 0.0)),
                            image = row.optString("image").takeIf { row.has("image") && !row.isNull("image") },
                            available = row.optBoolean("available", true)
                        )
                    )
                }
            }
            val age = System.currentTimeMillis() - file.lastModified()
            CachedCustomerFavorites(
                favorites = favorites,
                products = products,
                fresh = age in 0L..FRESH_MS
            )
        }.getOrNull()
    }

    fun write(userId: String, favorites: List<ShopFavorite>, products: List<CustomerProduct>) {
        if (userId.isBlank() || favorites.size > MAX_FAVORITES || products.size > MAX_PRODUCTS) return
        val json = JSONObject()
            .put("userId", userId)
            .put(
                "favorites",
                JSONArray().also { rows ->
                    favorites.forEach { favorite ->
                        rows.put(
                            JSONObject()
                                .put("id", favorite.id)
                                .put("shop", favorite.shop)
                                .put("product", favorite.product)
                        )
                    }
                }
            )
            .put(
                "products",
                JSONArray().also { rows ->
                    products.forEach { product ->
                        rows.put(
                            JSONObject()
                                .put("id", product.id)
                                .put("shopId", product.shopId)
                                .put("name", product.name)
                                .put("description", product.description ?: JSONObject.NULL)
                                .put("price", product.price)
                                .put("deliveryPrice", product.deliveryPrice)
                                .put("image", product.image ?: JSONObject.NULL)
                                .put("available", product.available)
                        )
                    }
                }
            )
        writeJson(fileFor(userId), json)
    }

    fun invalidate(userId: String) {
        if (userId.isBlank()) return
        runCatching { fileFor(userId).delete() }
    }

    private fun fileFor(userId: String): File =
        File(root, sha256(userId) + ".json")

    private fun writeJson(target: File, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) return
        val temp = File(root, target.name + ".tmp")
        runCatching {
            temp.outputStream().use { it.write(bytes) }
            if (!temp.renameTo(target)) {
                target.outputStream().use { it.write(bytes) }
                temp.delete()
            }
            target.setLastModified(System.currentTimeMillis())
        }.onFailure { runCatching { temp.delete() } }
    }
}

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
