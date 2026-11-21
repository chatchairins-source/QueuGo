package com.queuego.customer

import org.json.JSONObject
import java.util.UUID

internal data class PendingMarketCheckout(val rpc: String, val body: JSONObject, val cartSnapshot: String) {
    init {
        require(rpc in setOf("queuego_place_market_order", "queuego_add_market_order_shops"))
        UUID.fromString(body.getString("p_market_order_id"))
        if (rpc == "queuego_add_market_order_shops") UUID.fromString(body.getString("p_request_id"))
        require(body.getJSONArray("p_items").length() > 0)
    }
    fun encode(): String = JSONObject().put("rpc", rpc).put("body", body).put("cart", cartSnapshot).toString()
    companion object {
        fun decode(raw: String): PendingMarketCheckout {
            val value = JSONObject(raw)
            return PendingMarketCheckout(value.getString("rpc"), value.getJSONObject("body"), value.getString("cart"))
        }
    }
}

// Persist the destination and consumed guest revision in one commit before
// clearing the source. Reopening after source cleanup failure skips re-merging.
internal fun commitMarketGuestMerge(alreadyMerged: Boolean, persistMerged: () -> Unit, clearGuest: () -> Unit) {
    if (!alreadyMerged) persistMerged()
    clearGuest()
}
