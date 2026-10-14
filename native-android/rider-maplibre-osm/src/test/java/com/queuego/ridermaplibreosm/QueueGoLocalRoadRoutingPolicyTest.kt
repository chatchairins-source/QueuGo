package com.queuego.ridermaplibreosm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueGoLocalRoadRoutingPolicyTest {
    private fun road(
        verified: Boolean = true,
        access: QueueGoLocalRoadAccess = QueueGoLocalRoadAccess.MOTORCYCLE_AND_CAR,
        oneWay: Boolean = false
    ) = QueueGoLocalRoad(
        id = "road",
        points = listOf(
            QueueGoLocalRoadPoint(15.0000, 103.0000),
            QueueGoLocalRoadPoint(15.0005, 103.0007)
        ),
        source = QueueGoLocalRoadSource.FIELD_SURVEY,
        verified = verified,
        access = access,
        oneWay = oneWay
    )

    @Test
    fun unverifiedAndPrivateRoadsNeverBecomeRoutingEdges() {
        val unverified = queueGoLocalRoadRoutingDecision(
            road = road(verified = false),
            vehicle = QueueGoRoutingVehicle.MOTORCYCLE,
            traversalUse = QueueGoRoadTraversalUse.DESTINATION_ACCESS,
            direction = QueueGoRoadDirection.FORWARD
        )
        val private = queueGoLocalRoadRoutingDecision(
            road = road(access = QueueGoLocalRoadAccess.PRIVATE_RESTRICTED),
            vehicle = QueueGoRoutingVehicle.MOTORCYCLE,
            traversalUse = QueueGoRoadTraversalUse.DESTINATION_ACCESS,
            direction = QueueGoRoadDirection.FORWARD
        )

        assertFalse(unverified.allowed)
        assertEquals(QueueGoLocalRoadRoutingBlockReason.UNVERIFIED, unverified.blockReason)
        assertFalse(private.allowed)
        assertEquals(QueueGoLocalRoadRoutingBlockReason.PRIVATE_RESTRICTED, private.blockReason)
    }

    @Test
    fun motorcycleOnlyRoadRejectsCarButAllowsMotorcycle() {
        val motorcycleRoad = road(access = QueueGoLocalRoadAccess.MOTORCYCLE_ONLY)

        val car = queueGoLocalRoadRoutingDecision(
            motorcycleRoad,
            QueueGoRoutingVehicle.CAR,
            QueueGoRoadTraversalUse.THROUGH,
            QueueGoRoadDirection.FORWARD
        )
        val motorcycle = queueGoLocalRoadRoutingDecision(
            motorcycleRoad,
            QueueGoRoutingVehicle.MOTORCYCLE,
            QueueGoRoadTraversalUse.THROUGH,
            QueueGoRoadDirection.FORWARD
        )

        assertFalse(car.allowed)
        assertEquals(QueueGoLocalRoadRoutingBlockReason.VEHICLE_NOT_ALLOWED, car.blockReason)
        assertTrue(motorcycle.allowed)
    }

    @Test
    fun serviceRoadCannotBeUsedAsThroughShortcut() {
        val serviceRoad = road(access = QueueGoLocalRoadAccess.SERVICE_ACCESS)

        val through = queueGoLocalRoadRoutingDecision(
            serviceRoad,
            QueueGoRoutingVehicle.MOTORCYCLE,
            QueueGoRoadTraversalUse.THROUGH,
            QueueGoRoadDirection.FORWARD
        )
        val destination = queueGoLocalRoadRoutingDecision(
            serviceRoad,
            QueueGoRoutingVehicle.MOTORCYCLE,
            QueueGoRoadTraversalUse.DESTINATION_ACCESS,
            QueueGoRoadDirection.FORWARD
        )

        assertFalse(through.allowed)
        assertEquals(
            QueueGoLocalRoadRoutingBlockReason.DESTINATION_ACCESS_ONLY,
            through.blockReason
        )
        assertTrue(destination.allowed)
    }

    @Test
    fun oneWayRoadRejectsReverseTraversal() {
        val oneWayRoad = road(oneWay = true)
        val reverse = queueGoLocalRoadRoutingDecision(
            oneWayRoad,
            QueueGoRoutingVehicle.MOTORCYCLE,
            QueueGoRoadTraversalUse.THROUGH,
            QueueGoRoadDirection.REVERSE
        )

        assertFalse(reverse.allowed)
        assertEquals(
            QueueGoLocalRoadRoutingBlockReason.ONE_WAY_REVERSE,
            reverse.blockReason
        )
    }
}
