package com.queuego.rider

import android.content.Context
import android.net.Uri
import org.json.JSONObject

internal const val RIDER_PROOF_SLOT_DELIVERY = "delivery"
internal const val RIDER_PROOF_SLOT_RETURN = "return"
internal const val RIDER_PROOF_DRAFT_MAX_AGE_MS = 24L * 60L * 60L * 1000L
internal const val RIDER_PROOF_CAPTURE_MAX_AGE_MS = 30L * 60L * 1000L

internal data class RiderProofDraft(
    val workId: String,
    val mode: String,
    val marketPickupId: String?,
    val uri: String,
    val createdAtMs: Long
)

internal fun riderProofDraftMatches(
    draft: RiderProofDraft,
    workId: String,
    mode: String,
    marketPickupId: String?
): Boolean =
    draft.workId == workId &&
        draft.mode == mode &&
        draft.marketPickupId == marketPickupId

internal fun riderProofDraftFresh(
    createdAtMs: Long,
    nowMs: Long,
    maxAgeMs: Long
): Boolean =
    createdAtMs > 0L &&
        nowMs >= createdAtMs &&
        nowMs - createdAtMs <= maxAgeMs

internal class RiderPendingProofStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("rider_pending_proof_v2", Context.MODE_PRIVATE)

    private fun reservationKey(userId: String, slot: String) = "capture:$slot:$userId"
    private fun confirmedKey(userId: String, slot: String) = "confirmed:$slot:$userId"

    private fun encode(value: RiderProofDraft): String =
        JSONObject()
            .put("work_id", value.workId)
            .put("mode", value.mode)
            .put("market_pickup_id", value.marketPickupId ?: JSONObject.NULL)
            .put("uri", value.uri)
            .put("created_at_ms", value.createdAtMs)
            .toString()

    private fun decode(raw: String?): RiderProofDraft? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val value = JSONObject(raw)
            RiderProofDraft(
                workId = value.getString("work_id"),
                mode = value.getString("mode"),
                marketPickupId = value.optString("market_pickup_id")
                    .takeIf { it.isNotBlank() && it != "null" },
                uri = value.getString("uri"),
                createdAtMs = value.getLong("created_at_ms")
            )
        }.getOrNull()
    }

    private fun readable(uri: String): Boolean =
        runCatching {
            appContext.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                stream.read() != -1
            } == true
        }.getOrDefault(false)

    private fun discardUri(uri: String?) {
        if (uri.isNullOrBlank()) return
        runCatching {
            appContext.contentResolver.delete(Uri.parse(uri), null, null)
        }
    }

    fun reserveCapture(
        userId: String,
        slot: String,
        workId: String,
        mode: String,
        marketPickupId: String?,
        uri: Uri,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val key = reservationKey(userId, slot)
        val previousReservation = decode(prefs.getString(key, null))
        if (previousReservation?.uri != uri.toString()) discardUri(previousReservation?.uri)

        val draft = RiderProofDraft(
            workId = workId,
            mode = mode,
            marketPickupId = marketPickupId,
            uri = uri.toString(),
            createdAtMs = nowMs
        )
        prefs.edit().putString(key, encode(draft)).apply()
    }

    fun cancelCapture(userId: String, slot: String) {
        val key = reservationKey(userId, slot)
        val previousReservation = decode(prefs.getString(key, null))
        prefs.edit().remove(key).apply()
        discardUri(previousReservation?.uri)
    }

    fun promoteCapture(
        userId: String,
        slot: String,
        nowMs: Long = System.currentTimeMillis()
    ): RiderProofDraft? {
        val captureKey = reservationKey(userId, slot)
        val capture = decode(prefs.getString(captureKey, null)) ?: return null

        if (!riderProofDraftFresh(capture.createdAtMs, nowMs, RIDER_PROOF_CAPTURE_MAX_AGE_MS)) {
            cancelCapture(userId, slot)
            return null
        }
        if (!readable(capture.uri)) {
            cancelCapture(userId, slot)
            return null
        }

        val confirmedSlot = confirmedKey(userId, slot)
        val previousConfirmed = decode(prefs.getString(confirmedSlot, null))
        val confirmed = capture.copy(createdAtMs = nowMs)

        prefs.edit()
            .putString(confirmedSlot, encode(confirmed))
            .remove(captureKey)
            .apply()

        if (previousConfirmed?.uri != null && previousConfirmed.uri != confirmed.uri) {
            discardUri(previousConfirmed.uri)
        }
        return confirmed
    }

    fun loadConfirmedMatching(
        userId: String,
        slot: String,
        workId: String,
        mode: String,
        marketPickupId: String?,
        nowMs: Long = System.currentTimeMillis()
    ): RiderProofDraft? {
        val key = confirmedKey(userId, slot)
        val draft = decode(prefs.getString(key, null)) ?: return null

        if (!riderProofDraftFresh(draft.createdAtMs, nowMs, RIDER_PROOF_DRAFT_MAX_AGE_MS)) {
            prefs.edit().remove(key).apply()
            discardUri(draft.uri)
            return null
        }
        if (!riderProofDraftMatches(draft, workId, mode, marketPickupId)) return null
        if (!readable(draft.uri)) {
            prefs.edit().remove(key).apply()
            return null
        }
        return draft
    }

    fun clearConfirmedMatching(
        userId: String,
        slot: String,
        workId: String,
        mode: String,
        marketPickupId: String?
    ) {
        val key = confirmedKey(userId, slot)
        val draft = decode(prefs.getString(key, null)) ?: return
        if (!riderProofDraftMatches(draft, workId, mode, marketPickupId)) return

        prefs.edit().remove(key).apply()
        discardUri(draft.uri)
    }
}
