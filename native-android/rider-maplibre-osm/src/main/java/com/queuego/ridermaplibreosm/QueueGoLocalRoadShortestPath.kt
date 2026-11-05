package com.queuego.ridermaplibreosm

import java.util.PriorityQueue

/**
 * Edge-state Dijkstra over verified directed road edges.
 *
 * forbiddenTurns must be derived from the licensed and verified graph's turn
 * restrictions. An empty set is appropriate ONLY when the input feed explicitly
 * certifies there are no restrictions; missing restriction data is not certified.
 * This is not a nationwide offline provider or a source of road geometry.
 */
internal fun QueueGoLocalRoadGraph.shortestVerifiedEdgePath(
    startNodeId: String,
    destinationNodeId: String,
    vehicle: QueueGoRoutingVehicle,
    forbiddenTurns: Set<Pair<String, String>>
): List<QueueGoLocalRoadGraphEdge>? {
    // A typo or stale ID in signed turn restrictions must never silently
    // disable a safety rule. Validate against the exact graph revision.
    val edgeById = edges.associateBy { it.id }
    require(edgeById.size == edges.size) { "Duplicate directed edge IDs" }
    require(forbiddenTurns.all { (incoming, outgoing) ->
        val from = edgeById[incoming]
        val to = edgeById[outgoing]
        from != null && to != null && from.toNodeId == to.fromNodeId
    }) { "Turn restriction references unknown or disconnected graph edges" }
    if (startNodeId !in nodes || destinationNodeId !in nodes) return null
    if (startNodeId == destinationNodeId) return emptyList()

    data class State(val nodeId: String, val incomingEdgeId: String?)
    data class Candidate(val state: State, val distanceMeters: Double)
    val start = State(startNodeId, null)
    val pending = PriorityQueue<Candidate>(
        compareBy<Candidate> { it.distanceMeters }
            .thenBy { it.state.nodeId }
            .thenBy { it.state.incomingEdgeId ?: "" }
    )
    val best = mutableMapOf(start to 0.0)
    val previous = mutableMapOf<State, Pair<State, QueueGoLocalRoadGraphEdge>>()
    pending.add(Candidate(start, 0.0))

    while (pending.isNotEmpty()) {
        val current = pending.remove()
        if (current.distanceMeters > best.getValue(current.state)) continue
        if (current.state.nodeId == destinationNodeId) {
            val reversed = mutableListOf<QueueGoLocalRoadGraphEdge>()
            var state = current.state
            while (state != start) {
                val (parent, edge) = previous[state] ?: return null
                reversed.add(edge)
                state = parent
            }
            return reversed.asReversed()
        }

        for (edge in outgoingEdges[current.state.nodeId].orEmpty()) {
            val incoming = current.state.incomingEdgeId
            if (incoming != null && (incoming to edge.id) in forbiddenTurns) continue
            val use = if (edge.toNodeId == destinationNodeId) {
                QueueGoRoadTraversalUse.DESTINATION_ACCESS
            } else {
                QueueGoRoadTraversalUse.THROUGH
            }
            if (!edge.allows(vehicle, use)) continue
            if (!edge.lengthMeters.isFinite() || edge.lengthMeters <= 0.0) continue
            val nextState = State(edge.toNodeId, edge.id)
            val nextDistance = current.distanceMeters + edge.lengthMeters
            if (!nextDistance.isFinite()) continue
            if (nextDistance < best.getOrDefault(nextState, Double.POSITIVE_INFINITY)) {
                best[nextState] = nextDistance
                previous[nextState] = current.state to edge
                pending.add(Candidate(nextState, nextDistance))
            }
        }
    }
    return null
}
