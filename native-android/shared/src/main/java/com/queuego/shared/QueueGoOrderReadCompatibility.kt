package com.queuego.shared

import java.util.concurrent.ConcurrentHashMap

/** Reads remain compatible while optional Native features await backend deployment.
 * Never changes ownership/status filters, core money fields, RPCs or writes.
 */
class QueueGoOrderReadCompatibility {
    private val absent = ConcurrentHashMap.newKeySet<String>()
    private val optional = setOf(
        "ride_distance_km", "ride_duration_minutes", "rider_delivery_payout",
        "next_after_order_id", "merchant_ready_estimate_at", "merchant_ready_revision",
        "express_distance_km", "express_duration_minutes", "express_item_type", "express_weight_kg",
        "express_size", "express_vehicle_type", "express_sender_name", "express_sender_phone",
        "express_receiver_name", "express_receiver_phone", "express_instructions"
    )

    fun compatiblePath(path: String): String {
        if (!isOrderRead(path)) return path
        val parts = path.substringAfter('?').split('&').mapNotNull { part ->
            when (part.substringBefore('=')) {
                "select" -> "select=" + part.substringAfter('=').split(',').filterNot { it in absent }.joinToString(",")
                "order" -> part.substringAfter('=').split(',').filterNot { it.substringBefore('.') in absent }
                    .takeIf { it.isNotEmpty() }?.let { "order=" + it.joinToString(",") }
                else -> part
            }
        }
        return path.substringBefore('?') + "?" + parts.joinToString("&")
    }

    fun <T> read(path: String, statusCode: (Throwable) -> Int?, request: (String) -> T): T {
        if (!isOrderRead(path)) return request(path)
        repeat(optional.size + 1) {
            val attempted = compatiblePath(path)
            try { return request(attempted) }
            catch (failure: Exception) {
                if (statusCode(failure) != 400) throw failure
                val column = Regex("^column (?:orders\\.)?([a-z_]+) does not exist$")
                    .matchEntire(failure.message.orEmpty())?.groupValues?.get(1)
                // A missing filter cannot be dropped: its ownership/semantics must remain intact.
                val selected = attempted.substringAfter("select=", "").substringBefore('&').split(',')
                if (column !in optional || column !in selected) throw failure
                absent.add(column!!)
            }
        }
        error("ยังโหลดข้อมูลงานไม่ได้ กรุณาลองใหม่")
    }

    private fun isOrderRead(path: String) = path.startsWith("orders?select=") || path.startsWith("/rest/v1/orders?select=")
}
