package com.queuego.ridermaplibreosm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueGoLocalRoadPathfinderTest {
    private val a = QueueGoLocalRoadPoint(15.0000, 103.0000)
    private val b = QueueGoLocalRoadPoint(15.0000, 103.0010)
    private val c = QueueGoLocalRoadPoint(15.0000, 103.0020)
    private val d = QueueGoLocalRoadPoint(15.0010, 103.0010)
    private val e = QueueGoLocalRoadPoint(15.0020, 103.0010)

    private fun road(
        id: String,
        points: List<QueueGoLocalRoadPoint>,
        access: QueueGoLocalRoadAccess = QueueGoLocalRoadAccess.MOTORCYCLE_AND_CAR,
        oneWay: Boolean = false,
        source: QueueGoLocalRoadSource = QueueGoLocalRoadSource.FIELD_SURVEY
    ) = QueueGoLocalRoad(
        id = id,
        points = points,
        source = source,
        verified = true,
        access = access,
        oneWay = oneWay
    )

    @Test
    fun distanceOnlySearchChoosesShorterVerifiedPath() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(
                road("short-1", listOf(a, b)),
                road("short-2", listOf(b, c)),
                road("long-1", listOf(a, d)),
                road("long-2", listOf(d, e)),
                road("long-3", listOf(e, c))
            )
        )

        val result = findQueueGoLocalRoadPath(
            graph,
            QueueGoLocalRoadPathRequest(
                startNodeId = queueGoExactNodeId(a),
                endNodeId = queueGoExactNodeId(c),
                vehicle = QueueGoRoutingVehicle.MOTORCYCLE
            )
        )

        assertEquals(QueueGoLocalRoadPathStatus.FOUND, result.status)
        assertEquals(listOf("short-1", "short-2"), result.edges.map { it.roadId })
        assertTrue(result.totalDistanceMeters > 0.0)
    }

    @Test
    fun oneWayRoadCannotBeTraversedBackward() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("one-way", listOf(a, b), oneWay = true))
        )

        val result = findQueueGoLocalRoadPath(
            graph,
            QueueGoLocalRoadPathRequest(
                startNodeId = queueGoExactNodeId(b),
                endNodeId = queueGoExactNodeId(a),
                vehicle = QueueGoRoutingVehicle.MOTORCYCLE
            )
        )

        assertEquals(QueueGoLocalRoadPathStatus.NO_PATH, result.status)
    }

    @Test
    fun motorcycleOnlyRoadCannotRouteCar() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(
                road(
                    "moto",
                    listOf(a, b),
                    access = QueueGoLocalRoadAccess.MOTORCYCLE_ONLY
                )
            )
        )

        val result = findQueueGoLocalRoadPath(
            graph,
            QueueGoLocalRoadPathRequest(
                startNodeId = queueGoExactNodeId(a),
                endNodeId = queueGoExactNodeId(b),
                vehicle = QueueGoRoutingVehicle.CAR
            )
        )

        assertEquals(QueueGoLocalRoadPathStatus.NO_PATH, result.status)
    }

    @Test
    fun multiSegmentServiceRoadWorksOnlyAsExplicitDestinationAccess() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(
                road("main", listOf(a, b)),
                road(
                    "service",
                    listOf(b, d, e),
                    access = QueueGoLocalRoadAccess.SERVICE_ACCESS
                )
            )
        )

        val blocked = findQueueGoLocalRoadPath(
            graph,
            QueueGoLocalRoadPathRequest(
                startNodeId = queueGoExactNodeId(a),
                endNodeId = queueGoExactNodeId(e),
                vehicle = QueueGoRoutingVehicle.MOTORCYCLE
            )
        )
        val allowed = findQueueGoLocalRoadPath(
            graph,
            QueueGoLocalRoadPathRequest(
                startNodeId = queueGoExactNodeId(a),
                endNodeId = queueGoExactNodeId(e),
                vehicle = QueueGoRoutingVehicle.MOTORCYCLE,
                destinationAccessRoadIds = setOf("service")
            )
        )

        assertEquals(QueueGoLocalRoadPathStatus.NO_PATH, blocked.status)
        assertEquals(QueueGoLocalRoadPathStatus.FOUND, allowed.status)
        assertEquals(listOf("main", "service", "service"), allowed.edges.map { it.roadId })
    }

    @Test
    fun destinationAccessPhaseCannotExitIntoThroughShortcut() {
        val x = QueueGoLocalRoadPoint(15.0010, 103.0020)
        val graph = buildQueueGoLocalRoadGraph(
            listOf(
                road("main-a", listOf(a, b)),
                road("main-b", listOf(b, c)),
                road(
                    "service",
                    listOf(b, d),
                    access = QueueGoLocalRoadAccess.SERVICE_ACCESS
                ),
                road("shortcut-exit", listOf(d, x, c))
            )
        )

        val result = findQueueGoLocalRoadPath(
            graph,
            QueueGoLocalRoadPathRequest(
                startNodeId = queueGoExactNodeId(a),
                endNodeId = queueGoExactNodeId(c),
                vehicle = QueueGoRoutingVehicle.MOTORCYCLE,
                destinationAccessRoadIds = setOf("service")
            )
        )

        assertEquals(QueueGoLocalRoadPathStatus.FOUND, result.status)
        assertEquals(listOf("main-a", "main-b"), result.edges.map { it.roadId })
    }

    @Test
    fun merchantConfirmedRoadMustBeExplicitDestinationAccess() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(
                road("main", listOf(a, b)),
                road(
                    "merchant-entry",
                    listOf(b, d),
                    source = QueueGoLocalRoadSource.MERCHANT_CONFIRMED
                )
            )
        )

        val blocked = findQueueGoLocalRoadPath(
            graph,
            QueueGoLocalRoadPathRequest(
                startNodeId = queueGoExactNodeId(a),
                endNodeId = queueGoExactNodeId(d),
                vehicle = QueueGoRoutingVehicle.MOTORCYCLE
            )
        )
        val allowed = findQueueGoLocalRoadPath(
            graph,
            QueueGoLocalRoadPathRequest(
                startNodeId = queueGoExactNodeId(a),
                endNodeId = queueGoExactNodeId(d),
                vehicle = QueueGoRoutingVehicle.MOTORCYCLE,
                destinationAccessRoadIds = setOf("merchant-entry")
            )
        )

        assertEquals(QueueGoLocalRoadPathStatus.NO_PATH, blocked.status)
        assertEquals(QueueGoLocalRoadPathStatus.FOUND, allowed.status)
    }

    @Test
    fun invalidEndpointFailsWithoutNearestNodeSnapping() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("main", listOf(a, b)))
        )

        val result = findQueueGoLocalRoadPath(
            graph,
            QueueGoLocalRoadPathRequest(
                startNodeId = "n:not-a-real-node",
                endNodeId = queueGoExactNodeId(b),
                vehicle = QueueGoRoutingVehicle.MOTORCYCLE
            )
        )

        assertEquals(QueueGoLocalRoadPathStatus.INVALID_ENDPOINT, result.status)
    }

    @Test
    fun searchLimitFailsClosedInsteadOfRunningUnbounded() {
        val p1 = QueueGoLocalRoadPoint(15.0000, 103.0000)
        val p2 = QueueGoLocalRoadPoint(15.0000, 103.0010)
        val p3 = QueueGoLocalRoadPoint(15.0000, 103.0020)
        val p4 = QueueGoLocalRoadPoint(15.0000, 103.0030)
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("chain", listOf(p1, p2, p3, p4)))
        )

        val result = findQueueGoLocalRoadPath(
            graph,
            QueueGoLocalRoadPathRequest(
                startNodeId = queueGoExactNodeId(p1),
                endNodeId = queueGoExactNodeId(p4),
                vehicle = QueueGoRoutingVehicle.MOTORCYCLE,
                maxVisitedStates = 1
            )
        )

        assertEquals(QueueGoLocalRoadPathStatus.SEARCH_LIMIT_EXCEEDED, result.status)
    }
}
