package com.queuego.rider

import android.content.Context
import android.net.Uri
import org.json.JSONObject

internal const val RIDER_PROOF_DRAFT_MAX_AGE_MS = 24L * 60L * 60L * 1000L
internal const val RIDER_PROOF_CAPTURE_MAX_AGE_MS = 30L * 60L * 1000L

internal data class RiderProofDraft(
    val jobId: String,
    val mode: String,
    val marketPickupId: String?,
    val uri: String,
    val createdAtMs: Long
)

internal fun riderProofDraftMatches(
    draft: RiderProofDraft,
    jobId: String,
    mode: String,
    marketPickupId: String?
): Boolean =
    draft.jobId == jobId &&
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
    private val prefs = appContext.getSharedPreferences("rider_pending_proof_v1", Context.MODE_PRIVATE)

    private fun reservationKey(userId: String) = "capture:$userId"
    private fun confirmedKey(userId: String) = "confirmed:$userId"

    private fun encode(value: RiderProofDraft): String =
        JSONObject()
            .put("job_id", value.jobId)
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
                jobId = value.getString("job_id"),
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
        jobId: String,
        mode: String,
        marketPickupId: String?,
        uri: Uri,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val key = reservationKey(userId)
        val previous = decode(prefs.getString(key, null))
        if (previous?.uri != uri.toString()) discardUri(previous?.uri)
        val draft = RiderProofDraft(
            jobId = jobId,
            mode = mode,
            marketPickupId = marketPickupId,
            uri = uri.toString(),
            createdAtMs = nowMs
        )
        prefs.edit().putString(key, encode(draft)).apply()
    }

    fun cancelCapture(userId: String) {
        val key = reservationKey(userId)
        val previous = decode(prefs.getString(key, null))
        prefs.edit().remove(key).apply()
        discardUri(previous?.uri)
    }

    fun promoteCapture(
        userId: String,
        nowMs: Long = System.currentTimeMillis()
    ): RiderProofDraft? {
        val captureKey = reservationKey(userId)
        val capture = decode(prefs.getString(captureKey, null)) ?: return null
        if (!riderProofDraftFresh(capture.createdAtMs, nowMs, RIDER_PROOF_CAPTURE_MAX_AGE_MS)) {
            cancelCapture(userId)
            return null
        }
        if (!readable(capture.uri)) {
            cancelCapture(userId)
            return null
        }

        val confirmedSlot = confirmedKey(userId)
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
        jobId: String,
        mode: String,
        marketPickupId: String?,
        nowMs: Long = System.currentTimeMillis()
    ): RiderProofDraft? {
        val key = confirmedKey(userId)
        val draft = decode(prefs.getString(key, null)) ?: return null
        if (!riderProofDraftFresh(draft.createdAtMs, nowMs, RIDER_PROOF_DRAFT_MAX_AGE_MS)) {
            prefs.edit().remove(key).apply()
            discardUri(draft.uri)
            return null
        }
        if (!riderProofDraftMatches(draft, jobId, mode, marketPickupId)) return null
        if (!readable(draft.uri)) {
            prefs.edit().remove(key).apply()
            return null
        }
        return draft
    }

    fun clearConfirmedMatching(
        userId: String,
        jobId: String,
        mode: String,
        marketPickupId: String?
    ) {
        val key = confirmedKey(userId)
        val draft = decode(prefs.getString(key, null)) ?: return
        if (!riderProofDraftMatches(draft, jobId, mode, marketPickupId)) return
        prefs.edit().remove(key).apply()
        discardUri(draft.uri)
    }
}
