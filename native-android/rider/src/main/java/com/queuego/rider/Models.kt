package com.queuego.rider

import com.queuego.shared.nativeOrderNumber

data class QueueGoUser(
    val id: String,
    val name: String,
    val role: String,
    val status: String,
    val phone: String? = null
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

data class RiderVerificationDetails(
    val shopName: String?,
    val shopLogo: String?,
    val customerName: String?,
    val customerPhone: String?,
    val note: String?
)

data class RiderShopDisplay(
    val shopName: String?,
    val shopLogo: String?
)

internal fun cleanRiderOrderNote(note: String?): String? = note?.lineSequence()
    ?.filter { it.isNotEmpty() && !it.startsWith("__QT_ORDER_STATUS__=") }
    ?.joinToString("\n")?.trim()?.takeIf { it.isNotEmpty() }

data class MarketPickup(
    val pickupId: String,
    val status: String,
    val shopName: String,
    val shopAddress: String?,
    val shopAmount: Double,
    val latitude: Double?,
    val longitude: Double?
) {
    val done: Boolean
        get() = status.uppercase() == "PICKED_UP" || status.uppercase() == "CANCELLED"
}

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
    val offerExpiresAt: String? = null,
    val arrivedShopAt: String?,
    val arrivedCustomerAt: String?,
    val shopCash: Double? = null,
    val customerCash: Double? = null
) {
    val numberLabel: String
        get() = nativeOrderNumber(orderNumber)

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

data class RiderLaundryInvite(
    val inviteId: String,
    val hubId: String,
    val hubName: String,
    val shopName: String,
    val status: String,
    val createdAt: String?
)

data class RiderLaundryJob(
    val jobId: String,
    val laundryOrderId: String,
    val orderNumber: String?,
    val leg: String,
    val jobStatus: String,
    val orderStatus: String?,
    val hubId: String?,
    val hubName: String?,
    val shopName: String?,
    val serviceName: String?,
    val actualQuantity: Double?,
    val pricingType: String?,
    val jobFee: Double,
    val fromAddress: String?,
    val fromLatitude: Double?,
    val fromLongitude: Double?,
    val toAddress: String?,
    val toLatitude: Double?,
    val toLongitude: Double?,
    val customerAmount: Double?,
    val actualKg: Double? = null,
    val createdAt: String? = null
) {
    val numberLabel: String
        get() = nativeOrderNumber(orderNumber)

    val isCollected: Boolean
        get() = jobStatus == "collected"

    val navigationTarget: Pair<Double, Double>?
        get() {
            val lat = if (isCollected) toLatitude else fromLatitude
            val lng = if (isCollected) toLongitude else fromLongitude
            return if (lat != null && lng != null) lat to lng else null
        }

    val routeLabel: String
        get() = if (leg == "pickup") "รับผ้าไปส่งร้าน" else "รับผ้าจากร้านไปคืนลูกค้า"
}

data class RiderLaundryState(
    val featureEnabled: Boolean = false,
    val modeEnabled: Boolean = false,
    val invites: List<RiderLaundryInvite> = emptyList(),
    val activeJob: RiderLaundryJob? = null,
    val pool: List<RiderLaundryJob> = emptyList()
)

internal fun RiderLaundryJob.asMapJob(): RiderJob = RiderJob(
    id = "laundry:" + jobId,
    orderNumber = orderNumber,
    status = if (isCollected) "in_progress" else "rider_assigned",
    pickupAddress = fromAddress,
    pickupLat = fromLatitude,
    pickupLng = fromLongitude,
    deliveryAddress = toAddress,
    deliveryLat = toLatitude,
    deliveryLng = toLongitude,
    deliveryFee = jobFee,
    marketOrderId = null,
    arrivedShopAt = null,
    arrivedCustomerAt = null,
    shopCash = null,
    customerCash = customerAmount
)

data class RiderBundleOrder(
    val bundleId: String,
    val job: RiderJob,
    val bundleSequence: Int,
    val dropRank: Int,
    val shopId: String?,
    val shopName: String,
    val originalDeliveryFee: Double,
    val customerSavings: Double,
    val riderExtraFee: Double,
    val detourKm: Double,
    val addedMinutes: Int,
    val routeSequence: String?
)

data class RiderBundleCandidate(
    val orderId: String,
    val orderNumber: String?,
    val shopId: String?,
    val shopName: String,
    val pickupAddress: String?,
    val deliveryAddress: String?,
    val pickupLat: Double?,
    val pickupLng: Double?,
    val deliveryLat: Double?,
    val deliveryLng: Double?,
    val detourKm: Double,
    val addedMinutes: Int,
    val standaloneDeliveryFee: Double,
    val bundledDeliveryFee: Double,
    val customerSavings: Double,
    val riderExtraFee: Double,
    val routeSequence: String?
) {
    val numberLabel: String
        get() = nativeOrderNumber(orderNumber)
}

data class RiderRouteBundleState(
    val activeOrders: List<RiderBundleOrder> = emptyList(),
    val candidates: List<RiderBundleCandidate> = emptyList()
) {
    fun currentOrder(): RiderBundleOrder? {
        val live = activeOrders.filter { it.job.status !in setOf("cancelled", "completed") }
        if (live.isEmpty()) return null
        live.firstOrNull { it.job.status == "in_progress" }?.let { return it }
        live.filter { it.job.status in setOf("rider_assigned", "preparing", "ready", "assigned") }
            .minByOrNull { it.bundleSequence }?.let { return it }
        return live.filter { it.job.status == "picked_up" }.minByOrNull { it.dropRank } ?: live.first()
    }
}

data class RiderProfileInfo(
    val profileId: String,
    val riderName: String?,
    val phone: String?,
    val vehicleType: String?,
    val vehiclePlate: String?,
    val vehicleStatus: String?,
    val vehicleVerifiedAt: String?
)

data class RiderCashLedgerEntry(
    val orderId: String,
    val orderNumber: String?,
    val status: String,
    val deliveryFee: Double,
    val cashPaidMerchant: Double,
    val cashCollectedCustomer: Double,
    val createdAt: String?
) {
    val numberLabel: String
        get() = nativeOrderNumber(orderNumber)
}

data class RiderSnapshot(
    val online: Boolean,
    val activeJob: RiderJob?,
    val offeredJob: RiderJob?,
    val marketPickups: List<MarketPickup> = emptyList(),
    val riderProfileId: String? = null,
    val laundry: RiderLaundryState = RiderLaundryState(),
    val bundle: RiderRouteBundleState = RiderRouteBundleState(),
    val profile: RiderProfileInfo? = null
)


data class RiderPeriodSummary(
    val days: Int,
    val jobs: Int,
    val income: Double,
    val onlineHours: Double,
    val incomePerHour: Double?
)


data class RiderHistoryOrder(
    val id: String,
    val number: String,
    val pickupAddress: String?,
    val deliveryAddress: String?,
    val deliveryFee: Double,
    val completedAt: String?
)
