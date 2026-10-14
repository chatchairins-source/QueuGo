package com.queuego.rider

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View

internal enum class RiderEventFeedbackKind {
    PICKUP_SUCCESS,
    DELIVERY_SUCCESS,
    ACTION_ERROR
}

internal data class RiderEventFeedbackSpec(
    val tones: List<Pair<Int, Int>>,
    val gapsMs: List<Long>,
    val successHaptic: Boolean
)

internal fun riderEventFeedbackSpec(kind: RiderEventFeedbackKind): RiderEventFeedbackSpec =
    when (kind) {
        RiderEventFeedbackKind.PICKUP_SUCCESS -> RiderEventFeedbackSpec(
            tones = listOf(ToneGenerator.TONE_PROP_BEEP to 170),
            gapsMs = emptyList(),
            successHaptic = true
        )
        RiderEventFeedbackKind.DELIVERY_SUCCESS -> RiderEventFeedbackSpec(
            tones = listOf(
                ToneGenerator.TONE_PROP_BEEP to 150,
                ToneGenerator.TONE_PROP_BEEP2 to 260
            ),
            gapsMs = listOf(90L),
            successHaptic = true
        )
        RiderEventFeedbackKind.ACTION_ERROR -> RiderEventFeedbackSpec(
            tones = listOf(
                ToneGenerator.TONE_PROP_BEEP2 to 260,
                ToneGenerator.TONE_PROP_BEEP2 to 260
            ),
            gapsMs = listOf(110L),
            successHaptic = false
        )
    }

/**
 * QueueGo-owned event feedback. Uses platform-generated tones only; no third-party audio assets.
 * STREAM_NOTIFICATION means Android volume / silent / DND policy remains authoritative.
 * performHapticFeedback respects the device's haptic settings and needs no vibration permission.
 */
internal fun playRiderEventFeedback(
    view: View,
    kind: RiderEventFeedbackKind
) {
    val spec = riderEventFeedbackSpec(kind)
    runCatching {
        val haptic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (spec.successHaptic) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
        } else {
            if (spec.successHaptic) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(haptic)
    }

    val tone = runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 74)
    }.getOrNull() ?: return

    val handler = Handler(Looper.getMainLooper())
    var offsetMs = 0L
    spec.tones.forEachIndexed { index, item ->
        handler.postDelayed({
            runCatching { tone.startTone(item.first, item.second) }
        }, offsetMs)
        offsetMs += item.second.toLong()
        if (index < spec.gapsMs.size) offsetMs += spec.gapsMs[index]
    }
    handler.postDelayed({
        runCatching { tone.release() }
    }, offsetMs + 120L)
}
