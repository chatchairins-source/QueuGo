package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderNavigationTest {
    @Test fun gpsJitterDoesNotReplayAlreadySpokenThresholds() {
        val gate = RiderNavigationVoiceGate()
        assertTrue(gate.consume("job:shop:0:turn:2:300"))
        assertTrue(gate.consume("job:shop:0:turn:2:100"))
        assertEquals(false, gate.consume("job:shop:0:turn:2:300"))
        assertEquals(false, gate.consume("job:shop:0:turn:2:100"))
        assertTrue(gate.consume("job:shop:1:turn:2:100"))
        assertTrue(gate.consume("job:customer:0:turn:2:100"))
        gate.clear()
        assertTrue(gate.consume("job:shop:0:turn:2:300"))
    }

    @Test fun turnCodeMatchesLongdoContract() {
        assertEquals("เลี้ยวซ้าย", riderTurnPhrase(0))
        assertEquals("เลี้ยวขวา", riderTurnPhrase(1))
        assertEquals("เบี่ยงซ้าย", riderTurnPhrase(2))
        assertEquals("เบี่ยงขวา", riderTurnPhrase(3))
        assertEquals("ตรงไป", riderTurnPhrase(5))
        assertEquals("ขึ้นทางด่วน", riderTurnPhrase(6))
        assertEquals("ถึงจุดหมายแล้ว", riderTurnPhrase(9))
        assertEquals("ขึ้นเรือ", riderTurnPhrase(11))
        assertEquals(null, riderTurnPhrase(4))
    }

    @Test fun mapMatchingSnapsToRoadAndComputesProgress() {
        val plan = RiderRoutePlan(
            targetKey = "job:shop",
            parts = listOf(listOf(
                RiderNavPoint(13.0000, 100.0000),
                RiderNavPoint(13.0000, 100.0100),
                RiderNavPoint(13.0100, 100.0100)
            )),
            instructions = emptyList(),
            distanceMeters = 2200.0,
            intervalSeconds = 300.0
        )
        val match = riderMatchRoute(plan, RiderNavPoint(13.0002, 100.0050))
        assertNotNull(match)
        assertTrue(match!!.offRouteMeters < 30.0)
        assertTrue(match.progressMeters > 400.0)
        assertTrue(match.progressMeters < 700.0)
    }

    @Test fun nextTurnUsesSectionBoundaryNotEveryGpsUpdate() {
        val plan = RiderRoutePlan(
            targetKey = "job:shop",
            parts = listOf(listOf(RiderNavPoint(13.0,100.0), RiderNavPoint(13.01,100.01))),
            instructions = listOf(
                RiderRouteInstruction(0, 4, "ถนนแรก", 500.0, 60.0),
                RiderRouteInstruction(1, 0, "ถนนสอง", 250.0, 30.0),
                RiderRouteInstruction(2, 1, "ถนนสาม", 250.0, 30.0)
            ),
            distanceMeters = 1000.0,
            intervalSeconds = 120.0
        )
        val next = riderNextInstruction(plan, 220.0)
        assertEquals(1, next.first!!.sequence)
        assertEquals(280.0, next.second!!, 0.001)
    }

    @Test fun voiceCueBucketsAndDestinationAreStable() {
        val plan = RiderRoutePlan(
            targetKey = "job:shop",
            parts = listOf(listOf(RiderNavPoint(13.0,100.0), RiderNavPoint(13.01,100.01))),
            instructions = listOf(RiderRouteInstruction(1, 0, "ถนนหลัก", 1000.0, 120.0)),
            distanceMeters = 1000.0,
            intervalSeconds = 120.0
        )
        val state = RiderNavigationState(
            active = true,
            targetKey = "job:shop",
            targetLabel = "ร้านค้า",
            destinationKind = "pickup",
            plan = plan,
            nextInstruction = RiderRouteInstruction(2, 0, "ถนนหลัก", 100.0, 20.0),
            distanceToInstructionMeters = 295.0,
            distanceRemainingMeters = 800.0
        )
        val cue = riderVoiceCue(state)
        assertTrue(cue!!.phrase.startsWith("อีก 300 เมตร เลี้ยวซ้าย"))
        val arrived = riderVoiceCue(state.copy(distanceRemainingMeters = 20.0))
        assertEquals("ถึงจุดหมายแล้ว", arrived!!.phrase)
    }
}
