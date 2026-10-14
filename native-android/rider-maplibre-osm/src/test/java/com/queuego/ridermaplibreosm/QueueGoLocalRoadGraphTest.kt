package com.queuego.ridermaplibreosm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueGoLocalRoadGraphTest {
    private val a = QueueGoLocalRoadPoint(15.0000, 103.0000)
    private val b = QueueGoLocalRoadPoint(15.0005, 103.0007)

    private fun road(
        id: String = "road",
        points: List<QueueGoLocalRoadPoint> = listOf(a, b),
        verified: Boolean = true,
        access: QueueGoLocalRoadAccess = QueueGoLocalRoadAccess.MOTORCYCLE_AND_CAR,
        oneWay: Boolean = false,
        source: QueueGoLocalRoadSource = QueueGoLocalRoadSource.FIELD_SURVEY
    ) = QueueGoLocalRoad(
        id = id,
        points = points,
        source = source,
        verified = verified,
        access = access,
        oneWay = oneWay
    )

    @Test
    fun ordinaryVerifiedRoadCreatesTwoDirectedEdges() {
        val graph = buildQueueGoLocalRoadGraph(listOf(road()))

        assertEquals(2, graph.nodes.size)
        assertEquals(2, graph.edges.size)
        assertTrue(graph.edges.all { it.lengthMeters > 0.0 })
        assertTrue(
            graph.edges.all {
                it.allows(
                    QueueGoRoutingVehicle.MOTORCYCLE,
                    QueueGoRoadTraversalUse.THROUGH
                )
            }
        )
    }

    @Test
    fun oneWayRoadCreatesNoReverseGraphEdge() {
        val graph = buildQueueGoLocalRoadGraph(listOf(road(oneWay = true)))

        assertEquals(1, graph.edges.size)
        assertEquals(QueueGoRoadDirection.FORWARD, graph.edges.single().direction)
    }

    @Test
    fun unverifiedAndPrivateRoadsNeverEnterGraph() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(
                road(id = "candidate", verified = false),
                road(id = "private", access = QueueGoLocalRoadAccess.PRIVATE_RESTRICTED)
            )
        )

        assertTrue(graph.nodes.isEmpty())
        assertTrue(graph.edges.isEmpty())
    }

    @Test
    fun serviceRoadEdgesRemainDestinationOnly() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road(access = QueueGoLocalRoadAccess.SERVICE_ACCESS))
        )
        val edge = graph.edges.first()

        assertFalse(
            edge.allows(
                QueueGoRoutingVehicle.MOTORCYCLE,
                QueueGoRoadTraversalUse.THROUGH
            )
        )
        assertTrue(
            edge.allows(
                QueueGoRoutingVehicle.MOTORCYCLE,
                QueueGoRoadTraversalUse.DESTINATION_ACCESS
            )
        )
    }

    @Test
    fun motorcycleOnlyRoadNeverAllowsCar() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road(access = QueueGoLocalRoadAccess.MOTORCYCLE_ONLY))
        )

        assertTrue(
            graph.edges.all {
                it.allows(
                    QueueGoRoutingVehicle.MOTORCYCLE,
                    QueueGoRoadTraversalUse.THROUGH
                )
            }
        )
        assertTrue(
            graph.edges.none {
                it.allows(
                    QueueGoRoutingVehicle.CAR,
                    QueueGoRoadTraversalUse.THROUGH
                )
            }
        )
    }

    @Test
    fun exactSharedEndpointMergesButNearbyPointDoesNotAutoSnap() {
        val shared = QueueGoLocalRoadPoint(15.0010, 103.0010)
        val nearby = QueueGoLocalRoadPoint(15.0010001, 103.0010001)
        val graph = buildQueueGoLocalRoadGraph(
            listOf(
                road(id = "a", points = listOf(a, shared)),
                road(id = "b", points = listOf(shared, b)),
                road(id = "c", points = listOf(nearby, QueueGoLocalRoadPoint(15.0020, 103.0020)))
            )
        )

        assertEquals(5, graph.nodes.size)
        val sharedId = queueGoExactNodeId(shared)
        assertEquals(2, graph.outgoingEdges[sharedId].orEmpty().size)
        assertTrue(queueGoExactNodeId(shared) != queueGoExactNodeId(nearby))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateRoadIdsFailClosedInsteadOfMergingGeometry() {
        buildQueueGoLocalRoadGraph(
            listOf(
                road(id = "same"),
                road(id = "same")
            )
        )
    }
}
