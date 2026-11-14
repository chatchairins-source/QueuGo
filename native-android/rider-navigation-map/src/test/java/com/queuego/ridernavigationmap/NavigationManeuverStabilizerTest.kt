package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationManeuverStabilizerTest {
    private fun instruction(sequence: Int, turnCode: Int = 1, road: String = "ถนน") =
        NavigationRouteInstruction(
            sequence = sequence,
            turnCode = turnCode,
            roadName = road,
            sectionDistanceMeters = 100.0,
            sectionDurationSeconds = 20.0
        )

    @Test
    fun firstManeuverIsAcceptedImmediately() {
        val stabilizer = NavigationManeuverStabilizer()

        val result = stabilizer.update(
            routeId = "route:0",
            instruction = instruction(1),
            distanceToInstructionMeters = 120.0
        )

        assertEquals(1, result!!.instruction.sequence)
        assertEquals(120.0, result.distanceToInstructionMeters!!, 0.001)
    }

    @Test
    fun forwardManeuverRequiresTwoConsistentSamples() {
        val stabilizer = NavigationManeuverStabilizer()
        stabilizer.update("route:0", instruction(1), 100.0)

        val firstCandidate = stabilizer.update("route:0", instruction(2), 250.0)
        val confirmed = stabilizer.update("route:0", instruction(2), 240.0)

        assertEquals(1, firstCandidate!!.instruction.sequence)
        assertEquals(2, confirmed!!.instruction.sequence)
        assertEquals(240.0, confirmed.distanceToInstructionMeters!!, 0.001)
    }

    @Test
    fun jitterCannotRegressToPreviousManeuver() {
        val stabilizer = NavigationManeuverStabilizer()
        stabilizer.update("route:0", instruction(2), 80.0)

        val result = stabilizer.update("route:0", instruction(1), 10.0)

        assertEquals(2, result!!.instruction.sequence)
    }

    @Test
    fun weakGpsAndOffRouteFreezeCurrentInstruction() {
        val stabilizer = NavigationManeuverStabilizer()
        stabilizer.update("route:0", instruction(1), 90.0)

        val weak = stabilizer.update(
            routeId = "route:0",
            instruction = instruction(2),
            distanceToInstructionMeters = 70.0,
            weakGps = true
        )
        val offRoute = stabilizer.update(
            routeId = "route:0",
            instruction = instruction(2),
            distanceToInstructionMeters = 60.0,
            offRoute = true
        )

        assertEquals(1, weak!!.instruction.sequence)
        assertEquals(1, offRoute!!.instruction.sequence)
    }

    @Test
    fun rerouteAcceptsNewRouteManeuverImmediately() {
        val stabilizer = NavigationManeuverStabilizer()
        stabilizer.update("route:0", instruction(3), 40.0)

        val result = stabilizer.update(
            routeId = "route:1",
            instruction = instruction(0, turnCode = 5),
            distanceToInstructionMeters = 300.0
        )

        assertEquals("route:1", result!!.routeId)
        assertEquals(0, result.instruction.sequence)
    }

    @Test
    fun nearArrivalDoesNotWaitForSecondSample() {
        val stabilizer = NavigationManeuverStabilizer()
        stabilizer.update("route:0", instruction(2), 80.0)

        val result = stabilizer.update(
            routeId = "route:0",
            instruction = instruction(3, turnCode = 9, road = ""),
            distanceToInstructionMeters = 20.0
        )

        assertEquals(9, result!!.instruction.turnCode)
    }

    @Test
    fun blankRouteIdFailsClosed() {
        val stabilizer = NavigationManeuverStabilizer()
        stabilizer.update("route:0", instruction(1), 100.0)

        assertNull(stabilizer.update("", instruction(2), 50.0))
        assertNull(stabilizer.current())
    }
}
