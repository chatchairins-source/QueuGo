package com.queuetech.queuego.rider

data class RiderCoordinate(
    val latitude: Double,
    val longitude: Double,
)

data class RiderProfileState(
    val id: String,
    val userId: String,
    val status: String,
    val metadataJson: String,
    val online: Boolean,
    val available: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val vehicleType: String,
    val vehiclePlate: String,
)

data class RiderOffer(
    val orderId: String,
    val orderNumber: String,
    val shopName: String,
    val pickupLatitude: Double?,
    val pickupLongitude: Double?,
    val deliveryAddress: String,
    val deliveryLatitude: Double?,
    val deliveryLongitude: Double?,
    val distanceKm: Double?,
    val deliveryFee: Double,
    val expiresAtMillis: Long,
    val attempt: Int,
    val marketOrderId: String?,
    val fulfillmentVertical: String,
)

data class RiderActiveJob(
    val orderId: String,
    val orderNumber: String,
    val status: String,
    val shopName: String,
    val pickupAddress: String,
    val pickupLatitude: Double?,
    val pickupLongitude: Double?,
    val deliveryAddress: String,
    val deliveryLatitude: Double?,
    val deliveryLongitude: Double?,
    val totalAmount: Double,
    val deliveryFee: Double,
    val marketOrderId: String?,
    val fulfillmentVertical: String,
) {
    val isPickupPhase: Boolean
        get() = status in setOf(
            "rider_assigned",
            "assigned",
            "preparing",
            "ready",
        )
}

data class RiderDashboardState(
    val loading: Boolean = true,
    val profile: RiderProfileState? = null,
    val offer: RiderOffer? = null,
    val activeJob: RiderActiveJob? = null,
    val actionBusy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)
