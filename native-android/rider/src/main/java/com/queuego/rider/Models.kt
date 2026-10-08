package com.queuego.rider

data class QueueGoUser(
    val id: String,
    val name: String,
    val role: String,
    val status: String
)

data class QueueGoSession(
    val authUserId: String,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long,
    val sessionId: String
)

data class QueueGoAuth(
    val session: QueueGoSession,
    val user: QueueGoUser
)

data class RiderItem(
    val name: String,
    val description: String?,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val imageUrl: String?
)

data class RiderJob(
    val id: String,
    val orderNumber: String?,
    val status: String,
    val pickupAddress: String?,
    val pickupLat: Double?,
    val pickupLng: Double?,
    val deliveryAddress: String?,
    val deliveryLat: Double?,
    val deliveryLng: Double?,
    val deliveryFee: Double?,
    val marketOrderId: String?,
    val arrivedShopAt: String?,
    val arrivedCustomerAt: String?
) {
    val numberLabel: String
        get() = orderNumber?.let { if (it.startsWith("QT-")) it else "QT-" + it } ?: "QT-----"

    val isDelivering: Boolean
        get() = status == "picked_up" || status == "in_progress"

    val navigationTarget: Pair<Double, Double>?
        get() {
            val lat = if (isDelivering) deliveryLat else pickupLat
            val lng = if (isDelivering) deliveryLng else pickupLng
            return if (lat != null && lng != null) lat to lng else null
        }

    val shopArrived: Boolean
        get() = !arrivedShopAt.isNullOrBlank()

    val customerArrived: Boolean
        get() = !arrivedCustomerAt.isNullOrBlank()
}

data class RiderSnapshot(
    val online: Boolean,
    val activeJob: RiderJob?,
    val offeredJob: RiderJob?
)
