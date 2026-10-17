package com.queuego.ridermaplibreosm

/**
 * Trust token for reviewed Local Roads geometry.
 *
 * The constructor is internal on purpose: callers outside this module can obtain one only by
 * asking QueueGoLocalRoadSignedDelivery to load/refresh a snapshot that has already passed
 * envelope bounds, SHA-256, trusted-key signature, feed validation and revision checks.
 */
class QueueGoTrustedLocalRoadSnapshot internal constructor(
    val revision: Long,
    internal val roads: List<QueueGoLocalRoad>,
    internal val junctions: List<QueueGoVerifiedLocalRoadJunction>
) {
    val roadCount: Int
        get() = roads.size

    val junctionCount: Int
        get() = junctions.size
}

data class QueueGoLocalRoadSnap(
    val nodeId: String,
    val point: QueueGoLocalRoadPoint,
    val distanceMeters: Double
)

data class QueueGoTrustedLocalRoadRoute(
    val status: QueueGoLocalRoadPathStatus,
    val points: List<QueueGoLocalRoadPoint> = emptyList(),
    val totalDistanceMeters: Double = 0.0,
    val startSnapMeters: Double? = null,
    val endSnapMeters: Double? = null,
    val visitedStateCount: Int = 0
)

/**
 * Public field-test surface over an internally-built verified graph.
 *
 * No nearest-node snapping is exposed. A caller must supply an exact Local Roads point and gets
 * a node id only when that exact coordinate is present in the trusted graph.
 */
class QueueGoTrustedLocalRoadGraphSession internal constructor(
    val revision: Long,
    private val graph: QueueGoLocalRoadGraph
) {
    val nodeCount: Int
        get() = graph.nodes.size

    val edgeCount: Int
        get() = graph.edges.size

    fun exactNodeId(point: QueueGoLocalRoadPoint): String? =
        graph.exactPointNodeIds[queueGoExactNodeId(point)]

    fun nearestNode(
        point: QueueGoLocalRoadPoint,
        maxSnapMeters: Double
    ): QueueGoLocalRoadSnap? {
        require(maxSnapMeters.isFinite() && maxSnapMeters in 1.0..250.0) {
            "Local-road snap radius must be between 1 and 250 metres"
        }
        var best: QueueGoLocalRoadSnap? = null
        graph.nodes.values.forEach { node ->
            val distance = queueGoLocalRoadDistanceMeters(point, node.point)
            if (!distance.isFinite() || distance > maxSnapMeters) return@forEach
            val current = best
            if (
                current == null ||
                distance < current.distanceMeters ||
                (distance == current.distanceMeters && node.id < current.nodeId)
            ) {
                best = QueueGoLocalRoadSnap(
                    nodeId = node.id,
                    point = node.point,
                    distanceMeters = distance
                )
            }
        }
        return best
    }

    fun findPath(request: QueueGoLocalRoadPathRequest): QueueGoLocalRoadPathResult =
        findQueueGoLocalRoadPath(graph, request)

    fun routeBetween(
        start: QueueGoLocalRoadPoint,
        end: QueueGoLocalRoadPoint,
        vehicle: QueueGoRoutingVehicle,
        destinationAccessRoadIds: Set<String> = emptySet(),
        maxSnapMeters: Double = 80.0,
        maxVisitedStates: Int = 50_000
    ): QueueGoTrustedLocalRoadRoute {
        val startSnap = nearestNode(start, maxSnapMeters)
            ?: return QueueGoTrustedLocalRoadRoute(
                status = QueueGoLocalRoadPathStatus.INVALID_ENDPOINT
            )
        val endSnap = nearestNode(end, maxSnapMeters)
            ?: return QueueGoTrustedLocalRoadRoute(
                status = QueueGoLocalRoadPathStatus.INVALID_ENDPOINT,
                startSnapMeters = startSnap.distanceMeters
            )
        val path = findQueueGoLocalRoadPath(
            graph = graph,
            request = QueueGoLocalRoadPathRequest(
                startNodeId = startSnap.nodeId,
                endNodeId = endSnap.nodeId,
                vehicle = vehicle,
                destinationAccessRoadIds = destinationAccessRoadIds,
                maxVisitedStates = maxVisitedStates
            )
        )
        if (path.status != QueueGoLocalRoadPathStatus.FOUND) {
            return QueueGoTrustedLocalRoadRoute(
                status = path.status,
                startSnapMeters = startSnap.distanceMeters,
                endSnapMeters = endSnap.distanceMeters,
                visitedStateCount = path.visitedStateCount
            )
        }

        val points = if (path.edges.isEmpty()) {
            listOf(startSnap.point)
        } else {
            buildList {
                graph.nodes[path.edges.first().fromNodeId]?.point?.let(::add)
                path.edges.forEach { edge ->
                    graph.nodes[edge.toNodeId]?.point?.let { point ->
                        if (lastOrNull() != point) add(point)
                    }
                }
            }
        }
        require(points.isNotEmpty()) { "Found local-road path has no graph geometry" }

        return QueueGoTrustedLocalRoadRoute(
            status = path.status,
            points = points,
            totalDistanceMeters = path.totalDistanceMeters,
            startSnapMeters = startSnap.distanceMeters,
            endSnapMeters = endSnap.distanceMeters,
            visitedStateCount = path.visitedStateCount
        )
    }
}

fun QueueGoLocalRoadSignedDelivery.loadTrustedSnapshot(): QueueGoTrustedLocalRoadSnapshot? =
    loadCached().toTrustedSnapshotOrNull()

suspend fun QueueGoLocalRoadSignedDelivery.refreshTrustedSnapshot(): QueueGoTrustedLocalRoadSnapshot? =
    refresh().toTrustedSnapshotOrNull()

fun QueueGoTrustedLocalRoadSnapshot.buildGraphSession(): QueueGoTrustedLocalRoadGraphSession =
    QueueGoTrustedLocalRoadGraphSession(
        revision = revision,
        graph = buildQueueGoLocalRoadGraph(
            roads = roads,
            junctions = junctions
        )
    )

private fun QueueGoLocalRoadDeliveryResult.toTrustedSnapshotOrNull():
    QueueGoTrustedLocalRoadSnapshot? {
    if (status == QueueGoLocalRoadDeliveryStatus.NO_SNAPSHOT) return null
    val trustedRevision = requireNotNull(revision) {
        "Trusted Local Roads result must carry a revision"
    }
    return QueueGoTrustedLocalRoadSnapshot(
        revision = trustedRevision,
        roads = roads.toList(),
        junctions = junctions.toList()
    )
}
