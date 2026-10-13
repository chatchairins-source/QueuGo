package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationGuidanceTest {
    private fun routeLeg(): NavigationRouteLeg {
        val points = listOf(
            NavigationMapPoint(13.0000, 100.0000),
            NavigationMapPoint(13.0000, 100.0100),
            NavigationMapPoint(13.0100, 100.0100)
        )
        return NavigationRouteLeg(
            segment = NavigationRouteSegment(
                id = "leg-1",
                toStopId = "shop",
                points = points,
                style = NavigationRouteStyle.ACTIVE
            ),
            distanceMeters = 2200.0,
            durationSeconds = 440.0,
            instructions = listOf(
                NavigationRouteInstruction(0, 5, "ถนน A", 1100.0, 220.0),
                NavigationRouteInstruction(1, 1, "ถนน B", 1100.0, 220.0)
            )
        )
    }

    private val stop = NavigationMapStop(
        id = "shop",
        sequence = 1,
        kind = NavigationStopKind.PICKUP,
        point = NavigationMapPoint(13.0100, 100.0100)
    )

    @Test fun progressProducesRemainingDistanceEtaAndNextTurn() {
        val tracker = NavigationProgressTracker(nowMillis = { 10_000L })
        val state = tracker.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.0001, 100.0090),
                bearingDegrees = 90f,
                accuracyMeters = 5f,
                speedMetersPerSecond = 8f
            ),
            routeLeg(),
            stop
        )
        assertNotNull(state.snappedPoint)
        assertTrue(state.distanceRemainingMeters!! in 900.0..1300.0)
        assertTrue(state.etaSeconds!! in 170.0..270.0)
        assertEquals(1, state.nextInstruction!!.turnCode)
        assertFalse(state.offRoute)
        assertFalse(state.shouldReroute)
    }

    @Test fun rerouteNeedsTwoReliableOffRouteSamplesAndCooldown() {
        var now = 20_000L
        val tracker = NavigationProgressTracker(nowMillis = { now })
        val fix = NavigationMapFix(
            point = NavigationMapPoint(12.9985, 100.0050),
            accuracyMeters = 6f,
            speedMetersPerSecond = 6f
        )
        val first = tracker.update(fix, routeLeg(), stop)
        assertTrue(first.offRoute)
        assertFalse(first.shouldReroute)

        val second = tracker.update(fix, routeLeg(), stop)
        assertTrue(second.shouldReroute)

        now += 2_000L
        val third = tracker.update(fix, routeLeg(), stop)
        val fourth = tracker.update(fix, routeLeg(), stop)
        assertFalse(third.shouldReroute)
        assertFalse(fourth.shouldReroute)

        now += 8_000L
        val fifth = tracker.update(fix, routeLeg(), stop)
        val sixth = tracker.update(fix, routeLeg(), stop)
        assertFalse(fifth.shouldReroute)
        assertTrue(sixth.shouldReroute)
    }

    @Test fun weakGpsNeverTriggersReroute() {
        val tracker = NavigationProgressTracker(nowMillis = { 30_000L })
        val fix = NavigationMapFix(
            point = NavigationMapPoint(12.9980, 100.0050),
            accuracyMeters = 120f
        )
        repeat(4) {
            val state = tracker.update(fix, routeLeg(), stop)
            assertTrue(state.weakGps)
            assertFalse(state.shouldReroute)
        }
    }

    @Test fun arrivalRequiresCloseStopAndReliableGps() {
        val tracker = NavigationProgressTracker(nowMillis = { 40_000L })
        val state = tracker.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.00992, 100.00995),
                accuracyMeters = 5f,
                speedMetersPerSecond = 1f
            ),
            routeLeg(),
            stop
        )
        assertTrue(state.arrived)
        assertTrue(state.distanceRemainingMeters!! < 60.0)
    }

    @Test fun turnPhrasesStayThaiAndDeterministic() {
        assertEquals("เลี้ยวซ้าย", navigationTurnPhrase(0))
        assertEquals("เลี้ยวขวา", navigationTurnPhrase(1))
        assertEquals("ถึงจุดหมายแล้ว", navigationTurnPhrase(9))
        assertEquals(null, navigationTurnPhrase(99))
    }
}
