package com.queuego.merchant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Durable display cache for Merchant module settings. Server remains authoritative for actions. */
internal class MerchantModuleCache(context: Context) {
    companion object {
        private const val MAX_FILE_BYTES = 512L * 1024L
    }

    private val root = File(context.noBackupFilesDir, "merchant_module_cache_v1").also {
        runCatching { it.mkdirs() }
    }

    fun read(ownerId: String): List<MerchantModule> {
        if (ownerId.isBlank()) return emptyList()
        val file = fileFor(ownerId)
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES) return emptyList()
        val json = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .onFailure { runCatching { file.delete() } }
            .getOrNull() ?: return emptyList()
        val rows = json.optJSONArray("rows") ?: JSONArray()
        val result = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val key = row.optString("key")
                if (key.isBlank()) continue
                add(
                    MerchantModule(
                        id = row.optString("id").takeIf { it.isNotBlank() && it != "null" },
                        key = key,
                        status = row.optString("status").ifBlank { "disabled" },
                        startedAt = row.optString("startedAt").takeIf { it.isNotBlank() && it != "null" },
                        expiresAt = row.optString("expiresAt").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
        runCatching { file.setLastModified(System.currentTimeMillis()) }
        return result
    }

    fun write(ownerId: String, rows: List<MerchantModule>) {
        if (ownerId.isBlank()) return
        val json = JSONObject().put(
            "rows",
            JSONArray().also { array ->
                rows.forEach { module ->
                    array.put(
                        JSONObject()
                            .put("id", module.id ?: JSONObject.NULL)
                            .put("key", module.key)
                            .put("status", module.status)
                            .put("startedAt", module.startedAt ?: JSONObject.NULL)
                            .put("expiresAt", module.expiresAt ?: JSONObject.NULL)
                    )
                }
            }
        )
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) return
        val target = fileFor(ownerId)
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

    private fun fileFor(ownerId: String): File {
        val name = MessageDigest.getInstance("SHA-256")
            .digest(ownerId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(root, "$name.json")
    }
}
