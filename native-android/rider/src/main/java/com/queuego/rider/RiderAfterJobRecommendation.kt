package com.queuego.rider

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal const val RIDER_AFTER_JOB_MAX_REPOSITION_KM = 15.0

internal data class RiderAfterJobRecommendation(
    val zone: RiderDemandZone,
    val distanceKm: Double
)

internal fun riderAfterJobRecommendation(
    zones: List<RiderDemandZone>,
    originLatitude: Double?,
    originLongitude: Double?,
    maxDistanceKm: Double = RIDER_AFTER_JOB_MAX_REPOSITION_KM
): RiderAfterJobRecommendation? {
    val lat = originLatitude?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
    val lng = originLongitude?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
    if (!maxDistanceKm.isFinite() || maxDistanceKm <= 0.0) return null

    return zones
        .asSequence()
        .filter { zone ->
            zone.latitude.isFinite() &&
                zone.longitude.isFinite() &&
                zone.latitude in -90.0..90.0 &&
                zone.longitude in -180.0..180.0 &&
                zone.level in 1..3
        }
        .map { zone ->
            RiderAfterJobRecommendation(
                zone = zone,
                distanceKm = riderHaversineKm(lat, lng, zone.latitude, zone.longitude)
            )
        }
        .filter { it.distanceKm <= maxDistanceKm }
        .sortedWith(
            compareByDescending<RiderAfterJobRecommendation> { it.zone.level }
                .thenBy { it.distanceKm }
        )
        .firstOrNull()
}

internal fun riderAfterJobLevelLabel(level: Int): String = when (level) {
    3 -> "พื้นที่คึกคักมาก"
    2 -> "พื้นที่คึกคัก"
    else -> "พื้นที่มีแนวโน้มงาน"
}

internal fun riderAfterJobDistanceLabel(distanceKm: Double): String {
    val safe = distanceKm.coerceAtLeast(0.0)
    return if (safe < 1.0) {
        "ห่างประมาณ " + (safe * 1000.0).toInt().coerceAtLeast(0) + " ม."
    } else {
        "ห่างประมาณ " + String.format(java.util.Locale.US, "%.1f", safe) + " กม."
    }
}

private fun riderHaversineKm(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double
): Double {
    val earthKm = 6371.0088
    val lat1 = Math.toRadians(fromLatitude)
    val lat2 = Math.toRadians(toLatitude)
    val dLat = lat2 - lat1
    val dLng = Math.toRadians(toLongitude - fromLongitude)
    val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
        cos(lat1) * cos(lat2) * sin(dLng / 2.0) * sin(dLng / 2.0)
    return earthKm * 2.0 * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
}
