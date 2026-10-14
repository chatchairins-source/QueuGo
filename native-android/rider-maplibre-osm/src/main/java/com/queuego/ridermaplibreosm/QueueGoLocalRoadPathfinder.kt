package com.queuego.ridermaplibreosm

import java.util.PriorityQueue

enum class QueueGoLocalRoadPathStatus {
    FOUND,
    NO_PATH,
    INVALID_ENDPOINT,
    SEARCH_LIMIT_EXCEEDED
}

data class QueueGoLocalRoadPathRequest(
    val startNodeId: String,
    val endNodeId: String,
    val vehicle: QueueGoRoutingVehicle,
    val destinationAccessRoadIds: Set<String> = emptySet(),
    val maxVisitedStates: Int = 50_000
) {
    init {
        require(startNodeId.isNotBlank()) { "Local-road path start node is required" }
        require(endNodeId.isNotBlank()) { "Local-road path end node is required" }
        require(maxVisitedStates in 1..200_000) {
            "Local-road path search limit is outside the accepted range"
        }
        require(destinationAccessRoadIds.none { it.isBlank() }) {
            "Destination-access road ids must not be blank"
        }
    }
}

data class QueueGoLocalRoadPathResult(
    val status: QueueGoLocalRoadPathStatus,
    val edges: List<QueueGoLocalRoadGraphEdge> = emptyList(),
    val totalDistanceMeters: Double = 0.0,
    val visitedStateCount: Int = 0
)

private data class QueueGoPathStateKey(
    val nodeId: String,
    val destinationPhase: Boolean
)

private data class QueueGoPathQueueItem(
    val key: QueueGoPathStateKey,
    val distanceMeters: Double
)

private data class QueueGoPathPredecessor(
    val previous: QueueGoPathStateKey,
    val edge: QueueGoLocalRoadGraphEdge
)

/**
 * Experimental distance-only pathfinder for the isolated QueueGo Local Roads graph.
 *
 * It deliberately:
 * - uses exact graph nodes only (no coordinate snapping);
 * - uses geometry length only (no ETA/speed/traffic scoring);
 * - never touches Production Longdo/Google Maps routing;
 * - treats destination/service access as a terminal phase: once entered, the path cannot
 *   leave the explicitly allowed destination-road set and use it as a through shortcut.
 */
internal fun findQueueGoLocalRoadPath(
    graph: QueueGoLocalRoadGraph,
    request: QueueGoLocalRoadPathRequest
): QueueGoLocalRoadPathResult {
    if (
        request.startNodeId !in graph.nodes ||
        request.endNodeId !in graph.nodes
    ) {
        return QueueGoLocalRoadPathResult(
            status = QueueGoLocalRoadPathStatus.INVALID_ENDPOINT
        )
    }

    if (request.startNodeId == request.endNodeId) {
        return QueueGoLocalRoadPathResult(
            status = QueueGoLocalRoadPathStatus.FOUND,
            totalDistanceMeters = 0.0,
            visitedStateCount = 0
        )
    }

    val start = QueueGoPathStateKey(
        nodeId = request.startNodeId,
        destinationPhase = false
    )
    val best = hashMapOf(start to 0.0)
    val previous = hashMapOf<QueueGoPathStateKey, QueueGoPathPredecessor>()
    val visited = hashSetOf<QueueGoPathStateKey>()
    val queue = PriorityQueue(
        compareBy<QueueGoPathQueueItem> { it.distanceMeters }
            .thenBy { it.key.nodeId }
            .thenBy { it.key.destinationPhase }
    )
    queue += QueueGoPathQueueItem(start, 0.0)

    while (queue.isNotEmpty()) {
        val current = queue.poll()
        val knownDistance = best[current.key] ?: continue
        if (current.distanceMeters > knownDistance + DISTANCE_EPSILON_METERS) continue
        if (!visited.add(current.key)) continue

        if (visited.size > request.maxVisitedStates) {
            return QueueGoLocalRoadPathResult(
                status = QueueGoLocalRoadPathStatus.SEARCH_LIMIT_EXCEEDED,
                visitedStateCount = visited.size
            )
        }

        if (current.key.nodeId == request.endNodeId) {
            return reconstructQueueGoPath(
                end = current.key,
                start = start,
                totalDistanceMeters = current.distanceMeters,
                visitedStateCount = visited.size,
                previous = previous
            )
        }

        val outgoing = graph.outgoingEdges[current.key.nodeId]
            .orEmpty()
            .sortedBy { it.id }

        outgoing.forEach { edge ->
            if (!edge.lengthMeters.isFinite() || edge.lengthMeters <= 0.0) {
                return@forEach
            }

            if (current.key.destinationPhase) {
                if (edge.roadId !in request.destinationAccessRoadIds) {
                    return@forEach
                }
                if (!edge.allows(request.vehicle, QueueGoRoadTraversalUse.DESTINATION_ACCESS)) {
                    return@forEach
                }
                relaxQueueGoPathEdge(
                    current = current,
                    edge = edge,
                    nextDestinationPhase = true,
                    best = best,
                    previous = previous,
                    queue = queue
                )
                return@forEach
            }

            if (edge.allows(request.vehicle, QueueGoRoadTraversalUse.THROUGH)) {
                relaxQueueGoPathEdge(
                    current = current,
                    edge = edge,
                    nextDestinationPhase = false,
                    best = best,
                    previous = previous,
                    queue = queue
                )
            }

            if (
                edge.roadId in request.destinationAccessRoadIds &&
                edge.allows(request.vehicle, QueueGoRoadTraversalUse.DESTINATION_ACCESS)
            ) {
                relaxQueueGoPathEdge(
                    current = current,
                    edge = edge,
                    nextDestinationPhase = true,
                    best = best,
                    previous = previous,
                    queue = queue
                )
            }
        }
    }

    return QueueGoLocalRoadPathResult(
        status = QueueGoLocalRoadPathStatus.NO_PATH,
        visitedStateCount = visited.size
    )
}

private fun relaxQueueGoPathEdge(
    current: QueueGoPathQueueItem,
    edge: QueueGoLocalRoadGraphEdge,
    nextDestinationPhase: Boolean,
    best: MutableMap<QueueGoPathStateKey, Double>,
    previous: MutableMap<QueueGoPathStateKey, QueueGoPathPredecessor>,
    queue: PriorityQueue<QueueGoPathQueueItem>
) {
    val next = QueueGoPathStateKey(
        nodeId = edge.toNodeId,
        destinationPhase = nextDestinationPhase
    )
    val nextDistance = current.distanceMeters + edge.lengthMeters
    val oldDistance = best[next]
    val shouldReplace =
        oldDistance == null ||
            nextDistance + DISTANCE_EPSILON_METERS < oldDistance

    if (!shouldReplace) return

    best[next] = nextDistance
    previous[next] = QueueGoPathPredecessor(
        previous = current.key,
        edge = edge
    )
    queue += QueueGoPathQueueItem(next, nextDistance)
}

private fun reconstructQueueGoPath(
    end: QueueGoPathStateKey,
    start: QueueGoPathStateKey,
    totalDistanceMeters: Double,
    visitedStateCount: Int,
    previous: Map<QueueGoPathStateKey, QueueGoPathPredecessor>
): QueueGoLocalRoadPathResult {
    val reversed = mutableListOf<QueueGoLocalRoadGraphEdge>()
    var cursor = end

    while (cursor != start) {
        val step = previous[cursor]
            ?: return QueueGoLocalRoadPathResult(
                status = QueueGoLocalRoadPathStatus.NO_PATH,
                visitedStateCount = visitedStateCount
            )
        reversed += step.edge
        cursor = step.previous
    }

    reversed.reverse()
    return QueueGoLocalRoadPathResult(
        status = QueueGoLocalRoadPathStatus.FOUND,
        edges = reversed,
        totalDistanceMeters = totalDistanceMeters,
        visitedStateCount = visitedStateCount
    )
}

private const val DISTANCE_EPSILON_METERS = 0.000_001
