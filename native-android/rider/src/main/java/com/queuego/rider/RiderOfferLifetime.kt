package com.queuego.rider

import java.time.Instant

/** Invalid/missing deadlines never create an indefinitely actionable offer. */
internal fun riderOfferSecondsLeft(expiresAt: String?, nowMs: Long = System.currentTimeMillis()): Int {
    val deadline = try { Instant.parse(expiresAt).toEpochMilli() } catch (_: Exception) { return 0 }
    if (deadline <= nowMs) return 0
    return ((deadline - nowMs) / 1000L + if ((deadline - nowMs) % 1000L > 0L) 1L else 0L)
        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

internal fun RiderJob.matchesPushReference(reference: String): Boolean =
    reference.isNotBlank() && (id == reference || marketOrderId == reference)
