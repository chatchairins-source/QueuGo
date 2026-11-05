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
        access: QueueGoLocalRoadAccess = QueueGoLocalRoadAccess.MOTORCYCLE_AND_CAR,
        source: QueueGoLocalRoadSource = QueueGoLocalRoadSource.FIELD_SURVEY
    ) = QueueGoLocalRoad(
        id = id,
        points = points,
        source = source,
        verified = true,
        access = access,
        oneWay = oneWay
    )

    @Test fun multipleVerifiedTurnRestrictionsStillApplyAfterSelectiveIndexing() {
        val d = QueueGoLocalRoadPoint(15.001, 103.001)
        val graph = buildQueueGoLocalRoadGraph(
            listOf(
                road("approach", listOf(a, b), oneWay = true),
                road("direct", listOf(b, c), oneWay = true),
                road("detour1", listOf(b, d), oneWay = true),
                road("detour2", listOf(d, c), oneWay = true)
            )
        )
        val restrictions = setOf(
            "approach:0:forward" to "direct:0:forward",
            "detour1:0:forward" to "detour2:0:forward"
        )
        assertNull(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c),
            QueueGoRoutingVehicle.MOTORCYCLE, restrictions
        ))
    }

    @Test fun optionalPoliciesEmptyStillFindsVerifiedRoute() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("verified", listOf(a, b), oneWay = true))
        )
        assertEquals(listOf("verified"), graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(b),
            QueueGoRoutingVehicle.MOTORCYCLE, emptySet()
        )!!.map { it.roadId })
    }

    @Test(expected = IllegalArgumentException::class)
    fun staleTurnRestrictionEdgeIdCannotSilentlyDisableRule() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("approach", listOf(a, b), oneWay = true),
                road("exit", listOf(b, c), oneWay = true))
        )
        graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c),
            QueueGoRoutingVehicle.MOTORCYCLE,
            setOf("stale-road:0:forward" to "exit:0:forward")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun disconnectedRestrictionEdgePairCannotMasqueradeAsRealTurn() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("approach", listOf(a, b), oneWay = true),
                road("remote", listOf(c, QueueGoLocalRoadPoint(16.0, 104.0)), oneWay = true))
        )
        graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c),
            QueueGoRoutingVehicle.MOTORCYCLE,
            setOf("approach:0:forward" to "remote:0:forward")
        )
    }

    @Test fun shortestPathUsesDistanceRatherThanFewestEdges() {
        val far = QueueGoLocalRoadPoint(16.0, 104.0)
        val graph = buildQueueGoLocalRoadGraph(
            listOf(
                road("long-direct", listOf(a, far, c), oneWay = true),
                road("short-1", listOf(a, b), oneWay = true),
                road("short-2", listOf(b, c), oneWay = true)
            )
        )
        val path = graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c),
            QueueGoRoutingVehicle.MOTORCYCLE, emptySet()
        )
        assertEquals(listOf("short-1", "short-2"), path!!.map { it.roadId })
    }

    @Test fun cycleDoesNotPreventFindingDestination() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(
                road("forward", listOf(a, b), oneWay = true),
                road("cycle", listOf(b, a), oneWay = true),
                road("exit", listOf(b, c), oneWay = true)
            )
        )
        val path = graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c),
            QueueGoRoutingVehicle.MOTORCYCLE, emptySet()
        )
        assertEquals(listOf("forward", "exit"), path!!.map { it.roadId })
    }

    @Test fun forbiddenTurnAppliesOnlyToSpecifiedIncomingEdge() {
        val d = QueueGoLocalRoadPoint(15.001, 103.001)
        val graph = buildQueueGoLocalRoadGraph(
            listOf(
                road("approach", listOf(a, b), oneWay = true),
                road("alternate1", listOf(a, d), oneWay = true),
                road("alternate2", listOf(d, b), oneWay = true),
                road("exit", listOf(b, c), oneWay = true)
            )
        )
        val path = graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c),
            QueueGoRoutingVehicle.MOTORCYCLE,
            setOf("approach:0:forward" to "exit:0:forward")
        )
        assertEquals(
            listOf("alternate1", "alternate2", "exit"),
            path!!.map { it.roadId }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroVisitedStateBudgetCannotBypassSafetyBound() {
        val graph = buildQueueGoLocalRoadGraph(listOf(road("road", listOf(a, b))))
        graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(b),
            QueueGoRoutingVehicle.MOTORCYCLE, emptySet(), maxVisitedStates = 0
        )
    }

    @Test fun supersededHeapEntryDoesNotConsumeActiveQueueBudget() {
        val x = QueueGoLocalRoadPoint(15.0, 102.9999)
        val d = QueueGoLocalRoadPoint(15.0009, 103.0)
        val side = QueueGoLocalRoadPoint(15.001, 103.001)
        val graph = buildQueueGoLocalRoadGraph(
            listOf(
                road("to-x", listOf(a, x), oneWay = true),
                road("x-to-b", listOf(x, b), oneWay = true),
                road("to-d", listOf(a, d), oneWay = true),
                road("d-to-b", listOf(d, b), oneWay = true),
                road("side", listOf(b, side), oneWay = true),
                road("finish", listOf(b, c), oneWay = true)
            )
        )
        val route = graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c),
            QueueGoRoutingVehicle.MOTORCYCLE, emptySet(), maxQueuedStates = 2
        )
        assertEquals(listOf("to-d", "d-to-b", "finish"), route!!.map { it.roadId })
        // Heap compaction must preserve the same optimal route as an
        // unconstrained frontier; it may discard stale entries, not live paths.
        val unboundedFrontierRoute = graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c),
            QueueGoRoutingVehicle.MOTORCYCLE, emptySet(), maxQueuedStates = 100
        )
        assertEquals(unboundedFrontierRoute!!.map { it.id }, route.map { it.id })
    }

    @Test fun denseFrontierExceedingQueueBudgetFailsClosed() {
        val north = QueueGoLocalRoadPoint(15.001, 103.001)
        val south = QueueGoLocalRoadPoint(14.999, 103.001)
        val graph = buildQueueGoLocalRoadGraph(
            listOf(
                road("north", listOf(a, north), oneWay = true),
                road("south", listOf(a, south), oneWay = true),
                road("destination", listOf(north, c), oneWay = true)
            )
        )
        assertNull(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c),
            QueueGoRoutingVehicle.MOTORCYCLE, emptySet(), maxQueuedStates = 1
        ))
        assertEquals(listOf("north", "destination"), graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c),
            QueueGoRoutingVehicle.MOTORCYCLE, emptySet(), maxQueuedStates = 4
        )!!.map { it.roadId })
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroPendingQueueBudgetIsRejected() {
        val graph = buildQueueGoLocalRoadGraph(listOf(road("test", listOf(a, b))))
        graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(b),
            QueueGoRoutingVehicle.MOTORCYCLE, emptySet(), maxQueuedStates = 0
        )
    }

    @Test fun verifiedTurnRestrictionForcesLongerLegalPath() {
        val d = QueueGoLocalRoadPoint(15.001, 103.001)
        val graph = buildQueueGoLocalRoadGraph(
            listOf(
                road("approach", listOf(a, b), oneWay = true),
                road("direct", listOf(b, c), oneWay = true),
                road("detour1", listOf(b, d), oneWay = true),
                road("detour2", listOf(d, c), oneWay = true)
            )
        )
        val restriction = setOf("approach:0:forward" to "direct:0:forward")
        val path = graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c),
            QueueGoRoutingVehicle.MOTORCYCLE, restriction
        )
        assertEquals(listOf("approach", "detour1", "detour2"), path!!.map { it.roadId })
    }

    @Test fun prohibitedTurnWithoutAlternativeFailsClosed() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(
                road("approach", listOf(a, b), oneWay = true),
                road("exit", listOf(b, c), oneWay = true)
            )
        )
        assertNull(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c),
            QueueGoRoutingVehicle.MOTORCYCLE,
            setOf("approach:0:forward" to "exit:0:forward")
        ))
    }

    @Test fun destinationWhitelistNeverOverridesMotorcycleOnlyAccessForCars() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("bike", listOf(a, b),
                access = QueueGoLocalRoadAccess.MOTORCYCLE_ONLY))
        )
        assertNull(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(b),
            QueueGoRoutingVehicle.CAR, emptySet(),
            destinationAccessRoadIds = setOf("bike")
        ))
    }

    @Test fun destinationWhitelistNeverOverridesOneWayDirection() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("one-way", listOf(a, b), oneWay = true,
                access = QueueGoLocalRoadAccess.SERVICE_ACCESS))
        )
        assertNull(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(b), queueGoExactNodeId(a),
            QueueGoRoutingVehicle.MOTORCYCLE, emptySet(),
            destinationAccessRoadIds = setOf("one-way")
        ))
    }

    @Test fun directedPathUsesOnlyExistingRoadEdges() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("first", listOf(a, b), oneWay = true),
                road("second", listOf(b, c), oneWay = true))
        )
        val path = graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c), QueueGoRoutingVehicle.MOTORCYCLE, emptySet()
        )
        assertEquals(listOf("first", "second"), path!!.map { it.roadId })
        assertNull(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(c), queueGoExactNodeId(a), QueueGoRoutingVehicle.MOTORCYCLE, emptySet()
        ))
    }

    @Test fun disconnectedRoadsCannotProduceInventedConnector() {
        val far = QueueGoLocalRoadPoint(16.0, 104.0)
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("first", listOf(a, b)), road("remote", listOf(far, c)))
        )
        assertNull(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c), QueueGoRoutingVehicle.MOTORCYCLE, emptySet()
        ))
    }

    @Test fun motorcycleOnlyRoadDoesNotRouteCars() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("bike", listOf(a, b), access = QueueGoLocalRoadAccess.MOTORCYCLE_ONLY))
        )
        assertEquals(1, graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(b), QueueGoRoutingVehicle.MOTORCYCLE, emptySet()
        )!!.size)
        assertNull(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(b), QueueGoRoutingVehicle.CAR, emptySet()
        ))
    }

    @Test fun serviceAccessAllowedOnlyForFinalDestination() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("service", listOf(a, b), access = QueueGoLocalRoadAccess.SERVICE_ACCESS),
                road("continuation", listOf(b, c)))
        )
        assertEquals(1, graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(b), QueueGoRoutingVehicle.MOTORCYCLE, emptySet(), destinationAccessRoadIds = setOf("service")
        )!!.size)
        assertNull(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c), QueueGoRoutingVehicle.MOTORCYCLE, emptySet(), destinationAccessRoadIds = setOf("service")
        ))
        assertTrue(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(a), QueueGoRoutingVehicle.MOTORCYCLE, emptySet()
        )!!.isEmpty())
    }

    @Test fun verifiedMultiSegmentServiceRoadCanReachExplicitDestination() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("service", listOf(a, b, c),
                access = QueueGoLocalRoadAccess.SERVICE_ACCESS))
        )
        val start = queueGoExactNodeId(a)
        val destination = queueGoExactNodeId(c)
        assertNull(graph.shortestVerifiedEdgePath(
            start, destination, QueueGoRoutingVehicle.MOTORCYCLE, emptySet()
        ))
        val allowed = graph.shortestVerifiedEdgePath(
            start, destination, QueueGoRoutingVehicle.MOTORCYCLE, emptySet(),
            destinationAccessRoadIds = setOf("service")
        )
        assertEquals(listOf("service", "service"), allowed!!.map { it.roadId })
    }

    @Test fun destinationOnlyRoadCannotBecomeShortcutToAnotherPublicRoad() {
        val d = QueueGoLocalRoadPoint(15.003, 103.0)
        val graph = buildQueueGoLocalRoadGraph(
            listOf(
                road("service", listOf(a, b, c),
                    access = QueueGoLocalRoadAccess.SERVICE_ACCESS),
                road("public", listOf(c, d), oneWay = true)
            )
        )
        assertNull(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(d),
            QueueGoRoutingVehicle.MOTORCYCLE, emptySet(),
            destinationAccessRoadIds = setOf("service")
        ))
    }

    @Test fun destinationOnlyRouteCannotSwitchToAnotherServiceRoad() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(
                road("service-a", listOf(a, b),
                    access = QueueGoLocalRoadAccess.SERVICE_ACCESS),
                road("service-b", listOf(b, c),
                    access = QueueGoLocalRoadAccess.SERVICE_ACCESS)
            )
        )
        assertNull(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c),
            QueueGoRoutingVehicle.MOTORCYCLE, emptySet(),
            destinationAccessRoadIds = setOf("service-a", "service-b")
        ))
    }

    @Test fun merchantConfirmedMultiSegmentRoadRequiresExplicitDestinationApproval() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("merchant-path", listOf(a, b, c),
                source = QueueGoLocalRoadSource.MERCHANT_CONFIRMED))
        )
        assertNull(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c),
            QueueGoRoutingVehicle.CAR, emptySet()
        ))
        val route = graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c),
            QueueGoRoutingVehicle.CAR, emptySet(),
            destinationAccessRoadIds = setOf("merchant-path")
        )
        assertEquals(2, route!!.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun staleDestinationWhitelistCannotSilentlyAuthorizeAccess() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("service", listOf(a, b),
                access = QueueGoLocalRoadAccess.SERVICE_ACCESS))
        )
        graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(b),
            QueueGoRoutingVehicle.MOTORCYCLE, emptySet(),
            destinationAccessRoadIds = setOf("unknown-signed-road")
        )
    }

    @Test fun boundedDijkstraFailsClosedRatherThanExhaustingDeviceSearch() {
        val graph = buildQueueGoLocalRoadGraph(
            listOf(road("long", listOf(a, b, c), oneWay = true))
        )
        assertNull(graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c),
            QueueGoRoutingVehicle.MOTORCYCLE, emptySet(), maxVisitedStates = 2
        ))
        assertEquals(2, graph.shortestVerifiedEdgePath(
            queueGoExactNodeId(a), queueGoExactNodeId(c),
            QueueGoRoutingVehicle.MOTORCYCLE, emptySet(), maxVisitedStates = 3
        )!!.size)
    }
}
