package com.queuego.customer

import android.content.Context
import org.json.JSONObject
import java.util.UUID

internal data class PendingLaundryCheckout(val body: JSONObject) {
    init {
        listOf("p_request_id", "p_hub_id", "p_service_id").forEach { UUID.fromString(body.getString(it)) }
        require(body.getString("p_pickup_address").trim().length in 3..500)
        require(body.getDouble("p_pickup_latitude").let { it.isFinite() && it in -90.0..90.0 })
        require(body.getDouble("p_pickup_longitude").let { it.isFinite() && it in -180.0..180.0 })
        if (!body.isNull("p_estimated_quantity")) require(body.getDouble("p_estimated_quantity").let { it.isFinite() && it > 0 && it <= 1000 })
        require(body.optString("p_note", "").length <= 500)
    }
    fun encode(): String = body.toString()
    companion object { fun decode(raw: String) = PendingLaundryCheckout(JSONObject(raw)) }
}

internal class LaundryCheckoutJournal(context: Context, customerId: String) : CheckoutJournal<PendingLaundryCheckout> {
    private val prefs = context.getSharedPreferences("queuego_laundry_pending_" + customerId, Context.MODE_PRIVATE)
    fun hasPending() = prefs.contains("request")
    override fun read() = prefs.getString("request", null)?.let(PendingLaundryCheckout::decode)
    override fun write(pending: PendingLaundryCheckout) {
        check(prefs.edit().putString("request", pending.encode()).commit()) { "บันทึกคำขอฝากซักไม่สำเร็จ" }
    }
    override fun clear() { check(prefs.edit().remove("request").commit()) }
    override fun complete(pending: PendingLaundryCheckout) { clear() }
}
