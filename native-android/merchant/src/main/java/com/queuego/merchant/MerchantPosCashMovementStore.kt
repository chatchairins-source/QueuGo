package com.queuego.merchant

import android.content.Context
import org.json.JSONObject
import java.util.UUID

// A server-authoritative cash movement may time out after it commits.
// Persist the *same* request key and payload before sending so retries never duplicate cash.
internal data class PosCashMovementPending(
    val shopId: String,
    val actorId: String,
    val requestId: String,
    val type: String,
    val amount: Double,
    val reason: String
)

internal class MerchantPosCashMovementStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "queuego-pos-cash-movement-v1", Context.MODE_PRIVATE
    )
    private fun key(shopId: String, actorId: String): String = "pending:${shopId}:${actorId}"

    fun load(shopId: String, actorId: String): PosCashMovementPending? {
        val raw = prefs.getString(key(shopId, actorId), null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            val result = PosCashMovementPending(
                shopId = obj.getString("shop_id"),
                actorId = obj.getString("actor_id"),
                requestId = obj.getString("request_id"),
                type = obj.getString("type"),
                amount = obj.getDouble("amount"),
                reason = obj.getString("reason")
            )
            result.takeIf {
                it.shopId == shopId && it.actorId == actorId &&
                    UUID.fromString(it.requestId).toString() == it.requestId &&
                    it.type in setOf("cash_in", "cash_out") &&
                    it.amount.isFinite() && it.amount > 0 && it.reason.trim().length >= 5
            }
        }.getOrNull()
    }

    fun save(pending: PosCashMovementPending): Boolean {
        if (load(pending.shopId, pending.actorId) != null) return false
        return prefs.edit().putString(key(pending.shopId, pending.actorId),
            JSONObject().put("shop_id", pending.shopId)
                .put("actor_id", pending.actorId)
                .put("request_id", pending.requestId)
                .put("type", pending.type)
                .put("amount", pending.amount)
                .put("reason", pending.reason).toString()
        ).commit()
    }

    fun clear(pending: PosCashMovementPending): Boolean {
        val stored = load(pending.shopId, pending.actorId) ?: return false
        if (stored != pending) return false
        return prefs.edit().remove(key(pending.shopId, pending.actorId)).commit()
    }
}
