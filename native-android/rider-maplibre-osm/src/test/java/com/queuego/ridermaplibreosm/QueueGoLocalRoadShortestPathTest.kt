package com.queuego.ridermaplibreosm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueGoLocalRoadShortestPathTest {
    private val a = QueueGoLocalRoadPoint(15.0, 103.0)
    private val b = QueueGoLocalRoadPoint(15.001, 103.0)
    private val c = QueueGoLocalRoadPoint(15.002, 103.0)

    private fun road(
        id: String,
        points: List<QueueGoLocalRoadPoint>,
        oneWay: Boolean = false,
        access: QueueGoLocalRoadAccess = QueueGoLocalRoadAccess.MOTORCYCLE_AND_CAR
    ) = QueueGoLocalRoad(
        id = id,
        points = points,
        source = QueueGoLocalRoadSource.FIELD_SURVEY,
        verified = true,
        access = access,
        oneWay = oneWay
    )

    @Test fun directedPathUsesOnlyExistingRoadEdges() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("first", listOf(a, b), oneWay = true),
                road("second", listOf(b, c), oneWay = true))
        )
        val path = graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c), QueueGoRoutingVehicle.MOTORCYCLE
        )
        assertEquals(listOf("first", "second"), path!!.map { it.roadId })
        assertNull(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(c), queueGoExactNodeId(a), QueueGoRoutingVehicle.MOTORCYCLE
        ))
    }

    @Test fun disconnectedRoadsCannotProduceInventedConnector() {
        val far = QueueGoLocalRoadPoint(16.0, 104.0)
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("first", listOf(a, b)), road("remote", listOf(far, c)))
        )
        assertNull(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c), QueueGoRoutingVehicle.MOTORCYCLE
        ))
    }

    @Test fun motorcycleOnlyRoadDoesNotRouteCars() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("bike", listOf(a, b), access = QueueGoLocalRoadAccess.MOTORCYCLE_ONLY))
        )
        assertEquals(1, graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(b), QueueGoRoutingVehicle.MOTORCYCLE
        )!!.size)
        assertNull(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(b), QueueGoRoutingVehicle.CAR
        ))
    }

    @Test fun serviceAccessAllowedOnlyForFinalDestination() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("service", listOf(a, b), access = QueueGoLocalRoadAccess.SERVICE_ACCESS),
                road("continuation", listOf(b, c)))
        )
        assertEquals(1, graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(b), QueueGoRoutingVehicle.MOTORCYCLE
        )!!.size)
        assertNull(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c), QueueGoRoutingVehicle.MOTORCYCLE
        ))
        assertTrue(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(a), QueueGoRoutingVehicle.MOTORCYCLE
        )!!.isEmpty())
    }
}
