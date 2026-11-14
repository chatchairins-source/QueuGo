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
    // Sorting once makes repeated GPS/reroute snaps O(log N + local candidates)
    // rather than scanning all verified nodes on every location update. Latitude
    // filtering is intentionally conservative; the haversine check below remains
    // the final authority and handles longitudes across the dateline correctly.
    private val nodesByLatitude = graph.nodes.values.sortedWith(
        compareBy<QueueGoLocalRoadGraphNode> { it.point.latitude }.thenBy { it.id }
    )

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
        // At least 110 km of great-circle latitude distance per degree:
        // 110_000 is a conservative lower bound for our 6,371,008.8m sphere.
        // Never use a narrow longitude window here: at high latitudes or across
        // +/-180 degrees, that would silently discard a legitimate nearby road.
        val latitudeRadius = maxSnapMeters / 110_000.0
        val firstLatitude = point.latitude - latitudeRadius
        val lastLatitude = point.latitude + latitudeRadius
        var low = 0
        var high = nodesByLatitude.size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (nodesByLatitude[middle].point.latitude < firstLatitude) {
                low = middle + 1
            } else {
                high = middle
            }
        }

        var best: QueueGoLocalRoadSnap? = null
        var index = low
        while (index < nodesByLatitude.size) {
            val node = nodesByLatitude[index++]
            if (node.point.latitude > lastLatitude) break
            val distance = queueGoLocalRoadDistanceMeters(point, node.point)
            if (!distance.isFinite() || distance > maxSnapMeters) continue
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
        // Two distinct requested coordinates can snap to the same verified
        // graph node (including the two sides of a signed junction). The
        // distance-only search then returns FOUND with zero edges. That is
        // arrival proximity, not a renderable navigation route: never certify
        // a one-point route or invent a connecting straight line.
        if (startSnap.nodeId == endSnap.nodeId) {
            return QueueGoTrustedLocalRoadRoute(
                status = QueueGoLocalRoadPathStatus.NO_PATH,
                startSnapMeters = startSnap.distanceMeters,
                endSnapMeters = endSnap.distanceMeters
            )
        }
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
