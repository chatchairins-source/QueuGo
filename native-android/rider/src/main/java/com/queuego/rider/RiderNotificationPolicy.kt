package com.queuego.rider

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.time.Instant

internal fun riderOfferExpiryMillis(value: String?): Long? =
    value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

internal fun riderOfferAlertKey(userId: String, orderId: String, expiry: Long): String =
    "$userId:offer:$orderId:$expiry"

internal fun riderAlertReceipts(previous: List<String>, key: String): List<String>? =
    if (key in previous) null else (previous + key).takeLast(256)

private val riderAlertReceiptLock = Any()

/** Only event identity is persisted; no tokens, messages, customer data or positions. */
internal fun consumeRiderAlert(context: Context, key: String): Boolean = synchronized(riderAlertReceiptLock) {
    val prefs = context.applicationContext.getSharedPreferences("queuego_rider_alert_receipts", Context.MODE_PRIVATE)
    val previous = runCatching {
        val array = JSONArray(prefs.getString("events", "[]"))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())
    val next = riderAlertReceipts(previous, key) ?: return@synchronized false
    val saved = prefs.edit().putString("events", JSONArray(next).toString()).commit()
    if (!saved) Log.w("QueueGoPush", "Alert receipt persistence failed")
    saved
}
