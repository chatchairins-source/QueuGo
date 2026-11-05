package com.queuego.ridermaplibreosm

import java.util.PriorityQueue

/**
 * Finds a route only along existing verified directed graph edges.
 *
 * This is a graph-search primitive, NOT a nationwide offline route provider:
 * the caller must supply a real licensed road graph and enforce turn restrictions
 * before this can be used for rider navigation. No invented straight-line edges.
 */
internal fun QueueGoLocalRoadGraph.shortestVerifiedEdgePath(
    startNodeId: String,
    destinationNodeId: String,
    vehicle: QueueGoRoutingVehicle
): List<QueueGoLocalRoadGraphEdge>? {
    if (startNodeId !in nodes || destinationNodeId !in nodes) return null
    if (startNodeId == destinationNodeId) return emptyList()

    data class Candidate(val nodeId: String, val distanceMeters: Double)
    val pending = PriorityQueue<Candidate>(
        compareBy<Candidate> { it.distanceMeters }.thenBy { it.nodeId }
    )
    val best = mutableMapOf(startNodeId to 0.0)
    val previous = mutableMapOf<String, QueueGoLocalRoadGraphEdge>()
    pending.add(Candidate(startNodeId, 0.0))

    while (pending.isNotEmpty()) {
        val current = pending.remove()
        if (current.distanceMeters > best.getValue(current.nodeId)) continue
        if (current.nodeId == destinationNodeId) {
            val reversed = mutableListOf<QueueGoLocalRoadGraphEdge>()
            var node = destinationNodeId
            while (node != startNodeId) {
                val edge = previous[node] ?: return null
                reversed.add(edge)
                node = edge.fromNodeId
            }
            return reversed.asReversed()
        }

        for (edge in outgoingEdges[current.nodeId].orEmpty()) {
            val use = if (edge.toNodeId == destinationNodeId) {
                QueueGoRoadTraversalUse.DESTINATION_ACCESS
            } else {
                QueueGoRoadTraversalUse.THROUGH
            }
            if (!edge.allows(vehicle, use)) continue
            if (!edge.lengthMeters.isFinite() || edge.lengthMeters <= 0.0) continue
            val nextDistance = current.distanceMeters + edge.lengthMeters
            if (nextDistance < best.getOrDefault(edge.toNodeId, Double.POSITIVE_INFINITY)) {
                best[edge.toNodeId] = nextDistance
                previous[edge.toNodeId] = edge
                pending.add(Candidate(edge.toNodeId, nextDistance))
            }
        }
    }
    return null
}
