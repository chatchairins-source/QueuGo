package com.queuego.customer

import java.time.Instant

internal fun CustomerOrder.chatDeadlineMs(): Long? {
    if (status != "completed") return null
    val source = completedAt ?: updatedAt ?: return null
    val base = runCatching { Instant.parse(source).toEpochMilli() }.getOrNull() ?: return null
    return base + 30L * 60L * 1000L
}

fun CustomerOrder.chatAvailable(now: Long = System.currentTimeMillis()): Boolean {
    if (riderId.isNullOrBlank()) return false
    if (status != "completed") return status !in setOf("cancelled", "no_rider_available")
    return (chatDeadlineMs() ?: 0L) > now
}



fun CustomerOrder.chatMinutesLeft(now: Long = System.currentTimeMillis()): Int {
    val deadline = chatDeadlineMs() ?: return 0
    return kotlin.math.max(0, kotlin.math.ceil((deadline - now) / 60_000.0).toInt())
}
