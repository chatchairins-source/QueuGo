package com.queuego.rider

import com.queuego.ridernavigationmap.NavigationGuidanceState
import com.queuego.ridernavigationmap.navigationTurnPhrase
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class RiderNavigationHudInfo(
    val distanceText: String,
    val etaText: String?,
    val instructionText: String?
)

internal fun riderNavigationHudInfo(guidance: NavigationGuidanceState): RiderNavigationHudInfo? {
    if (guidance.weakGps || guidance.offRoute) return null
    if (guidance.arrived) {
        return RiderNavigationHudInfo(
            distanceText = "ถึงจุดหมาย",
            etaText = null,
            instructionText = "ถึงจุดถัดไปแล้ว"
        )
    }

    val remaining = guidance.distanceRemainingMeters
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?: return null
    val eta = guidance.etaSeconds
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?.let(::riderEtaLabel)

    val instruction = guidance.nextInstruction?.let { next ->
        navigationTurnPhrase(next.turnCode)?.let { phrase ->
            val distance = guidance.distanceToInstructionMeters
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?.let(::riderRouteDistanceLabel)
            val road = next.roadName.trim().takeIf { it.isNotEmpty() }
            buildString {
                append(phrase)
                if (distance != null) append("ใน ").append(distance)
                if (road != null) append(" · ").append(road)
            }
        }
    }

    return RiderNavigationHudInfo(
        distanceText = riderRouteDistanceLabel(remaining),
        etaText = eta,
        instructionText = instruction
    )
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
