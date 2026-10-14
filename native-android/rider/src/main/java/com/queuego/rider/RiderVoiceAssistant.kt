package com.queuego.rider

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal object RiderVoiceAssistantSettings {
    private const val PREFS = "queuego-rider-voice-assistant"
    private val flows = linkedMapOf<String, MutableStateFlow<Boolean>>()

    @Synchronized
    fun enabled(context: Context, userId: String): StateFlow<Boolean> =
        flows.getOrPut(userId) {
            MutableStateFlow(
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean("enabled:$userId", false)
            )
        }

    @Synchronized
    fun setEnabled(context: Context, userId: String, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("enabled:$userId", enabled)
            .apply()
        flows.getOrPut(userId) { MutableStateFlow(enabled) }.value = enabled
    }
}

internal class RiderVoiceAssistantSpeaker(context: Context) : TextToSpeech.OnInitListener {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, String>? = null

    init {
        engine = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        val current = engine ?: return
        if (status != TextToSpeech.SUCCESS) {
            pending = null
            return
        }
        val thaiResult = current.setLanguage(Locale("th", "TH"))
        if (
            thaiResult == TextToSpeech.LANG_MISSING_DATA ||
            thaiResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            current.language = Locale.getDefault()
        }
        current.setSpeechRate(1.0f)
        ready = true
        pending?.let { (text, id) ->
            pending = null
            speak(text, id)
        }
    }

    fun speak(text: String, utteranceId: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (!ready) {
            pending = clean to utteranceId
            return
        }
        engine?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        pending = null
        runCatching { engine?.stop() }
    }

    fun close() {
        stop()
        ready = false
        runCatching { engine?.shutdown() }
        engine = null
    }
}

internal fun riderVoiceOfferAnnouncement(): String =
    "มีงานใหม่ กรุณาตรวจสอบและตอบรับภายในสามสิบวินาที"

internal fun riderVoiceJobStageKey(job: RiderJob?): String? =
    job?.let {
        listOf(
            it.id,
            it.status,
            if (it.shopArrived) "shop-arrived" else "shop-away",
            if (it.customerArrived) "customer-arrived" else "customer-away"
        ).joinToString(":")
    }

internal fun riderVoiceJobAnnouncement(job: RiderJob?): String? {
    val current = job ?: return null
    return when (current.status) {
        "rider_assigned", "assigned" ->
            if (current.shopArrived) {
                "ถึงร้านแล้ว กรุณาตรวจสอบรายการและรอรับสินค้า"
            } else {
                "รับงานแล้ว กำลังไปที่ร้าน"
            }
        "preparing" -> "ร้านกำลังเตรียมสินค้า"
        "ready" -> "ร้านเตรียมสินค้าเสร็จแล้ว กรุณาไปรับสินค้า"
        "picked_up", "in_progress" ->
            if (current.customerArrived) {
                "ถึงจุดส่งแล้ว กรุณาตรวจสอบยอดในหน้าจอและส่งสินค้า"
            } else {
                "รับสินค้าแล้ว กำลังนำส่งลูกค้า"
            }
        "completed" -> "งานเสร็จเรียบร้อยแล้ว"
        else -> null
    }
}
