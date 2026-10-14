package com.queuego.ridernavigationmap

import kotlin.math.max

enum class NavigationFixQuality {
    GOOD,
    DEGRADED,
    WEAK,
    REJECTED
}

data class NavigationLocationFilterResult(
    val fix: NavigationMapFix?,
    val quality: NavigationFixQuality,
    val rejected: Boolean = false,
    val reason: String? = null
)

class NavigationLocationFilter(
    private val maxAcceptedAccuracyMeters: Float = 160f,
    private val maxPlausibleSpeedMetersPerSecond: Double = 55.0
) {
    private var previousAcceptedFix: NavigationMapFix? = null

    fun reset() {
        previousAcceptedFix = null
    }

    fun filter(next: NavigationMapFix): NavigationLocationFilterResult {
        if (!next.point.valid) {
            return rejected("invalid-coordinate")
        }

        val accuracy = next.accuracyMeters
            ?.takeIf { it.isFinite() && it >= 0f }

        if (accuracy != null && accuracy > maxAcceptedAccuracyMeters) {
            return rejected("accuracy-too-low")
        }

        val previous = previousAcceptedFix
        if (previous == null) {
            previousAcceptedFix = next
            return accepted(next, qualityForAccuracy(accuracy))
        }

        val elapsedSeconds = elapsedSeconds(previous, next)
        if (elapsedSeconds != null && elapsedSeconds <= 0.0) {
            return rejected("stale-fix")
        }

        val distanceMeters = navigationHaversineMeters(previous.point, next.point)
        val combinedAccuracy = max(
            previous.accuracyMeters?.takeIf { it.isFinite() && it >= 0f }?.toDouble() ?: 0.0,
            accuracy?.toDouble() ?: 0.0
        )

        if (elapsedSeconds != null && elapsedSeconds > 0.0) {
            val impliedSpeed = distanceMeters / elapsedSeconds
            val reportedSpeed = next.speedMetersPerSecond
                ?.takeIf { it.isFinite() && it >= 0f }
                ?.toDouble()
                ?: 0.0
            val plausibleLimit = max(
                maxPlausibleSpeedMetersPerSecond,
                reportedSpeed * 2.2 + 12.0
            )
            val teleportDistance = max(85.0, combinedAccuracy * 2.0)
            if (distanceMeters > teleportDistance && impliedSpeed > plausibleLimit) {
                return rejected("implausible-teleport")
            }
        }

        val speed = next.speedMetersPerSecond
            ?.takeIf { it.isFinite() && it >= 0f }
        val stationaryThreshold = max(
            1.8,
            ((accuracy ?: previous.accuracyMeters ?: 12f).coerceAtLeast(0f) * 0.12f).toDouble()
        )

        val filtered = when {
            speed != null && speed < 0.8f && distanceMeters <= stationaryThreshold ->
                next.copy(point = previous.point)
            distanceMeters <= 0.5 ->
                next.copy(point = previous.point)
            else -> {
                val alpha = smoothingAlpha(speed, accuracy)
                next.copy(
                    point = NavigationMapPoint(
                        latitude = previous.point.latitude +
                            (next.point.latitude - previous.point.latitude) * alpha,
                        longitude = previous.point.longitude +
                            (next.point.longitude - previous.point.longitude) * alpha
                    )
                )
            }
        }

        previousAcceptedFix = filtered
        return accepted(filtered, qualityForAccuracy(accuracy))
    }

    private fun smoothingAlpha(speedMetersPerSecond: Float?, accuracyMeters: Float?): Double {
        val speedAlpha = when {
            speedMetersPerSecond == null -> 0.58
            speedMetersPerSecond >= 12f -> 0.94
            speedMetersPerSecond >= 5f -> 0.86
            speedMetersPerSecond >= 2f -> 0.74
            else -> 0.56
        }
        val accuracyPenalty = when {
            accuracyMeters == null -> 0.0
            accuracyMeters <= 20f -> 0.0
            accuracyMeters <= 50f -> 0.06
            else -> 0.12
        }
        return (speedAlpha - accuracyPenalty).coerceIn(0.40, 0.96)
    }

    private fun elapsedSeconds(previous: NavigationMapFix, next: NavigationMapFix): Double? {
        val previousNanos = previous.elapsedRealtimeNanos
        val nextNanos = next.elapsedRealtimeNanos
        if (previousNanos <= 0L || nextNanos <= 0L) return null
        return (nextNanos - previousNanos) / 1_000_000_000.0
    }

    private fun qualityForAccuracy(accuracyMeters: Float?): NavigationFixQuality = when {
        accuracyMeters == null -> NavigationFixQuality.DEGRADED
        accuracyMeters <= 25f -> NavigationFixQuality.GOOD
        accuracyMeters <= 60f -> NavigationFixQuality.DEGRADED
        else -> NavigationFixQuality.WEAK
    }

    private fun accepted(
        fix: NavigationMapFix,
        quality: NavigationFixQuality
    ) = NavigationLocationFilterResult(
        fix = fix,
        quality = quality,
        rejected = false
    )

    private fun rejected(reason: String) = NavigationLocationFilterResult(
        fix = null,
        quality = NavigationFixQuality.REJECTED,
        rejected = true,
        reason = reason
    )
}
