package com.queuego.customer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal data class CachedShopReviews(
    val value: ShopReviews,
    val fresh: Boolean
)

/**
 * Durable public shop-review cache.
 *
 * Reviews are safe to render stale for a short period while checkout/order authority
 * remains entirely server-side. This prevents re-downloading the same public review
 * payload every time a customer re-opens a shop.
 */
internal class CustomerShopReviewCache(context: Context) {
    companion object {
        private const val FRESH_MS = 10L * 60L * 1000L
        private const val MAX_FILE_BYTES = 2L * 1024L * 1024L
        private const val MAX_TOTAL_BYTES = 24L * 1024L * 1024L
        private const val MAX_ITEMS = 200
    }

    private val root = File(context.noBackupFilesDir, "customer_shop_review_cache_v1").also {
        runCatching { it.mkdirs() }
    }

    fun read(shopId: String): CachedShopReviews? {
        if (shopId.isBlank()) return null
        val file = fileFor(shopId)
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES) return null
        val json = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .onFailure { runCatching { file.delete() } }
            .getOrNull() ?: return null
        if (json.optString("shopId") != shopId) return null

        return runCatching {
            val rows = json.optJSONArray("items") ?: JSONArray()
            val items = buildList {
                for (index in 0 until minOf(rows.length(), MAX_ITEMS)) {
                    val row = rows.optJSONObject(index) ?: continue
                    val rating = row.optInt("rating", 0)
                    if (rating !in 1..5) continue
                    add(
                        ShopReview(
                            name = row.optString("name").ifBlank { "ลูกค้า" },
                            rating = rating,
                            comment = row.optString("comment"),
                            date = row.optString("date")
                        )
                    )
                }
            }
            val value = ShopReviews(
                count = json.optInt("count", items.size).coerceAtLeast(items.size),
                average = json.optDouble("average", 0.0).takeIf { it.isFinite() } ?: 0.0,
                items = items
            )
            val age = System.currentTimeMillis() - file.lastModified()
            CachedShopReviews(value = value, fresh = age in 0L..FRESH_MS)
        }.getOrNull()
    }

    fun write(shopId: String, value: ShopReviews) {
        if (shopId.isBlank()) return
        val items = value.items.take(MAX_ITEMS)
        val json = JSONObject()
            .put("shopId", shopId)
            .put("count", value.count.coerceAtLeast(items.size))
            .put("average", value.average)
            .put(
                "items",
                JSONArray().also { array ->
                    items.forEach { review ->
                        array.put(
                            JSONObject()
                                .put("name", review.name)
                                .put("rating", review.rating)
                                .put("comment", review.comment)
                                .put("date", review.date)
                        )
                    }
                }
            )
        writeJson(fileFor(shopId), json)
    }

    private fun fileFor(shopId: String): File =
        File(root, sha256(shopId) + ".json")

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
            prune()
        }.onFailure { runCatching { temp.delete() } }
    }

    private fun prune() {
        val files = root.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        var total = files.sumOf { it.length().coerceAtLeast(0L) }
        if (total <= MAX_TOTAL_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_TOTAL_BYTES * 3L / 4L) break
            val size = file.length().coerceAtLeast(0L)
            if (runCatching { file.delete() }.getOrDefault(false)) total -= size
        }
    }
}

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
