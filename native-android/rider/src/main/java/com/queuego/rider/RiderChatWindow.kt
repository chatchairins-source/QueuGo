package com.queuego.rider

import java.time.Instant

/** Production web riderChatDeadline: delivery timestamp, then existing order updated_at. */
internal data class RiderChatWindow(val status: String, val deadline: Long?) {
    fun isOpen(now: Long): Boolean = status != "cancelled" &&
        (status != "completed" || (deadline != null && deadline > now))
}

internal fun riderChatWindow(status: String, deliveredAt: String?, updatedAt: String?): RiderChatWindow {
    val deadline = if (status == "completed") {
        val timestamp = deliveredAt?.takeIf { it.isNotBlank() && it != "null" }
            ?: updatedAt?.takeIf { it.isNotBlank() && it != "null" }
        timestamp?.let { runCatching { Instant.parse(it).toEpochMilli() + 30 * 60_000L }.getOrNull() } ?: 0L
    } else null
    return RiderChatWindow(status, deadline)
}
