package com.queuego.ridermaplibreosm

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class QueueGoLocalRoadGraphNode internal constructor(
    val id: String,
    val point: QueueGoLocalRoadPoint
)

data class QueueGoLocalRoadGraphEdge internal constructor(
    val id: String,
    val roadId: String,
    val fromNodeId: String,
    val toNodeId: String,
    val direction: QueueGoRoadDirection,
    val lengthMeters: Double,
    val source: QueueGoLocalRoadSource,
    val access: QueueGoLocalRoadAccess,
    val motorcycleThroughAllowed: Boolean,
    val motorcycleDestinationAllowed: Boolean,
    val carThroughAllowed: Boolean,
    val carDestinationAllowed: Boolean
) {
    fun allows(
        vehicle: QueueGoRoutingVehicle,
        traversalUse: QueueGoRoadTraversalUse
    ): Boolean = when (vehicle) {
        QueueGoRoutingVehicle.MOTORCYCLE -> when (traversalUse) {
            QueueGoRoadTraversalUse.THROUGH -> motorcycleThroughAllowed
            QueueGoRoadTraversalUse.DESTINATION_ACCESS -> motorcycleDestinationAllowed
        }
        QueueGoRoutingVehicle.CAR -> when (traversalUse) {
            QueueGoRoadTraversalUse.THROUGH -> carThroughAllowed
            QueueGoRoadTraversalUse.DESTINATION_ACCESS -> carDestinationAllowed
        }
    }
}

data class QueueGoLocalRoadGraph internal constructor(
    val nodes: Map<String, QueueGoLocalRoadGraphNode>,
    val edges: List<QueueGoLocalRoadGraphEdge>,
    val outgoingEdges: Map<String, List<QueueGoLocalRoadGraphEdge>>
) {
    fun outgoing(
        nodeId: String,
        vehicle: QueueGoRoutingVehicle,
        traversalUse: QueueGoRoadTraversalUse
    ): List<QueueGoLocalRoadGraphEdge> =
        outgoingEdges[nodeId].orEmpty().filter { it.allows(vehicle, traversalUse) }
}

/**
 * Experimental topology builder only.
 *
 * This deliberately does not snap nearby-but-different coordinates together. False connectivity
 * is worse than a temporarily disconnected alley because parallel roads, walls and divided access
 * lanes can be only a few meters apart. A later field-certified topology step may add explicit
 * junction evidence; this builder only merges exact coordinate identity.
 */
internal fun buildQueueGoLocalRoadGraph(
    roads: List<QueueGoLocalRoad>
): QueueGoLocalRoadGraph {
    val nodes = linkedMapOf<String, QueueGoLocalRoadGraphNode>()
    val edges = mutableListOf<QueueGoLocalRoadGraphEdge>()
    val seenRoadIds = hashSetOf<String>()

    roads.forEach { road ->
        if (!road.verified || road.access == QueueGoLocalRoadAccess.PRIVATE_RESTRICTED) {
            return@forEach
        }
        require(seenRoadIds.add(road.id)) {
            "Duplicate local-road id cannot enter graph: " + road.id
        }

        road.points.zipWithNext().forEachIndexed { segmentIndex, (from, to) ->
            if (from == to) return@forEachIndexed

            val fromNode = nodeFor(nodes, from)
            val toNode = nodeFor(nodes, to)
            val lengthMeters = queueGoLocalRoadDistanceMeters(from, to)
            if (!lengthMeters.isFinite() || lengthMeters <= 0.0) {
                return@forEachIndexed
            }

            buildEdge(
                road = road,
                segmentIndex = segmentIndex,
                fromNode = fromNode,
                toNode = toNode,
                direction = QueueGoRoadDirection.FORWARD,
                lengthMeters = lengthMeters
            )?.let(edges::add)

            if (!road.oneWay) {
                buildEdge(
                    road = road,
                    segmentIndex = segmentIndex,
                    fromNode = toNode,
                    toNode = fromNode,
                    direction = QueueGoRoadDirection.REVERSE,
                    lengthMeters = lengthMeters
                )?.let(edges::add)
            }
        }
    }

    val outgoing = edges
        .groupBy { it.fromNodeId }
        .mapValues { (_, value) -> value.toList() }

    return QueueGoLocalRoadGraph(
        nodes = nodes.toMap(),
        edges = edges.toList(),
        outgoingEdges = outgoing
    )
}

private fun nodeFor(
    nodes: MutableMap<String, QueueGoLocalRoadGraphNode>,
    point: QueueGoLocalRoadPoint
): QueueGoLocalRoadGraphNode {
    val id = queueGoExactNodeId(point)
    return nodes.getOrPut(id) {
        QueueGoLocalRoadGraphNode(id = id, point = point)
    }
}

private fun buildEdge(
    road: QueueGoLocalRoad,
    segmentIndex: Int,
    fromNode: QueueGoLocalRoadGraphNode,
    toNode: QueueGoLocalRoadGraphNode,
    direction: QueueGoRoadDirection,
    lengthMeters: Double
): QueueGoLocalRoadGraphEdge? {
    val motorcycleThrough = queueGoLocalRoadRoutingDecision(
        road,
        QueueGoRoutingVehicle.MOTORCYCLE,
        QueueGoRoadTraversalUse.THROUGH,
        direction
    ).allowed
    val motorcycleDestination = queueGoLocalRoadRoutingDecision(
        road,
        QueueGoRoutingVehicle.MOTORCYCLE,
        QueueGoRoadTraversalUse.DESTINATION_ACCESS,
        direction
    ).allowed
    val carThrough = queueGoLocalRoadRoutingDecision(
        road,
        QueueGoRoutingVehicle.CAR,
        QueueGoRoadTraversalUse.THROUGH,
        direction
    ).allowed
    val carDestination = queueGoLocalRoadRoutingDecision(
        road,
        QueueGoRoutingVehicle.CAR,
        QueueGoRoadTraversalUse.DESTINATION_ACCESS,
        direction
    ).allowed

    if (!motorcycleThrough && !motorcycleDestination && !carThrough && !carDestination) {
        return null
    }

    return QueueGoLocalRoadGraphEdge(
        id = buildString {
            append(road.id)
            append(':')
            append(segmentIndex)
            append(':')
            append(direction.name.lowercase())
        },
        roadId = road.id,
        fromNodeId = fromNode.id,
        toNodeId = toNode.id,
        direction = direction,
        lengthMeters = lengthMeters,
        source = road.source,
        access = road.access,
        motorcycleThroughAllowed = motorcycleThrough,
        motorcycleDestinationAllowed = motorcycleDestination,
        carThroughAllowed = carThrough,
        carDestinationAllowed = carDestination
    )
}

internal fun queueGoExactNodeId(point: QueueGoLocalRoadPoint): String =
    "n:" +
        point.latitude.toBits().toULong().toString(16) +
        ":" +
        point.longitude.toBits().toULong().toString(16)

internal fun queueGoLocalRoadDistanceMeters(
    a: QueueGoLocalRoadPoint,
    b: QueueGoLocalRoadPoint
): Double {
    val earthRadiusMeters = 6_371_008.8
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val deltaLat = lat2 - lat1
    val deltaLon = Math.toRadians(b.longitude - a.longitude)

    val sinLat = sin(deltaLat / 2.0)
    val sinLon = sin(deltaLon / 2.0)
    val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon
    return 2.0 * earthRadiusMeters * asin(sqrt(h.coerceIn(0.0, 1.0)))
}
