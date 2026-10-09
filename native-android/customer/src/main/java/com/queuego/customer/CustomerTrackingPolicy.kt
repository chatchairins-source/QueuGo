package com.queuego.customer

import com.queuego.shared.QgMapPoint

fun CustomerOrder.trackingClosed(): Boolean = status.lowercase() in
    setOf("completed", "cancelled", "no_rider_available")

fun customerTrackingPoints(order: CustomerOrder, rider: CustomerTrackingRider?): List<QgMapPoint> {
    if (order.trackingClosed()) return emptyList()
    return buildList {
        fun point(lat: Double?, lon: Double?, color: Int) {
            if (lat != null && lon != null) QgMapPoint(lat, lon, color).takeIf { it.valid }?.let { add(it) }
        }
        point(order.pickupLatitude, order.pickupLongitude, 0xFF087556.toInt())
        point(order.deliveryLatitude, order.deliveryLongitude, 0xFFE6002D.toInt())
        if (order.status != "searching_rider") point(rider?.latitude, rider?.longitude, 0xFF1677FF.toInt())
    }
}

/** One final context attempt per detail visit; repeated invalidations must not poll closed locations. */
internal class TrackingContextRefreshGate {
    private val finalAttempts = mutableSetOf<String>()
    fun shouldFetch(order: CustomerOrder): Boolean = !order.trackingClosed() || finalAttempts.add(order.id)
    fun cancelled(order: CustomerOrder) { finalAttempts.remove(order.id) }
}
