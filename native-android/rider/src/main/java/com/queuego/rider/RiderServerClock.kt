package com.queuego.rider

import android.os.SystemClock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal data class RiderServerClockSample(
    val serverEpochMs: Long,
    val elapsedRealtimeMs: Long,
    val deviceWallEpochMs: Long
)

internal fun riderServerEpochFromHttpDate(raw: String?): Long? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

internal fun riderServerClockNow(
    sample: RiderServerClockSample?,
    elapsedRealtimeMs: Long,
    fallbackWallEpochMs: Long
): Long {
    val current = sample ?: return fallbackWallEpochMs
    val delta = elapsedRealtimeMs - current.elapsedRealtimeMs
    if (delta < 0L) return fallbackWallEpochMs
    return current.serverEpochMs + delta
}

internal fun riderServerClockSkewMs(sample: RiderServerClockSample?): Long? =
    sample?.let { it.serverEpochMs - it.deviceWallEpochMs }

internal data class RiderServerClockHealth(
    val headline: String,
    val detail: String,
    val healthy: Boolean?
)

internal fun riderServerClockHealth(skewMs: Long?): RiderServerClockHealth {
    if (skewMs == null) {
        return RiderServerClockHealth(
            headline = "ยังไม่ได้เทียบเวลา Server",
            detail = "เมื่อ QueueGo ติดต่อระบบสำเร็จ แอปจะปรับ countdown งานกับเวลา Server อัตโนมัติ",
            healthy = null
        )
    }
    val absolute = kotlin.math.abs(skewMs)
    val seconds = absolute / 1000.0
    return if (absolute < 5_000L) {
        RiderServerClockHealth(
            headline = "เวลาเครื่องตรงกับ Server",
            detail = "คลาดประมาณ %.1f วินาที · countdown ใช้เวลา Server".format(seconds),
            healthy = true
        )
    } else {
        RiderServerClockHealth(
            headline = "เวลาเครื่องคลาดจาก Server",
            detail = "คลาดประมาณ %.1f วินาที · QueueGo ชดเชยให้ countdown งานแล้ว".format(seconds),
            healthy = false
        )
    }
}

internal object RiderServerClock {
    @Volatile
    private var latest: RiderServerClockSample? = null

    fun observeHttpDate(
        rawDate: String?,
        responseElapsedRealtimeMs: Long = SystemClock.elapsedRealtime(),
        deviceWallEpochMs: Long = System.currentTimeMillis()
    ) {
        val serverEpochMs = riderServerEpochFromHttpDate(rawDate) ?: return
        latest = RiderServerClockSample(
            serverEpochMs = serverEpochMs,
            elapsedRealtimeMs = responseElapsedRealtimeMs,
            deviceWallEpochMs = deviceWallEpochMs
        )
    }

    fun nowEpochMillis(
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime(),
        fallbackWallEpochMs: Long = System.currentTimeMillis()
    ): Long = riderServerClockNow(latest, elapsedRealtimeMs, fallbackWallEpochMs)

    fun skewMillis(): Long? = riderServerClockSkewMs(latest)
}
