package com.queuego.rider

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal data class CachedRiderBenefits(
    val rows: List<RiderBenefit>,
    val fresh: Boolean
)

/**
 * Durable display-only cache for Rider Benefits.
 *
 * Benefits may render stale briefly while the server refreshes in the background.
 * This cache never owns order, finance, dispatch, approval or availability state.
 */
internal class RiderBenefitsCache(context: Context) {
    companion object {
        private const val MAX_FILE_BYTES = 512L * 1024L
        private const val MAX_ROWS = 100
        private const val MAX_TEXT = 2_000
        private const val FRESH_MS = 30L * 60L * 1000L
    }

    private val root = File(context.noBackupFilesDir, "rider_benefits_cache_v1").also {
        runCatching { it.mkdirs() }
    }

    fun read(userId: String): CachedRiderBenefits? {
        if (userId.isBlank()) return null
        val file = fileFor(userId)
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES) return null
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            if (root.optString("userId") != userId) return@runCatching null
            val source = root.optJSONArray("benefits") ?: JSONArray()
            val rows = buildList {
                for (index in 0 until minOf(source.length(), MAX_ROWS)) {
                    source.optJSONObject(index)?.toBenefit()?.let(::add)
                }
            }
            val age = System.currentTimeMillis() - file.lastModified()
            CachedRiderBenefits(rows = rows, fresh = age in 0L..FRESH_MS)
        }.onFailure { runCatching { file.delete() } }.getOrNull()
    }

    fun write(userId: String, rows: List<RiderBenefit>) {
        if (userId.isBlank() || rows.size > MAX_ROWS) return
        val json = JSONObject()
            .put("userId", userId)
            .put("benefits", JSONArray().also { array -> rows.forEach { array.put(it.toJson()) } })
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) return
        val target = fileFor(userId)
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

    private fun fileFor(userId: String): File =
        File(root, sha256(userId) + ".json")

    private fun RiderBenefit.toJson(): JSONObject = JSONObject()
        .put("id", id.take(MAX_TEXT))
        .put("category", category.take(MAX_TEXT))
        .put("providerName", providerName.take(MAX_TEXT))
        .put("title", title.take(MAX_TEXT))
        .putNullable("description", description?.take(MAX_TEXT))
        .put("startsOn", startsOn.take(MAX_TEXT))
        .putNullable("expiresOn", expiresOn?.take(MAX_TEXT))
        .putNullable("actionLabel", actionLabel?.take(MAX_TEXT))
        .putNullable("actionUrl", actionUrl?.take(MAX_TEXT))
        .putNullable("supportPhone", supportPhone?.take(MAX_TEXT))

    private fun JSONObject.toBenefit(): RiderBenefit? {
        val id = text("id") ?: return null
        val provider = text("providerName") ?: return null
        val title = text("title") ?: return null
        val startsOn = text("startsOn") ?: return null
        val category = text("category")
            ?.takeIf { it in setOf("fuel", "maintenance", "food", "telecom", "health", "other") }
            ?: "other"
        return RiderBenefit(
            id = id,
            category = category,
            providerName = provider,
            title = title,
            description = text("description"),
            startsOn = startsOn,
            expiresOn = text("expiresOn"),
            actionLabel = text("actionLabel"),
            actionUrl = text("actionUrl")?.takeIf { it.startsWith("https://") },
            supportPhone = text("supportPhone")
        )
    }

    private fun JSONObject.text(key: String): String? =
        if (!has(key) || isNull(key)) null
        else optString(key).trim().takeIf { it.isNotEmpty() && it.length <= MAX_TEXT }

    private fun JSONObject.putNullable(key: String, value: String?): JSONObject =
        put(key, value ?: JSONObject.NULL)
}

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
