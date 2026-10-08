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
    val subtotal: Double,
    val customerName: String,
    val note: String,
    val riderArrivedShopAt: String?,
    val riderArrivedCustomerAt: String?,
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


enum class RiderProofMode {
    PICKUP,
    DELIVERY,
}

data class RiderOrderItem(
    val name: String,
    val description: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val imageUrl: String,
)

data class RiderMarketPickup(
    val pickupId: String,
    val sequence: Int,
    val shopName: String,
    val shopAddress: String,
    val latitude: Double?,
    val longitude: Double?,
    val shopAmount: Double,
    val cashPaidAmount: Double,
    val status: String,
) {
    val done: Boolean
        get() = status.uppercase() in setOf("PICKED_UP", "CANCELLED")
}

data class RiderProofState(
    val mode: RiderProofMode,
    val orderId: String,
    val orderNumber: String,
    val items: List<RiderOrderItem> = emptyList(),
    val loading: Boolean = true,
    val photoUri: String? = null,
    val marketPickupId: String? = null,
    val marketPickupLabel: String? = null,
    val marketPickupAmount: Double? = null,
    val submitting: Boolean = false,
    val error: String? = null,
)

data class RiderNavigationRequest(
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val id: Long,
)

data class RiderDashboardState(
    val loading: Boolean = true,
    val profile: RiderProfileState? = null,
    val offer: RiderOffer? = null,
    val activeJob: RiderActiveJob? = null,
    val proof: RiderProofState? = null,
    val marketPickups: List<RiderMarketPickup> = emptyList(),
    val navigationRequest: RiderNavigationRequest? = null,
    val actionBusy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)
