package com.queuego.merchant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Durable local-first display cache for Merchant media rows. Storage remains authoritative. */
internal class MerchantMediaCache(context: Context) {
    companion object {
        private const val MAX_FILE_BYTES = 2L * 1024L * 1024L
    }

    private val root = File(context.noBackupFilesDir, "merchant_media_cache_v1").also {
        runCatching { it.mkdirs() }
    }

    fun read(ownerKey: String): List<MerchantMediaItem> {
        if (ownerKey.isBlank()) return emptyList()
        val file = fileFor(ownerKey)
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES) return emptyList()
        val json = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .onFailure { runCatching { file.delete() } }
            .getOrNull() ?: return emptyList()
        val rows = json.optJSONArray("rows") ?: JSONArray()
        val result = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val name = row.optString("name")
                val url = row.optString("url")
                if (name.isBlank() || !url.startsWith("https://")) continue
                add(
                    MerchantMediaItem(
                        name = name,
                        url = url,
                        kind = row.optString("kind").ifBlank { "image" }
                    )
                )
            }
        }
        runCatching { file.setLastModified(System.currentTimeMillis()) }
        return result
    }

    fun write(ownerKey: String, rows: List<MerchantMediaItem>) {
        if (ownerKey.isBlank()) return
        val json = JSONObject().put(
            "rows",
            JSONArray().also { array ->
                rows.take(250).forEach { item ->
                    array.put(
                        JSONObject()
                            .put("name", item.name)
                            .put("url", item.url)
                            .put("kind", item.kind)
                    )
                }
            }
        )
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) return
        val target = fileFor(ownerKey)
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

    private fun fileFor(ownerKey: String): File =
        File(root, sha256(ownerKey) + ".json")
}

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
