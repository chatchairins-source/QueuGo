package com.queuego.shared

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class CachedNativeSupportTickets(
    val rows: List<NativeSupportTicket>,
    val fresh: Boolean
)

/** Short-lived display cache for read-only support ticket history. */
class NativeSupportTicketCache(context: Context, roleKey: String) {
    companion object {
        private const val FRESH_MS = 60L * 1000L
        private const val MAX_FILE_BYTES = 768L * 1024L
        private const val MAX_ROWS = 50
        private const val MAX_TEXT = 4_000
    }

    private val namespace = roleKey.trim().ifBlank { "role" }
    private val root = File(context.noBackupFilesDir, "native_support_ticket_cache_v1").also {
        runCatching { it.mkdirs() }
    }

    fun read(ownerKey: String): CachedNativeSupportTickets? {
        if (ownerKey.isBlank()) return null
        val file = fileFor(ownerKey)
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES) return null
        return runCatching {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            if (json.optString("ownerKey") != ownerKey || json.optString("roleKey") != namespace) {
                return@runCatching null
            }
            val source = json.optJSONArray("rows") ?: JSONArray()
            val rows = buildList {
                for (index in 0 until minOf(source.length(), MAX_ROWS)) {
                    source.optJSONObject(index)?.toTicket()?.let(::add)
                }
            }
            val age = System.currentTimeMillis() - file.lastModified()
            CachedNativeSupportTickets(rows = rows, fresh = age in 0L..FRESH_MS)
        }.onFailure { runCatching { file.delete() } }.getOrNull()
    }

    fun write(ownerKey: String, rows: List<NativeSupportTicket>) {
        if (ownerKey.isBlank() || rows.size > MAX_ROWS) return
        val json = JSONObject()
            .put("ownerKey", ownerKey)
            .put("roleKey", namespace)
            .put("rows", JSONArray().also { array ->
                rows.forEach { array.put(it.toJson()) }
            })
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

    fun invalidate(ownerKey: String) {
        if (ownerKey.isBlank()) return
        runCatching { fileFor(ownerKey).delete() }
    }

    private fun fileFor(ownerKey: String): File =
        File(root, sha256("$namespace:$ownerKey") + ".json")

    private fun NativeSupportTicket.toJson(): JSONObject = JSONObject()
        .put("id", id.take(MAX_TEXT))
        .put("category", category.take(MAX_TEXT))
        .put("details", details.take(MAX_TEXT))
        .put("status", status.take(MAX_TEXT))
        .put("orderId", orderId ?: JSONObject.NULL)
        .put("createdAt", createdAt ?: JSONObject.NULL)
        .put("adminNote", adminNote?.take(MAX_TEXT) ?: JSONObject.NULL)
        .put("evidencePath", evidencePath ?: JSONObject.NULL)

    private fun JSONObject.toTicket(): NativeSupportTicket? {
        val id = text("id") ?: return null
        val category = text("category") ?: return null
        val details = text("details") ?: return null
        val status = text("status") ?: return null
        return NativeSupportTicket(
            id = id,
            category = category,
            details = details,
            status = status,
            orderId = text("orderId"),
            createdAt = text("createdAt"),
            adminNote = text("adminNote"),
            evidencePath = text("evidencePath")
        )
    }

    private fun JSONObject.text(key: String): String? =
        if (!has(key) || isNull(key)) null
        else optString(key).trim().takeIf { it.isNotEmpty() && it.length <= MAX_TEXT }
}

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
