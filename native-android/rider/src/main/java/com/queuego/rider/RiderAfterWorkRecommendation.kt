package com.queuego.rider

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal const val RIDER_AFTER_WORK_MAX_REPOSITION_KM = 15.0

internal enum class RiderAfterWorkRecommendationTarget {
    HOME,
    EARNINGS,
    DEMAND_NAVIGATION
}

internal data class RiderAfterWorkRecommendation(
    val key: String,
    val title: String,
    val detail: String,
    val actionLabel: String,
    val target: RiderAfterWorkRecommendationTarget,
    val latitude: Double? = null,
    val longitude: Double? = null
)

internal fun riderAfterWorkRecommendation(
    online: Boolean,
    stopAfterWorkPending: Boolean,
    missions: List<RiderMission>,
    demandZones: List<RiderDemandZone>,
    originLatitude: Double? = null,
    originLongitude: Double? = null,
    maxRepositionKm: Double = RIDER_AFTER_WORK_MAX_REPOSITION_KM
): RiderAfterWorkRecommendation? {
    if (!online || stopAfterWorkPending) return null

    val activeMission = missions.firstOrNull { it.state == "active" && !it.completed }
        ?: missions.firstOrNull { it.state == "upcoming" && !it.completed }
    if (activeMission != null) {
        val progress = "${activeMission.progressJobs}/${activeMission.targetJobs} เที่ยว"
        val reward = activeMission.rewardNote?.takeIf { it.isNotBlank() }
        return RiderAfterWorkRecommendation(
            key = "mission:${activeMission.id}",
            title = if (activeMission.state == "upcoming") "ภารกิจกำลังจะเริ่ม" else "ไปต่อกับภารกิจของคุณ",
            detail = buildString {
                append(activeMission.title)
                append(" · ")
                append(progress)
                reward?.let {
                    append(" · ")
                    append(it)
                }
            },
            actionLabel = "ดูภารกิจ",
            target = RiderAfterWorkRecommendationTarget.EARNINGS
        )
    }

    if (demandZones.isEmpty()) return null

    val originLat = originLatitude?.takeIf { it.isFinite() && it in -90.0..90.0 }
    val originLng = originLongitude?.takeIf { it.isFinite() && it in -180.0..180.0 }
    val safeRadius = maxRepositionKm.takeIf { it.isFinite() && it > 0.0 }

    if (originLat != null && originLng != null && safeRadius != null) {
        val best = demandZones
            .asSequence()
            .filter { zone ->
                zone.latitude.isFinite() &&
                    zone.longitude.isFinite() &&
                    zone.latitude in -90.0..90.0 &&
                    zone.longitude in -180.0..180.0 &&
                    zone.level in 1..3
            }
            .map { zone ->
                zone to riderAfterWorkDistanceKm(
                    originLat,
                    originLng,
                    zone.latitude,
                    zone.longitude
                )
            }
            .filter { (_, distanceKm) -> distanceKm <= safeRadius }
            .sortedWith(
                compareByDescending<Pair<RiderDemandZone, Double>> { it.first.level }
                    .thenBy { it.second }
            )
            .firstOrNull()

        if (best != null) {
            val zone = best.first
            val distanceKm = best.second
            return RiderAfterWorkRecommendation(
                key = "demand:${zone.latitude}:${zone.longitude}:${zone.level}:${zone.windowHours}",
                title = riderAfterWorkDemandLevelLabel(zone.level),
                detail = riderAfterWorkDistanceLabel(distanceKm) +
                    " · ข้อมูลพื้นที่งาน ${zone.windowHours} ชม. · ไม่รับประกันว่าจะมีงาน",
                actionLabel = "นำทางไปพื้นที่งาน",
                target = RiderAfterWorkRecommendationTarget.DEMAND_NAVIGATION,
                latitude = zone.latitude,
                longitude = zone.longitude
            )
        }
    }

    val strongestLevel = demandZones.maxOf { it.level }
    val freshestWindow = demandZones.minOf { it.windowHours }
    return RiderAfterWorkRecommendation(
        key = "demand:$strongestLevel:$freshestWindow:${demandZones.size}",
        title = "ดูพื้นที่งานถัดไป",
        detail = "มีข้อมูลพื้นที่งานจริง ${demandZones.size} จุด · ระดับสูงสุด $strongestLevel/3 · ช่วง $freshestWindow ชม.",
        actionLabel = "ดูบนแผนที่",
        target = RiderAfterWorkRecommendationTarget.HOME
    )
}

internal fun riderAfterWorkDemandLevelLabel(level: Int): String = when (level) {
    3 -> "พื้นที่คึกคักมาก"
    2 -> "พื้นที่คึกคัก"
    else -> "พื้นที่มีแนวโน้มงาน"
}

internal fun riderAfterWorkDistanceLabel(distanceKm: Double): String {
    val safe = distanceKm.coerceAtLeast(0.0)
    return if (safe < 1.0) {
        "ห่างประมาณ " + (safe * 1000.0).toInt().coerceAtLeast(0) + " ม."
    } else {
        "ห่างประมาณ " + String.format(Locale.US, "%.1f", safe) + " กม."
    }
}

private fun riderAfterWorkDistanceKm(
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
