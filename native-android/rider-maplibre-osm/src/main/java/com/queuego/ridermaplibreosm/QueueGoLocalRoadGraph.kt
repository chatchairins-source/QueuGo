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
    val outgoingEdges: Map<String, List<QueueGoLocalRoadGraphEdge>>,
    internal val exactPointNodeIds: Map<String, String>
) {
    fun outgoing(
        nodeId: String,
        vehicle: QueueGoRoutingVehicle,
        traversalUse: QueueGoRoadTraversalUse
    ): List<QueueGoLocalRoadGraphEdge> =
        outgoingEdges[nodeId].orEmpty().filter { it.allows(vehicle, traversalUse) }
}

private data class QueueGoRoadPointKey(
    val roadId: String,
    val pointIndex: Int
)

private data class QueueGoGraphJunctionIndex(
    val memberNodeIds: Map<QueueGoRoadPointKey, String>,
    val canonicalPoints: Map<String, QueueGoLocalRoadPoint>
)

/**
 * Experimental topology builder only.
 *
 * Nearby coordinates never connect automatically. They may share a graph node only when the
 * signed/reviewed feed carries explicit verified junction evidence naming the exact road point
 * indices. This keeps false connectivity fail-closed while still allowing reviewed village
 * alleys/intersections whose surveyed coordinates are a few metres apart.
 */
internal fun buildQueueGoLocalRoadGraph(
    roads: List<QueueGoLocalRoad>,
    junctions: List<QueueGoVerifiedLocalRoadJunction> = emptyList()
): QueueGoLocalRoadGraph {
    val roadById = roads.associateBy { it.id }
    val junctionIndex = buildQueueGoGraphJunctionIndex(
        roadsById = roadById,
        junctions = junctions
    )
    val nodes = linkedMapOf<String, QueueGoLocalRoadGraphNode>()
    val exactPointNodeIds = linkedMapOf<String, String>()
    val edges = mutableListOf<QueueGoLocalRoadGraphEdge>()
    val seenRoadIds = hashSetOf<String>()

    roads.forEach { road ->
        if (!road.verified || road.access == QueueGoLocalRoadAccess.PRIVATE_RESTRICTED) {
            return@forEach
        }
        require(seenRoadIds.add(road.id)) {
            "Duplicate local-road id cannot enter graph: " + road.id
        }

        for (segmentIndex in 0 until road.points.lastIndex) {
            val from = road.points[segmentIndex]
            val to = road.points[segmentIndex + 1]
            if (from == to) continue

            val fromNode = nodeFor(
                nodes = nodes,
                exactPointNodeIds = exactPointNodeIds,
                roadId = road.id,
                pointIndex = segmentIndex,
                point = from,
                junctionIndex = junctionIndex
            )
            val toNode = nodeFor(
                nodes = nodes,
                exactPointNodeIds = exactPointNodeIds,
                roadId = road.id,
                pointIndex = segmentIndex + 1,
                point = to,
                junctionIndex = junctionIndex
            )
            val lengthMeters = queueGoLocalRoadDistanceMeters(from, to)
            if (!lengthMeters.isFinite() || lengthMeters <= 0.0) continue

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
        outgoingEdges = outgoing,
        exactPointNodeIds = exactPointNodeIds.toMap()
    )
}

private fun buildQueueGoGraphJunctionIndex(
    roadsById: Map<String, QueueGoLocalRoad>,
    junctions: List<QueueGoVerifiedLocalRoadJunction>
): QueueGoGraphJunctionIndex {
    val memberNodeIds = linkedMapOf<QueueGoRoadPointKey, String>()
    val canonicalPoints = linkedMapOf<String, QueueGoLocalRoadPoint>()
    val seenJunctionIds = hashSetOf<String>()

    junctions.forEach { junction ->
        require(seenJunctionIds.add(junction.id)) {
            "Duplicate local-road junction id cannot enter graph: " + junction.id
        }
        val nodeId = "j:" + junction.id
        val memberPoints = junction.members.map { member ->
            val road = roadsById[member.roadId]
                ?: throw IllegalArgumentException(
                    "Local-road junction references unknown graph road: " + member.roadId
                )
            require(road.verified && road.access != QueueGoLocalRoadAccess.PRIVATE_RESTRICTED) {
                "Local-road junction references an ineligible graph road: " + member.roadId
            }
            require(member.pointIndex in road.points.indices) {
                "Local-road junction point index is invalid: " + member.roadId
            }
            val key = QueueGoRoadPointKey(member.roadId, member.pointIndex)
            require(memberNodeIds.putIfAbsent(key, nodeId) == null) {
                "Local-road point cannot belong to multiple graph junctions: " +
                    member.roadId + ":" + member.pointIndex
            }
            road.points[member.pointIndex]
        }
        require(memberPoints.isNotEmpty()) { "Local-road junction has no graph members" }
        canonicalPoints[nodeId] = QueueGoLocalRoadPoint(
            latitude = memberPoints.sumOf { it.latitude } / memberPoints.size.toDouble(),
            longitude = memberPoints.sumOf { it.longitude } / memberPoints.size.toDouble()
        )
    }

    return QueueGoGraphJunctionIndex(
        memberNodeIds = memberNodeIds.toMap(),
        canonicalPoints = canonicalPoints.toMap()
    )
}

private fun nodeFor(
    nodes: MutableMap<String, QueueGoLocalRoadGraphNode>,
    exactPointNodeIds: MutableMap<String, String>,
    roadId: String,
    pointIndex: Int,
    point: QueueGoLocalRoadPoint,
    junctionIndex: QueueGoGraphJunctionIndex
): QueueGoLocalRoadGraphNode {
    val memberKey = QueueGoRoadPointKey(roadId, pointIndex)
    val junctionNodeId = junctionIndex.memberNodeIds[memberKey]
    val id = junctionNodeId ?: queueGoExactNodeId(point)
    val canonicalPoint = junctionNodeId
        ?.let(junctionIndex.canonicalPoints::get)
        ?: point

    val exactPointId = queueGoExactNodeId(point)
    val existingMapping = exactPointNodeIds.putIfAbsent(exactPointId, id)
    require(existingMapping == null || existingMapping == id) {
        "Exact Local Roads coordinate is assigned to conflicting verified junctions"
    }

    return nodes.getOrPut(id) {
        QueueGoLocalRoadGraphNode(
            id = id,
            point = canonicalPoint
        )
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
    if (fromNode.id == toNode.id) return null

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
