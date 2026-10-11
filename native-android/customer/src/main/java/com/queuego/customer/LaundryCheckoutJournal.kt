package com.queuego.customer

import android.content.Context
import com.queuego.shared.QueueGoHttpException
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

// A rolled-back SQL rejection may release the form only after an owner read
// confirms that this request has no committed order. Network failures never do.
internal fun isDefinitiveLaundryRejection(error: Exception, confirmedAbsent: Boolean): Boolean =
    confirmedAbsent && error is QueueGoHttpException && error.statusCode in setOf(400, 422) &&
        Regex("laundry is temporarily disabled|invalid laundry request|invalid pickup address|invalid pickup coordinates|invalid estimated quantity|note too long|active customer required|request id already used|laundry hub unavailable|laundry shop unavailable|laundry shop pickup location required|laundry pickup and return unavailable|laundry Rider unavailable|laundry service unavailable|OUTSIDE_SERVICE_AREA|DELIVERY_DISTANCE_EXCEEDED", RegexOption.IGNORE_CASE)
            .containsMatchIn(error.message.orEmpty())
