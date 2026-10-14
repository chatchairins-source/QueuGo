package com.queuego.rider

import android.content.Context
import org.json.JSONObject

internal const val RIDER_DELIVERY_CONTACT_ATTEMPT_MAX_AGE_MS = 24L * 60L * 60L * 1000L

internal enum class RiderDeliveryContactChannel {
    CALL,
    CHAT
}

internal data class RiderDeliveryContactAttempts(
    val callCount: Int = 0,
    val chatCount: Int = 0,
    val lastAttemptAtMs: Long = 0L
) {
    val totalCount: Int get() = callCount + chatCount
}

internal fun riderDeliveryContactAttemptsFresh(
    attempts: RiderDeliveryContactAttempts,
    nowMs: Long,
    maxAgeMs: Long = RIDER_DELIVERY_CONTACT_ATTEMPT_MAX_AGE_MS
): Boolean =
    attempts.totalCount > 0 &&
        attempts.lastAttemptAtMs > 0L &&
        nowMs >= attempts.lastAttemptAtMs &&
        nowMs - attempts.lastAttemptAtMs <= maxAgeMs

internal fun riderRecordDeliveryContactAttempt(
    current: RiderDeliveryContactAttempts,
    channel: RiderDeliveryContactChannel,
    nowMs: Long
): RiderDeliveryContactAttempts =
    when (channel) {
        RiderDeliveryContactChannel.CALL -> current.copy(
            callCount = (current.callCount + 1).coerceAtMost(99),
            lastAttemptAtMs = nowMs
        )
        RiderDeliveryContactChannel.CHAT -> current.copy(
            chatCount = (current.chatCount + 1).coerceAtMost(99),
            lastAttemptAtMs = nowMs
        )
    }

internal fun riderDeliveryIssueNoteWithAttempts(
    note: String,
    attempts: RiderDeliveryContactAttempts
): String {
    val marker = "[CONTACT_ATTEMPTS:${attempts.totalCount};CALL:${attempts.callCount};CHAT:${attempts.chatCount}]"
    val clean = note.trim().take(1050)
    return if (clean.isBlank()) marker else "$marker $clean"
}

internal class RiderDeliveryContactAttemptStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "rider_delivery_contact_attempts_v1",
        Context.MODE_PRIVATE
    )

    private fun key(userId: String, orderId: String) = "attempts:$userId:$orderId"

    private fun encode(value: RiderDeliveryContactAttempts): String =
        JSONObject()
            .put("call_count", value.callCount)
            .put("chat_count", value.chatCount)
            .put("last_attempt_at_ms", value.lastAttemptAtMs)
            .toString()

    private fun decode(raw: String?): RiderDeliveryContactAttempts? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val value = JSONObject(raw)
            RiderDeliveryContactAttempts(
                callCount = value.optInt("call_count").coerceIn(0, 99),
                chatCount = value.optInt("chat_count").coerceIn(0, 99),
                lastAttemptAtMs = value.optLong("last_attempt_at_ms")
            )
        }.getOrNull()
    }

    fun load(
        userId: String,
        orderId: String,
        nowMs: Long = System.currentTimeMillis()
    ): RiderDeliveryContactAttempts {
        val storageKey = key(userId, orderId)
        val value = decode(prefs.getString(storageKey, null)) ?: return RiderDeliveryContactAttempts()
        if (!riderDeliveryContactAttemptsFresh(value, nowMs)) {
            prefs.edit().remove(storageKey).apply()
            return RiderDeliveryContactAttempts()
        }
        return value
    }

    fun record(
        userId: String,
        orderId: String,
        channel: RiderDeliveryContactChannel,
        nowMs: Long = System.currentTimeMillis()
    ): RiderDeliveryContactAttempts {
        val current = load(userId, orderId, nowMs)
        val updated = riderRecordDeliveryContactAttempt(current, channel, nowMs)
        prefs.edit().putString(key(userId, orderId), encode(updated)).apply()
        return updated
    }

    fun clear(userId: String, orderId: String) {
        prefs.edit().remove(key(userId, orderId)).apply()
    }
}
