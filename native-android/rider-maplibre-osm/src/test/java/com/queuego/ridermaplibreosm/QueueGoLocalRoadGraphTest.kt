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

    @Test(expected = IllegalArgumentException::class)
    fun unverifiedDuplicateCannotOverrideEligibleRoadInJunctionLookup() {
        val shared = QueueGoLocalRoadPoint(15.001, 103.001)
        buildQueueGoLocalRoadGraph(
            roads = listOf(
                road(id = "shared-id", points = listOf(a, shared)),
                road(id = "shared-id", points = listOf(a, b), verified = false),
                road(id = "connector", points = listOf(shared, b))
            ),
            junctions = listOf(
                QueueGoVerifiedLocalRoadJunction(
                    id = "survey:shared-id",
                    members = listOf(
                        QueueGoLocalRoadJunctionMember("shared-id", 1),
                        QueueGoLocalRoadJunctionMember("connector", 0)
                    ),
                    source = QueueGoLocalRoadSource.FIELD_SURVEY,
                    sourceReference = "survey:shared-id",
                    verifiedAtEpochMillis = 1_700_000_000_000L
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun excludedPrivateDuplicateCannotShadowEligibleRoad() {
        buildQueueGoLocalRoadGraph(
            listOf(
                road(id = "shared-id"),
                road(id = "shared-id", access = QueueGoLocalRoadAccess.PRIVATE_RESTRICTED)
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun signedJunctionCannotTeleportBetweenDistantRoads() {
        val first = QueueGoLocalRoadPoint(15.001, 103.001)
        val distant = QueueGoLocalRoadPoint(15.1, 103.1)
        buildQueueGoLocalRoadGraph(
            roads = listOf(
                road(id = "north", points = listOf(a, first)),
                road(id = "south", points = listOf(distant, b))
            ),
            junctions = listOf(
                QueueGoVerifiedLocalRoadJunction(
                    id = "survey:distant",
                    members = listOf(
                        QueueGoLocalRoadJunctionMember("north", 1),
                        QueueGoLocalRoadJunctionMember("south", 0)
                    ),
                    source = QueueGoLocalRoadSource.FIELD_SURVEY,
                    sourceReference = "survey:distant",
                    verifiedAtEpochMillis = 1_700_000_000_000L
                )
            )
        )
    }

    @Test
    fun signedNearbyJunctionStillConnectsSurveyedRoads() {
        val first = QueueGoLocalRoadPoint(15.001, 103.001)
        val nearby = QueueGoLocalRoadPoint(15.00101, 103.00101)
        val graph = buildQueueGoLocalRoadGraph(
            roads = listOf(
                road(id = "north", points = listOf(a, first)),
                road(id = "south", points = listOf(nearby, b))
            ),
            junctions = listOf(
                QueueGoVerifiedLocalRoadJunction(
                    id = "survey:nearby",
                    members = listOf(
                        QueueGoLocalRoadJunctionMember("north", 1),
                        QueueGoLocalRoadJunctionMember("south", 0)
                    ),
                    source = QueueGoLocalRoadSource.FIELD_SURVEY,
                    sourceReference = "survey:nearby",
                    verifiedAtEpochMillis = 1_700_000_000_000L
                )
            )
        )
        assertEquals(1, graph.nodes.keys.count { it == "j:survey:nearby" })
        assertEquals(4, graph.edges.size)
    }

    @Test
    fun signedJunctionAcrossDatelineKeepsCanonicalPointNearBothSurveyedRoads() {
        val east = QueueGoLocalRoadPoint(0.0, 179.99999)
        val west = QueueGoLocalRoadPoint(0.0, -179.99999)
        val graph = buildQueueGoLocalRoadGraph(
            roads = listOf(
                road(
                    id = "east-road",
                    points = listOf(QueueGoLocalRoadPoint(0.0, 179.999), east)
                ),
                road(
                    id = "west-road",
                    points = listOf(west, QueueGoLocalRoadPoint(0.0, -179.999))
                )
            ),
            junctions = listOf(
                QueueGoVerifiedLocalRoadJunction(
                    id = "field-survey:dateline-junction",
                    members = listOf(
                        QueueGoLocalRoadJunctionMember("east-road", 1),
                        QueueGoLocalRoadJunctionMember("west-road", 0)
                    ),
                    source = QueueGoLocalRoadSource.FIELD_SURVEY,
                    sourceReference = "field-survey:dateline-junction",
                    verifiedAtEpochMillis = 1_700_000_000_000L
                )
            )
        )
        val junction = graph.nodes.getValue("j:field-survey:dateline-junction").point
        assertTrue(kotlin.math.abs(junction.longitude) > 179.9)
        assertTrue(queueGoLocalRoadDistanceMeters(east, junction) < 2.0)
        assertTrue(queueGoLocalRoadDistanceMeters(west, junction) < 2.0)
        assertEquals(4, graph.edges.size)
    }
}
