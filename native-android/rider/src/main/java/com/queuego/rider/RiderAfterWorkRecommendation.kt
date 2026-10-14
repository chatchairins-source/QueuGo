package com.queuego.rider

internal enum class RiderAfterWorkRecommendationTarget {
    HOME,
    EARNINGS
}

internal data class RiderAfterWorkRecommendation(
    val key: String,
    val title: String,
    val detail: String,
    val actionLabel: String,
    val target: RiderAfterWorkRecommendationTarget
)

internal fun riderAfterWorkRecommendation(
    online: Boolean,
    stopAfterWorkPending: Boolean,
    missions: List<RiderMission>,
    demandZones: List<RiderDemandZone>
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

    if (demandZones.isNotEmpty()) {
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

    return null
}
