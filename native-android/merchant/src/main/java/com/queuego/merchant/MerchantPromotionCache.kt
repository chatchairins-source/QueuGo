package com.queuego.merchant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal data class CachedMerchantPromotions(
    val rows: List<MerchantPromotion>,
    val fresh: Boolean
)

/** Durable local-first cache for read-heavy Merchant promotion metadata. */
internal class MerchantPromotionCache(context: Context) {
    companion object {
        private const val MAX_FILE_BYTES = 2L * 1024L * 1024L
        private const val FRESH_MS = 10L * 60L * 1000L
    }

    private val root = File(context.noBackupFilesDir, "merchant_promotion_cache_v1").also {
        runCatching { it.mkdirs() }
    }

    fun read(ownerId: String, shopId: String): CachedMerchantPromotions? {
        val file = fileFor(ownerId, shopId)
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES) return null
        val json = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .onFailure { runCatching { file.delete() } }
            .getOrNull() ?: return null
        val source = json.optJSONArray("rows") ?: JSONArray()
        val rows = buildList {
            for (index in 0 until source.length()) {
                val row = source.optJSONObject(index) ?: continue
                val id = row.optString("id")
                if (id.isBlank()) continue
                val budget = row.optDouble("maxBudget", 0.0)
                if (!budget.isFinite() || budget < 0.0) continue
                add(
                    MerchantPromotion(
                        id = id,
                        title = row.optString("title").ifBlank { "โปรโมชั่น" },
                        status = row.optString("status").ifBlank { "pending" },
                        maxBudget = budget,
                        periodDays = row.optInt("periodDays", 7).coerceAtLeast(1),
                        paymentStatus = row.optString("paymentStatus").ifBlank { "pending" },
                        createdAt = row.optString("createdAt").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
        val age = System.currentTimeMillis() - file.lastModified()
        return CachedMerchantPromotions(rows = rows, fresh = age in 0L..FRESH_MS)
    }

    fun write(ownerId: String, shopId: String, rows: List<MerchantPromotion>) {
        if (ownerId.isBlank() || shopId.isBlank()) return
        val json = JSONObject().put(
            "rows",
            JSONArray().also { array ->
                rows.forEach { promotion ->
                    array.put(
                        JSONObject()
                            .put("id", promotion.id)
                            .put("title", promotion.title)
                            .put("status", promotion.status)
                            .put("maxBudget", promotion.maxBudget)
                            .put("periodDays", promotion.periodDays)
                            .put("paymentStatus", promotion.paymentStatus)
                            .put("createdAt", promotion.createdAt ?: JSONObject.NULL)
                    )
                }
            }
        )
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) return
        val target = fileFor(ownerId, shopId)
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

    private fun fileFor(ownerId: String, shopId: String): File {
        val key = "$ownerId:$shopId"
        val name = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(root, "$name.json")
    }
}
