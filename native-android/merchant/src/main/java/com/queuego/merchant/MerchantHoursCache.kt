package com.queuego.merchant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Durable local-first display cache for Merchant business hours. Server remains authoritative. */
internal class MerchantHoursCache(context: Context) {
    companion object {
        private const val MAX_FILE_BYTES = 256L * 1024L
    }

    private val root = File(context.noBackupFilesDir, "merchant_hours_cache_v1").also {
        runCatching { it.mkdirs() }
    }

    fun read(shopId: String, fromDay: String): MerchantHoursState? {
        if (shopId.isBlank()) return null
        val file = fileFor(shopId)
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES) return null
        val json = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
            .onFailure { runCatching { file.delete() } }
            .getOrNull() ?: return null
        return runCatching {
            val rows = json.optJSONArray("days") ?: JSONArray()
            val byDay = HashMap<Int, MerchantBusinessDay>()
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val weekday = row.optInt("weekday", -1)
                if (weekday !in 0..6) continue
                byDay[weekday] = MerchantBusinessDay(
                    weekday = weekday,
                    opensAt = row.optString("opensAt").ifBlank { "06:00" },
                    closesAt = row.optString("closesAt").ifBlank { "22:00" },
                    isClosed = row.optBoolean("isClosed", false)
                )
            }
            val days = (0..6).map { weekday ->
                byDay[weekday] ?: MerchantBusinessDay(weekday, "06:00", "22:00", false)
            }
            val special = json.optJSONObject("special")?.let { row ->
                val day = row.optString("day").takeIf { it.isNotBlank() && it >= fromDay }
                if (day == null) null else MerchantSpecialHours(
                    day = day,
                    opensAt = row.optString("opensAt").ifBlank { "06:00" },
                    closesAt = row.optString("closesAt").ifBlank { "22:00" },
                    isClosed = row.optBoolean("isClosed", true)
                )
            }
            runCatching { file.setLastModified(System.currentTimeMillis()) }
            MerchantHoursState(days, special)
        }.getOrNull()
    }

    fun write(shopId: String, state: MerchantHoursState) {
        if (shopId.isBlank()) return
        val json = JSONObject().put(
            "days",
            JSONArray().also { rows ->
                state.days.sortedBy { it.weekday }.forEach { day ->
                    rows.put(
                        JSONObject()
                            .put("weekday", day.weekday)
                            .put("opensAt", day.opensAt)
                            .put("closesAt", day.closesAt)
                            .put("isClosed", day.isClosed)
                    )
                }
            }
        )
        state.special?.let { special ->
            json.put(
                "special",
                JSONObject()
                    .put("day", special.day ?: "")
                    .put("opensAt", special.opensAt)
                    .put("closesAt", special.closesAt)
                    .put("isClosed", special.isClosed)
            )
        }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) return
        val target = fileFor(shopId)
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

    private fun fileFor(shopId: String): File =
        File(root, sha256(shopId) + ".json")
}

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
