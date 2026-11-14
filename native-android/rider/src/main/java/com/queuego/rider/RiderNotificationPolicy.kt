package com.queuego.rider

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun riderOfferExpiryMillis(value: String?): Long? =
    value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

internal fun riderOfferAlertKey(userId: String, orderId: String, expiry: Long): String =
    "$userId:offer:$orderId:$expiry"

internal fun riderAlertReceipts(previous: List<String>, key: String): List<String>? =
    if (key in previous) null else (previous + key).takeLast(256)

private const val RIDER_ALERT_RECEIPT_MAX_KEY_CHARS = 1024
private const val RIDER_ALERT_RECEIPTS_MAX_JSON_CHARS = 512 * 1024
private val riderAlertReceiptLock = Any()

/** Only event identity is persisted; no tokens, messages, customer data or positions. */
internal suspend fun consumeRiderAlert(context: Context, key: String): Boolean = withContext(Dispatchers.IO) {
    if (key.length !in 1..RIDER_ALERT_RECEIPT_MAX_KEY_CHARS) return@withContext false
    synchronized(riderAlertReceiptLock) {
        val prefs = context.applicationContext.getSharedPreferences("queuego_rider_alert_receipts", Context.MODE_PRIVATE)
        val previous = runCatching {
            val stored = prefs.getString("events", "[]") ?: "[]"
            val safe = if (stored.length <= RIDER_ALERT_RECEIPTS_MAX_JSON_CHARS) stored else {
                prefs.edit().remove("events").apply()
                "[]"
            }
            val array = JSONArray(safe)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
        val next = riderAlertReceipts(previous, key) ?: return@synchronized false
        val encoded = JSONArray(next).toString()
        if (encoded.length > RIDER_ALERT_RECEIPTS_MAX_JSON_CHARS) return@synchronized false
        val saved = prefs.edit().putString("events", encoded).commit()
        if (!saved) Log.w("QueueGoPush", "Alert receipt persistence failed")
        saved
    }
}
