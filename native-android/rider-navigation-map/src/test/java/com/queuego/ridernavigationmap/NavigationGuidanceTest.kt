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

    @Test fun rerouteNeedsThreeReliableOffRouteSamplesAndTwentySecondCooldown() {
        var now = 20_000L
        val tracker = NavigationProgressTracker(nowMillis = { now })
        val fix = NavigationMapFix(
            point = NavigationMapPoint(12.9985, 100.0050),
            accuracyMeters = 6f,
            speedMetersPerSecond = 6f
        )
        val first = tracker.update(fix, routeLeg(), stop)
        val second = tracker.update(fix, routeLeg(), stop)
        val third = tracker.update(fix, routeLeg(), stop)
        assertTrue(first.offRoute)
        assertFalse(first.shouldReroute)
        assertFalse(second.shouldReroute)
        assertTrue(third.shouldReroute)

        now += 2_000L
        val fourth = tracker.update(fix, routeLeg(), stop)
        val fifth = tracker.update(fix, routeLeg(), stop)
        assertFalse(fourth.shouldReroute)
        assertFalse(fifth.shouldReroute)

        now += 20_000L
        val sixth = tracker.update(fix, routeLeg(), stop)
        val seventh = tracker.update(fix, routeLeg(), stop)
        // Off-route evidence remained continuous during cooldown, so the third reliable sample
        // after the reset may reroute as soon as the 20-second cooldown has elapsed.
        assertTrue(sixth.shouldReroute)
        assertFalse(seventh.shouldReroute)
    }

    @Test fun authoritativeProgressUsesHybridContinuityAtOverlappingRouteGeometry() {
        val loop = NavigationRouteLeg(
            segment = NavigationRouteSegment(
                id = "loop",
                toStopId = "shop",
                points = listOf(
                    NavigationMapPoint(15.0000, 103.0000),
                    NavigationMapPoint(15.0000, 103.0100),
                    NavigationMapPoint(15.0100, 103.0100),
                    NavigationMapPoint(15.0100, 103.0000),
                    NavigationMapPoint(15.0000, 103.0000)
                ),
                style = NavigationRouteStyle.ACTIVE
            ),
            distanceMeters = 4_400.0,
            durationSeconds = 800.0
        )
        val loopStop = stop.copy(point = NavigationMapPoint(15.0000, 103.0000))
        val tracker = NavigationProgressTracker(nowMillis = { 30_000L })

        val late = tracker.update(
            NavigationMapFix(
                point = NavigationMapPoint(15.0030, 103.0000),
                bearingDegrees = 180f,
                accuracyMeters = 6f,
                speedMetersPerSecond = 7f,
                elapsedRealtimeNanos = 1_000_000_000L
            ),
            loop,
            loopStop
        )
        val repeatedEndpoint = tracker.update(
            NavigationMapFix(
                point = NavigationMapPoint(15.0000, 103.0000),
                accuracyMeters = 6f,
                speedMetersPerSecond = 1f,
                elapsedRealtimeNanos = 2_000_000_000L
            ),
            loop,
            loopStop
        )

        assertTrue(repeatedEndpoint.progressMeters!! >= late.progressMeters!! - 35.0)
        assertTrue(repeatedEndpoint.distanceRemainingMeters!! < 20.0)
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
