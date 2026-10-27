package com.queuego.customer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal data class CachedCustomerSupportTickets(
    val rows: List<CustomerSupportTicket>,
    val fresh: Boolean
)

/** Short-lived display cache for the Customer support history. */
internal class CustomerSupportCache(context: Context) {
    companion object {
        private const val FRESH_MS = 60L * 1000L
        private const val MAX_FILE_BYTES = 512L * 1024L
        private const val MAX_ROWS = 30
        private const val MAX_TEXT = 4_000
    }

    private val root = File(context.noBackupFilesDir, "customer_support_cache_v1").also {
        runCatching { it.mkdirs() }
    }

    fun read(userId: String): CachedCustomerSupportTickets? {
        if (userId.isBlank()) return null
        val file = fileFor(userId)
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES) return null
        return runCatching {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            if (json.optString("userId") != userId) return@runCatching null
            val source = json.optJSONArray("rows") ?: JSONArray()
            val rows = buildList {
                for (index in 0 until minOf(source.length(), MAX_ROWS)) {
                    source.optJSONObject(index)?.toTicket()?.let(::add)
                }
            }
            val age = System.currentTimeMillis() - file.lastModified()
            CachedCustomerSupportTickets(rows = rows, fresh = age in 0L..FRESH_MS)
        }.onFailure { runCatching { file.delete() } }.getOrNull()
    }

    fun write(userId: String, rows: List<CustomerSupportTicket>) {
        if (userId.isBlank() || rows.size > MAX_ROWS) return
        val json = JSONObject()
            .put("userId", userId)
            .put("rows", JSONArray().also { array ->
                rows.forEach { ticket -> array.put(ticket.toJson()) }
            })
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

    private fun CustomerSupportTicket.toJson(): JSONObject = JSONObject()
        .put("id", id.take(MAX_TEXT))
        .put("category", category.take(MAX_TEXT))
        .put("details", details.take(MAX_TEXT))
        .put("status", status.take(MAX_TEXT))
        .put("orderId", orderId ?: JSONObject.NULL)
        .put("adminNote", adminNote?.take(MAX_TEXT) ?: JSONObject.NULL)
        .put("createdAt", createdAt ?: JSONObject.NULL)

    private fun JSONObject.toTicket(): CustomerSupportTicket? {
        val id = text("id") ?: return null
        val category = text("category") ?: return null
        val details = text("details") ?: return null
        val status = text("status") ?: return null
        return CustomerSupportTicket(
            id = id,
            category = category,
            details = details,
            status = status,
            orderId = text("orderId"),
            adminNote = text("adminNote"),
            createdAt = text("createdAt")
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
