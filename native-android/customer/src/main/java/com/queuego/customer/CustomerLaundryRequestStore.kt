package com.queuego.customer

import android.content.Context
import org.json.JSONObject
import java.util.UUID

internal data class PendingLaundryCheckout(val body: JSONObject) {
    fun prepared(): PreparedLaundryCheckout = PreparedLaundryCheckout(JSONObject(body.toString()))
}

internal class CustomerLaundryRequestStore(context: Context, userId: String) {
    private val prefs = context.getSharedPreferences("queuego_laundry_request_" + userId, Context.MODE_PRIVATE)

    fun journal(): CheckoutJournal<PendingLaundryCheckout> = object : CheckoutJournal<PendingLaundryCheckout> {
        override fun read(): PendingLaundryCheckout? {
            val raw = prefs.getString("pending_checkout", null) ?: return null
            val body = JSONObject(raw)
            UUID.fromString(body.getString("p_request_id"))
            UUID.fromString(body.getString("p_hub_id"))
            UUID.fromString(body.getString("p_service_id"))
            return PendingLaundryCheckout(body)
        }

        override fun write(pending: PendingLaundryCheckout) {
            check(prefs.edit().putString("pending_checkout", pending.body.toString()).commit()) {
                "บันทึกคำขอฝากซักไม่สำเร็จ"
            }
        }

        override fun clear() {
            check(prefs.edit().remove("pending_checkout").commit())
        }

        override fun complete(pending: PendingLaundryCheckout) {
            check(prefs.edit().remove("pending_checkout").commit())
        }
    }

    fun pending(prepared: PreparedLaundryCheckout): PendingLaundryCheckout =
        PendingLaundryCheckout(JSONObject(prepared.body.toString()))
}
