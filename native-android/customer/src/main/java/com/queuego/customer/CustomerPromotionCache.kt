package com.queuego.customer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class CachedCustomerPromotions(
    val rows: List<CustomerPromotion>,
    val fresh: Boolean
)

/** Durable local-first cache for public Customer promotions. */
internal class CustomerPromotionCache(context: Context) {
    companion object {
        private const val MAX_FILE_BYTES = 1024L * 1024L
        private const val FRESH_MS = 10L * 60L * 1000L
    }

    private val root = File(context.noBackupFilesDir, "customer_promotion_cache_v1").also {
        runCatching { it.mkdirs() }
    }
    private val file = File(root, "promotions.json")

    fun read(): CachedCustomerPromotions? {
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES) return null
        val json = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .onFailure { runCatching { file.delete() } }
            .getOrNull() ?: return null
        val source = json.optJSONArray("rows") ?: JSONArray()
        val rows = buildList {
            for (index in 0 until source.length()) {
                val row = source.optJSONObject(index) ?: continue
                val shopId = row.optString("shopId")
                if (shopId.isBlank()) continue
                add(
                    CustomerPromotion(
                        shopId = shopId,
                        shopName = row.optString("shopName").ifBlank { "ร้านค้า" },
                        title = row.optString("title").ifBlank { "โปรโมชั่น" },
                        description = row.optString("description")
                    )
                )
            }
        }
        val age = System.currentTimeMillis() - file.lastModified()
        return CachedCustomerPromotions(rows = rows, fresh = age in 0L..FRESH_MS)
    }

    fun write(rows: List<CustomerPromotion>) {
        val json = JSONObject().put(
            "rows",
            JSONArray().also { array ->
                rows.forEach { promotion ->
                    array.put(
                        JSONObject()
                            .put("shopId", promotion.shopId)
                            .put("shopName", promotion.shopName)
                            .put("title", promotion.title)
                            .put("description", promotion.description)
                    )
                }
            }
        )
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) return
        val temp = File(root, "promotions.tmp")
        runCatching {
            temp.outputStream().use { it.write(bytes) }
            if (!temp.renameTo(file)) {
                file.outputStream().use { it.write(bytes) }
                temp.delete()
            }
            file.setLastModified(System.currentTimeMillis())
        }.onFailure { runCatching { temp.delete() } }
    }
}
