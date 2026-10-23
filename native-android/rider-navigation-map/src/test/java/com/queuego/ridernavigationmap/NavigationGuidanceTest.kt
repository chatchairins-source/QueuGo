package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test fun rerouteNeedsThreeSecondsContinuouslyOffRouteAndTwentySecondCooldown() {
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

        now = 22_999L
        val almost = tracker.update(fix, routeLeg(), stop)
        assertFalse(almost.shouldReroute)

        now = 23_000L
        val sustained = tracker.update(fix, routeLeg(), stop)
        assertTrue(sustained.shouldReroute)

        now = 24_000L
        val cooldownStart = tracker.update(fix, routeLeg(), stop)
        assertFalse(cooldownStart.shouldReroute)

        now = 27_000L
        val sustainedDuringCooldown = tracker.update(fix, routeLeg(), stop)
        assertFalse(sustainedDuringCooldown.shouldReroute)

        now = 43_000L
        val cooldownElapsed = tracker.update(fix, routeLeg(), stop)
        assertTrue(cooldownElapsed.shouldReroute)
    }

    @Test fun missedTurnReroutesImmediatelyOnFirstReliableMovingFix() {
        var now = 50_000L
        val tracker = NavigationProgressTracker(nowMillis = { now })

        val approaching = tracker.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.0000, 100.0096),
                bearingDegrees = 90f,
                accuracyMeters = 5f,
                speedMetersPerSecond = 8f
            ),
            routeLeg(),
            stop
        )
        assertFalse(approaching.offRoute)
        assertFalse(approaching.shouldReroute)
        assertTrue((approaching.distanceToInstructionMeters ?: Double.MAX_VALUE) <= 90.0)

        now += 1_000L
        val missedTurn = tracker.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.0000, 100.01055),
                bearingDegrees = 90f,
                accuracyMeters = 5f,
                speedMetersPerSecond = 8f
            ),
            routeLeg(),
            stop
        )
        assertTrue(missedTurn.offRoute)
        assertTrue(missedTurn.shouldReroute)
    }

    @Test fun passedTurnReroutesBeforeGenericThirtyMeterThreshold() {
        var now = 55_000L
        val tracker = NavigationProgressTracker(nowMillis = { now })

        val approaching = tracker.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.0000, 100.0096),
                bearingDegrees = 90f,
                accuracyMeters = 5f,
                speedMetersPerSecond = 8f
            ),
            routeLeg(),
            stop
        )
        assertFalse(approaching.shouldReroute)

        now += 1_000L
        val justPastTurn = tracker.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.0000, 100.01015),
                bearingDegrees = 90f,
                accuracyMeters = 5f,
                speedMetersPerSecond = 8f
            ),
            routeLeg(),
            stop
        )
        assertTrue(justPastTurn.offRouteMeters!! >= 8.0)
        assertTrue(justPastTurn.offRouteMeters!! < 30.0)
        assertFalse(justPastTurn.offRoute)
        assertTrue(justPastTurn.shouldReroute)
    }

    @Test fun stoppedNearMissedTurnStillUsesNormalOffRouteDebounce() {
        var now = 60_000L
        val tracker = NavigationProgressTracker(nowMillis = { now })

        tracker.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.0000, 100.0096),
                bearingDegrees = 90f,
                accuracyMeters = 5f,
                speedMetersPerSecond = 8f
            ),
            routeLeg(),
            stop
        )

        now += 1_000L
        val stopped = tracker.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.0000, 100.01055),
                bearingDegrees = 90f,
                accuracyMeters = 5f,
                speedMetersPerSecond = 0f
            ),
            routeLeg(),
            stop
        )
        assertTrue(stopped.offRoute)
        assertFalse(stopped.shouldReroute)

        now += 3_000L
        val sustained = tracker.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.0000, 100.01055),
                bearingDegrees = 90f,
                accuracyMeters = 5f,
                speedMetersPerSecond = 0f
            ),
            routeLeg(),
            stop
        )
        assertTrue(sustained.shouldReroute)
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

    @Test fun speedLimitUsesOnlyExplicitOsmIntervalOnReliableRouteProgress() {
        val leg = routeLeg().copy(
            speedLimitIntervals = listOf(
                NavigationSpeedLimitInterval(
                    startGeometryMeters = 0.0,
                    endGeometryMeters = 2_000.0,
                    maxspeedKph = 80.0,
                    maxspeedRaw = "80",
                    source = "queuego:road_class_estimate_v1",
                    osmWayId = 88L
                ),
                NavigationSpeedLimitInterval(
                    startGeometryMeters = 0.0,
                    endGeometryMeters = 1_100.0,
                    maxspeedKph = 50.0,
                    maxspeedRaw = "50",
                    source = "osm:maxspeed",
                    osmWayId = 101L
                )
            )
        )
        val tracker = NavigationProgressTracker(nowMillis = { 30_000L })
        val onRoute = tracker.update(
            NavigationMapFix(
                point = NavigationMapPoint(13.0000, 100.0050),
                bearingDegrees = 90f,
                accuracyMeters = 5f,
                speedMetersPerSecond = 7f
            ),
            leg,
            stop
        )
        assertEquals(50.0, onRoute.speedLimitKph!!, 0.001)

        val offRoute = tracker.update(
            NavigationMapFix(
                point = NavigationMapPoint(12.9980, 100.0050),
                bearingDegrees = 90f,
                accuracyMeters = 5f,
                speedMetersPerSecond = 7f
            ),
            leg,
            stop
        )
        assertTrue(offRoute.offRoute)
        assertNull(offRoute.speedLimitKph)
    }

    @Test fun weakGpsNeverTriggersReroute() {
        val tracker = NavigationProgressTracker(nowMillis = { 30_000L })
        val fix = NavigationMapFix(
            point = NavigationMapPoint(12.9980, 100.0050),
            accuracyMeters = 40f
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
        assertEquals("กลับรถ", navigationTurnPhrase(4))
        assertEquals("ถึงจุดหมายแล้ว", navigationTurnPhrase(9))
        assertEquals(null, navigationTurnPhrase(99))
    }
}
