package com.queuego.shared

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal data class QueueGoCachedPlaceSearch(
    val results: List<QueueGoPlaceSearchResult>,
    val fresh: Boolean
)

/**
 * Durable local-first cache for QueueGo Places text search.
 *
 * Place metadata is safe to show stale while it is refreshed. Order, fare, inventory,
 * finance and dispatch data must never be stored here.
 */
internal class QueueGoPlaceSearchCache(context: Context) {
    companion object {
        private const val MAX_FILE_BYTES = 256L * 1024L
        private const val MAX_TOTAL_BYTES = 8L * 1024L * 1024L
        private const val FRESH_MS = 7L * 24L * 60L * 60L * 1000L
    }

    private val root = File(context.noBackupFilesDir, "queuego_place_search_cache_v1").also {
        runCatching { it.mkdirs() }
    }

    fun read(query: String): QueueGoCachedPlaceSearch? {
        val normalized = normalize(query)
        if (normalized.length < 2) return null
        val file = File(root, sha256(normalized) + ".json")
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES) return null
        val json = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .onFailure { runCatching { file.delete() } }
            .getOrNull() ?: return null
        val rows = json.optJSONArray("results") ?: return null
        val results = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val id = row.optString("id")
                val name = row.optString("name")
                val latitude = row.optDouble("latitude", Double.NaN)
                val longitude = row.optDouble("longitude", Double.NaN)
                if (
                    id.isBlank() || name.isBlank() ||
                    !latitude.isFinite() || !longitude.isFinite() ||
                    latitude !in 5.0..21.0 || longitude !in 97.0..106.0
                ) continue
                add(
                    QueueGoPlaceSearchResult(
                        id = id,
                        name = name,
                        address = row.optNullable("address"),
                        latitude = latitude,
                        longitude = longitude,
                        category = row.optNullable("category"),
                        subcategory = row.optNullable("subcategory"),
                        province = row.optNullable("province")
                    )
                )
            }
        }
        if (results.isEmpty()) return null
        val fetchedAt = json.optLong("fetchedAt", 0L)
        val age = System.currentTimeMillis() - fetchedAt
        runCatching { file.setLastModified(System.currentTimeMillis()) }
        return QueueGoCachedPlaceSearch(
            results = results,
            fresh = fetchedAt > 0L && age in 0L..FRESH_MS
        )
    }

    fun write(query: String, results: List<QueueGoPlaceSearchResult>) {
        val normalized = normalize(query)
        if (normalized.length < 2 || results.isEmpty()) return
        val json = JSONObject()
            .put("fetchedAt", System.currentTimeMillis())
            .put(
                "results",
                JSONArray().also { array ->
                    results.take(12).forEach { result ->
                        array.put(
                            JSONObject()
                                .put("id", result.id)
                                .put("name", result.name)
                                .putNullable("address", result.address)
                                .put("latitude", result.latitude)
                                .put("longitude", result.longitude)
                                .putNullable("category", result.category)
                                .putNullable("subcategory", result.subcategory)
                                .putNullable("province", result.province)
                        )
                    }
                }
            )
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) return
        val target = File(root, sha256(normalized) + ".json")
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

    private fun normalize(query: String): String =
        query.trim().lowercase().replace(Regex("\\s+"), " ")
}

private fun JSONObject.optNullable(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.putNullable(key: String, value: String?): JSONObject =
    put(key, value ?: JSONObject.NULL)

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
