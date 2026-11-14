package com.queuego.ridermaplibreosm

enum class QueueGoRoutingVehicle {
    MOTORCYCLE,
    CAR
}

enum class QueueGoRoadTraversalUse {
    THROUGH,
    DESTINATION_ACCESS
}

enum class QueueGoRoadDirection {
    FORWARD,
    REVERSE
}

enum class QueueGoLocalRoadRoutingBlockReason {
    UNVERIFIED,
    PRIVATE_RESTRICTED,
    VEHICLE_NOT_ALLOWED,
    DESTINATION_ACCESS_ONLY,
    ONE_WAY_REVERSE
}

data class QueueGoLocalRoadRoutingDecision(
    val allowed: Boolean,
    val blockReason: QueueGoLocalRoadRoutingBlockReason? = null
)

/**
 * Eligibility only. This does not choose a route, score roads, or mutate Production navigation.
 *
 * FORWARD follows the order of QueueGoLocalRoad.points. A verified one-way road is therefore
 * never traversable in REVERSE. SERVICE_ACCESS is last-mile destination access only and may not
 * be used as a shortcut through a property/service lane.
 */
fun queueGoLocalRoadRoutingDecision(
    road: QueueGoLocalRoad,
    vehicle: QueueGoRoutingVehicle,
    traversalUse: QueueGoRoadTraversalUse,
    direction: QueueGoRoadDirection
): QueueGoLocalRoadRoutingDecision {
    if (!road.verified) {
        return QueueGoLocalRoadRoutingDecision(
            allowed = false,
            blockReason = QueueGoLocalRoadRoutingBlockReason.UNVERIFIED
        )
    }

    if (road.access == QueueGoLocalRoadAccess.PRIVATE_RESTRICTED) {
        return QueueGoLocalRoadRoutingDecision(
            allowed = false,
            blockReason = QueueGoLocalRoadRoutingBlockReason.PRIVATE_RESTRICTED
        )
    }

    if (
        road.access == QueueGoLocalRoadAccess.MOTORCYCLE_ONLY &&
        vehicle != QueueGoRoutingVehicle.MOTORCYCLE
    ) {
        return QueueGoLocalRoadRoutingDecision(
            allowed = false,
            blockReason = QueueGoLocalRoadRoutingBlockReason.VEHICLE_NOT_ALLOWED
        )
    }

    if (
        road.source == QueueGoLocalRoadSource.MERCHANT_CONFIRMED &&
        traversalUse != QueueGoRoadTraversalUse.DESTINATION_ACCESS
    ) {
        return QueueGoLocalRoadRoutingDecision(
            allowed = false,
            blockReason = QueueGoLocalRoadRoutingBlockReason.DESTINATION_ACCESS_ONLY
        )
    }

    if (
        road.access == QueueGoLocalRoadAccess.SERVICE_ACCESS &&
        traversalUse != QueueGoRoadTraversalUse.DESTINATION_ACCESS
    ) {
        return QueueGoLocalRoadRoutingDecision(
            allowed = false,
            blockReason = QueueGoLocalRoadRoutingBlockReason.DESTINATION_ACCESS_ONLY
        )
    }

    if (road.oneWay && direction == QueueGoRoadDirection.REVERSE) {
        return QueueGoLocalRoadRoutingDecision(
            allowed = false,
            blockReason = QueueGoLocalRoadRoutingBlockReason.ONE_WAY_REVERSE
        )
    }

    return QueueGoLocalRoadRoutingDecision(allowed = true)
}
