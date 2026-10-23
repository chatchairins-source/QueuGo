package com.queuego.rider

import com.queuego.ridernavigationmap.NavigationGuidanceState
import com.queuego.ridernavigationmap.navigationTurnPhrase
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class RiderNavigationHudInfo(
    val distanceText: String,
    val etaText: String?,
    val instructionText: String?,
    val maneuverDistanceText: String? = null,
    val maneuverText: String? = null,
    val maneuverRoadName: String? = null,
    val maneuverTurnCode: Int? = null,
    val followingInstructionText: String? = null,
    val followingTurnCode: Int? = null
)

internal fun riderNavigationHudInfo(guidance: NavigationGuidanceState): RiderNavigationHudInfo? {
    if (guidance.weakGps || guidance.offRoute) return null
    if (guidance.arrived) {
        return RiderNavigationHudInfo(
            distanceText = "ถึงจุดหมาย",
            etaText = null,
            instructionText = "ถึงจุดถัดไปแล้ว",
            maneuverDistanceText = "0 ม.",
            maneuverText = "ถึงจุดหมายแล้ว",
            maneuverTurnCode = 9
        )
    }

    val remaining = guidance.distanceRemainingMeters
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?: return null
    val eta = guidance.etaSeconds
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?.let(::riderEtaLabel)

    val next = guidance.nextInstruction
    val nextPhrase = next?.let { navigationTurnPhrase(it.turnCode) }
    val nextDistanceMeters = guidance.distanceToInstructionMeters
        ?.takeIf { it.isFinite() && it >= 0.0 }
    val nextDistance = nextDistanceMeters?.let(::riderRouteDistanceLabel)
    val nextRoad = next?.roadName?.trim()?.takeIf { it.isNotEmpty() }
    val instruction = nextPhrase?.let { phrase ->
        buildString {
            append(phrase)
            if (nextDistance != null) append("ใน ").append(nextDistance)
            if (nextRoad != null) append(" · ").append(nextRoad)
        }
    }

    val following = guidance.followingInstruction
    val followingDistanceMeters = guidance.distanceToFollowingInstructionMeters
        ?.takeIf { it.isFinite() && it >= 0.0 }
    val showFollowing = following != null && (
        (nextDistanceMeters != null && nextDistanceMeters <= 400.0) ||
            (followingDistanceMeters != null && followingDistanceMeters <= 300.0)
        )
    val followingText = if (showFollowing) {
        navigationTurnPhrase(following!!.turnCode)?.let { phrase ->
            buildString {
                append(phrase)
                followingDistanceMeters?.let {
                    append("ใน ").append(riderRouteDistanceLabel(it))
                }
                following.roadName.trim().takeIf { it.isNotEmpty() }?.let {
                    append(" · ").append(it)
                }
            }
        }
    } else null

    return RiderNavigationHudInfo(
        distanceText = riderRouteDistanceLabel(remaining),
        etaText = eta,
        instructionText = instruction,
        maneuverDistanceText = nextDistance,
        maneuverText = nextPhrase,
        maneuverRoadName = nextRoad,
        maneuverTurnCode = next?.turnCode,
        followingInstructionText = followingText,
        followingTurnCode = if (showFollowing) following?.turnCode else null
    )
}

internal fun riderNavigationSpeedLimitKph(guidance: NavigationGuidanceState): Int? {
    if (guidance.weakGps || guidance.offRoute) return null
    val kph = guidance.speedLimitKph
        ?.takeIf { it.isFinite() && it > 0.0 && it <= 160.0 }
        ?: return null
    return kph.roundToInt().coerceIn(1, 160)
}

internal fun riderNavigationCustomerNote(
    note: String?,
    delivering: Boolean,
    guidance: NavigationGuidanceState,
    revealWithinMeters: Double = 300.0
): String? {
    if (!delivering || guidance.weakGps || guidance.offRoute) return null
    val remaining = guidance.distanceRemainingMeters
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?: return null
    if (remaining > revealWithinMeters.coerceAtLeast(0.0)) return null
    return cleanRiderOrderNote(note)
}

internal fun riderRouteDistanceLabel(meters: Double): String {
    val safe = meters.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    return if (safe < 1_000.0) {
        val rounded = ((safe / 10.0).roundToInt() * 10).coerceAtLeast(0)
        "$rounded ม."
    } else {
        String.format(Locale.US, "%.1f กม.", safe / 1_000.0)
    }
}

internal fun riderEtaLabel(seconds: Double): String {
    val safe = seconds.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    val totalMinutes = ceil(safe / 60.0).toInt().coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours ชม. $minutes นาที"
        hours > 0 -> "$hours ชม."
        else -> "$totalMinutes นาที"
    }
}
// QueueGo Rider unified parity release-gate checkpoint: ETA HUD is read-only.
