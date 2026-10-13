package com.queuego.customer

import com.queuego.shared.QgMapPoint
import com.queuego.shared.QgMapRole

fun CustomerOrder.trackingClosed(): Boolean = status.lowercase() in
    setOf("completed", "cancelled", "no_rider_available")

fun customerTrackingPoints(order: CustomerOrder, rider: CustomerTrackingRider?): List<QgMapPoint> {
    if (order.trackingClosed()) return emptyList()
    return buildList {
        fun point(lat: Double?, lon: Double?, color: Int, role: QgMapRole) {
            if (lat != null && lon != null) QgMapPoint(lat, lon, color, role).takeIf { it.valid }?.let { add(it) }
        }
        point(order.pickupLatitude, order.pickupLongitude, 0xFFE6002D.toInt(), QgMapRole.MERCHANT)
        point(order.deliveryLatitude, order.deliveryLongitude, 0xFF007F99.toInt(), QgMapRole.CUSTOMER)
        if (order.status != "searching_rider") point(rider?.latitude, rider?.longitude, 0xFFE6002D.toInt(), QgMapRole.RIDER)
    }
}

/** One final context attempt per detail visit; repeated invalidations must not poll closed locations. */
internal class TrackingContextRefreshGate {
    private val finalAttempts = mutableSetOf<String>()
    fun shouldFetch(order: CustomerOrder): Boolean = !order.trackingClosed() || finalAttempts.add(order.id)
    fun cancelled(order: CustomerOrder) { finalAttempts.remove(order.id) }
}
