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

    fun exactNodeId(point: QueueGoLocalRoadPoint): String? {
        val id = queueGoExactNodeId(point)
        return id.takeIf { it in graph.nodes }
    }

    fun findPath(request: QueueGoLocalRoadPathRequest): QueueGoLocalRoadPathResult =
        findQueueGoLocalRoadPath(graph, request)
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
