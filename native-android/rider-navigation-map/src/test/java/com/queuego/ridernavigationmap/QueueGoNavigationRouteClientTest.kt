package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class QueueGoNavigationRouteClientTest {
    private val stop = NavigationMapStop(
        id = "queuego-route-stop",
        sequence = 1,
        kind = NavigationStopKind.PICKUP,
        point = NavigationMapPoint(13.0, 100.01)
    )

    private val routeJson =
        """{"code":"Ok","routes":[{"distance":4494,"duration":289,"route_id":"qg-1","geometry":{"type":"LineString","coordinates":[[100.0,13.0],[100.01,13.0]]},"legs":[{"steps":[{"distance":100,"duration":10,"name":"ถนนทดสอบ","vendor_type":1}]}]}]}"""

    @Test fun parserUsesQueueGoSummaryAndProducesRenderableLeg() {
        val leg = QueueGoNavigationRouteClient("https://maps.queuego.test")
            .parseQueueGoRoute(routeJson, stop, "leg-1")

        assertEquals(4494.0, leg.distanceMeters, 0.001)
        assertEquals(289.0, leg.durationSeconds!!, 0.001)
        assertEquals("queuego-route-stop", leg.segment.toStopId)
        assertEquals(2, leg.segment.points.size)
        assertEquals(1, leg.instructions.size)
        assertEquals(1, leg.instructions.first().turnCode)
        assertEquals("ถนนทดสอบ", leg.instructions.first().roadName)
    }

    @Test fun parserKeepsOnlyExplicitOsmSpeedLimits() {
        val raw =
            """{"code":"Ok","routes":[{"distance":1200,"duration":180,"geometry":{"type":"LineString","coordinates":[[100.0,13.0],[100.01,13.0]]},"maxspeed_intervals":[{"start_geometry_m":0,"end_geometry_m":400,"maxspeed_kph":50,"maxspeed_raw":"50","source":"osm:maxspeed","osm_way_id":101},{"start_geometry_m":400,"end_geometry_m":800,"maxspeed_kph":60,"maxspeed_raw":"60","source":"queuego:road_class_estimate_v1","osm_way_id":202},{"start_geometry_m":800,"end_geometry_m":700,"maxspeed_kph":40,"maxspeed_raw":"40","source":"osm:maxspeed"}],"legs":[]}]}"""
        val leg = QueueGoNavigationRouteClient("https://maps.queuego.test")
            .parseQueueGoRoute(raw, stop, "leg-1")

        assertEquals(1, leg.speedLimitIntervals.size)
        val limit = leg.speedLimitIntervals.single()
        assertEquals(0.0, limit.startGeometryMeters, 0.001)
        assertEquals(400.0, limit.endGeometryMeters, 0.001)
        assertEquals(50.0, limit.maxspeedKph, 0.001)
        assertEquals("50", limit.maxspeedRaw)
        assertEquals("osm:maxspeed", limit.source)
        assertEquals(101L, limit.osmWayId)
    }

    @Test fun staleSpeedLimitBeyondGeometryIsNotShownToRider() {
        val raw = """{"code":"Ok","routes":[{"distance":1200,"duration":180,"geometry":{"type":"LineString","coordinates":[[100.0,13.0],[100.01,13.0]]},"maxspeed_intervals":[{"start_geometry_m":0,"end_geometry_m":400,"maxspeed_kph":50,"maxspeed_raw":"50","source":"osm:maxspeed"},{"start_geometry_m":400,"end_geometry_m":5000,"maxspeed_kph":80,"maxspeed_raw":"80","source":"osm:maxspeed"}],"legs":[]}]}"""
        val leg = QueueGoNavigationRouteClient("https://maps.queuego.test")
            .parseQueueGoRoute(raw, stop, "leg-1")

        assertEquals(1, leg.speedLimitIntervals.size)
        assertEquals(50.0, leg.speedLimitIntervals.single().maxspeedKph, 0.001)
    }

    @Test fun missingSpeedLimitIntervalsStayBackwardCompatible() {
        val leg = QueueGoNavigationRouteClient("https://maps.queuego.test")
            .parseQueueGoRoute(routeJson, stop, "leg-1")

        assertEquals(emptyList<NavigationSpeedLimitInterval>(), leg.speedLimitIntervals)
    }

    @Test fun negativeStepDistanceCannotSilentlyBecomeZero() {
        val malformed = routeJson.replace("\"distance\":100", "\"distance\":-10")
        assertThrows(IllegalArgumentException::class.java) {
            QueueGoNavigationRouteClient("https://maps.queuego.test")
                .parseQueueGoRoute(malformed, stop, "leg-1")
        }
    }

    @Test fun nonNumericStepDurationCannotSilentlyBecomeZero() {
        val malformed = routeJson.replace("\"duration\":10", "\"duration\":\"invalid\"")
        assertThrows(IllegalArgumentException::class.java) {
            QueueGoNavigationRouteClient("https://maps.queuego.test")
                .parseQueueGoRoute(malformed, stop, "leg-1")
        }
    }

    @Test fun explicitNullStepMetricIsRejected() {
        val malformed = routeJson.replace("\"duration\":10", "\"duration\":null")
        assertThrows(IllegalArgumentException::class.java) {
            QueueGoNavigationRouteClient("https://maps.queuego.test")
                .parseQueueGoRoute(malformed, stop, "leg-1")
        }
    }

    @Test fun booleanStepMetricIsRejected() {
        val malformed = routeJson.replace("\"distance\":100", "\"distance\":true")
        assertThrows(IllegalArgumentException::class.java) {
            QueueGoNavigationRouteClient("https://maps.queuego.test")
                .parseQueueGoRoute(malformed, stop, "leg-1")
        }
    }

    @Test fun positiveNumericStepMetricsArePreserved() {
        val raw = routeJson.replace("\"distance\":100", "\"distance\":12.5")
            .replace("\"duration\":10", "\"duration\":3.25")
        val instruction = QueueGoNavigationRouteClient("https://maps.queuego.test")
            .parseQueueGoRoute(raw, stop, "leg-1").instructions.single()
        assertEquals(12.5, instruction.sectionDistanceMeters, 0.001)
        assertEquals(3.25, instruction.sectionDurationSeconds, 0.001)
    }

    @Test fun malformedStepObjectCannotSilentlyDropTurnInstruction() {
        val malformed = routeJson.replace(
            """{"distance":100,"duration":10,"name":"ถนนทดสอบ","vendor_type":1}""",
            "null"
        )
        assertThrows(IllegalStateException::class.java) {
            QueueGoNavigationRouteClient("https://maps.queuego.test")
                .parseQueueGoRoute(malformed, stop, "leg-1")
        }
    }

    @Test fun nullNavigationLegCannotSilentlyEraseGuidance() {
        val malformed = routeJson.replace(
            """{"steps":[{"distance":100,"duration":10,"name":"ถนนทดสอบ","vendor_type":1}]}""",
            "null"
        )
        assertThrows(IllegalStateException::class.java) {
            QueueGoNavigationRouteClient("https://maps.queuego.test")
                .parseQueueGoRoute(malformed, stop, "leg-1")
        }
    }

    @Test fun malformedStepsArrayCannotSilentlyEraseGuidance() {
        val malformed = routeJson.replace(
            """"steps":[{"distance":100,"duration":10,"name":"ถนนทดสอบ","vendor_type":1}]""",
            """"steps":null"""
        )
        assertThrows(IllegalStateException::class.java) {
            QueueGoNavigationRouteClient("https://maps.queuego.test")
                .parseQueueGoRoute(malformed, stop, "leg-1")
        }
    }

    @Test fun missingStepsRemainBackwardCompatible() {
        val withoutSteps = routeJson.replace(
            """"steps":[{"distance":100,"duration":10,"name":"ถนนทดสอบ","vendor_type":1}]""",
            """"legacy":true"""
        )
        val leg = QueueGoNavigationRouteClient("https://maps.queuego.test")
            .parseQueueGoRoute(withoutSteps, stop, "leg-1")
        assertEquals(0, leg.instructions.size)
    }

    @Test fun missingOptionalStepMetricsRemainBackwardCompatible() {
        val missing = routeJson.replace("\"distance\":100,\"duration\":10,", "")
        val leg = QueueGoNavigationRouteClient("https://maps.queuego.test")
            .parseQueueGoRoute(missing, stop, "leg-1")
        assertEquals(0.0, leg.instructions.single().sectionDistanceMeters, 0.001)
        assertEquals(0.0, leg.instructions.single().sectionDurationSeconds, 0.001)
    }

    @Test fun missingDurationKeepsGeometryWithoutInventingEta() {
        val raw =
            """{"code":"Ok","routes":[{"distance":1200,"geometry":{"type":"LineString","coordinates":[[100.0,13.0],[100.01,13.0]]},"legs":[]}]}"""
        val leg = QueueGoNavigationRouteClient("https://maps.queuego.test")
            .parseQueueGoRoute(raw, stop, "leg-1")

        assertNull(leg.durationSeconds)
    }

    @Test fun missingRouteCannotRenderAsStraightLineFallback() {
        assertThrows(IllegalStateException::class.java) {
            QueueGoNavigationRouteClient("https://maps.queuego.test")
                .parseQueueGoRoute("""{"code":"Ok","routes":[]}""", stop, "leg-1")
        }
    }


    @Test fun unrelatedDestinationGeometryIsRejectedEvenWithSuccessfulResponseCode() {
        val unrelated = routeJson.replace("[100.01,13.0]", "[101.01,13.0]")
        assertThrows(IllegalArgumentException::class.java) {
            QueueGoNavigationRouteClient("https://maps.queuego.test")
                .parseQueueGoRoute(unrelated, stop, "leg-1", from = NavigationMapPoint(13.0, 100.0))
        }
    }

    @Test fun routeFromUnrelatedOriginIsRejectedBeforeNavigationCanDrawIt() {
        assertThrows(IllegalArgumentException::class.java) {
            QueueGoNavigationRouteClient("https://maps.queuego.test")
                .parseQueueGoRoute(routeJson, stop, "leg-1", from = NavigationMapPoint(14.0, 100.0))
        }
    }

    @Test fun smallRoadSnapNearRequestedEndpointsRemainsValid() {
        val snapped = routeJson
            .replace("[100.0,13.0]", "[100.0004,13.0]")
            .replace("[100.01,13.0]", "[100.0104,13.0]")
        val leg = QueueGoNavigationRouteClient("https://maps.queuego.test")
            .parseQueueGoRoute(snapped, stop, "leg-1", from = NavigationMapPoint(13.0, 100.0))
        assertEquals(2, leg.segment.points.size)
    }

    @Test fun routeEndingBeyondSnapBoundaryIsRejectedInsteadOfMisleadingTheRider() {
        val farEnd = routeJson.replace("[100.01,13.0]", "[100.03,13.0]")
        assertThrows(IllegalArgumentException::class.java) {
            QueueGoNavigationRouteClient("https://maps.queuego.test")
                .parseQueueGoRoute(farEnd, stop, "leg-1")
        }
    }


    @Test fun malformedInteriorCoordinateCannotBecomeInventedStraightLine() {
        val malformed = routeJson.replace(
            "[[100.0,13.0],[100.01,13.0]]",
            "[[100.0,13.0],[\"invalid\",13.0],[100.01,13.0]]"
        )
        assertThrows(IllegalArgumentException::class.java) {
            QueueGoNavigationRouteClient("https://maps.queuego.test")
                .parseQueueGoRoute(malformed, stop, "leg-1")
        }
    }

    @Test fun missingInteriorCoordinateArrayIsRejectedRatherThanSkipped() {
        val malformed = routeJson.replace(
            "[[100.0,13.0],[100.01,13.0]]",
            "[[100.0,13.0],null,[100.01,13.0]]"
        )
        assertThrows(IllegalStateException::class.java) {
            QueueGoNavigationRouteClient("https://maps.queuego.test")
                .parseQueueGoRoute(malformed, stop, "leg-1")
        }
    }


    @Test fun boundedRuralRoadSnapRemainsAcceptedWithoutInventingDestination() {
        val nearRoad = routeJson.replace("[100.01,13.0]", "[100.016,13.0]")
        val leg = QueueGoNavigationRouteClient("https://maps.queuego.test")
            .parseQueueGoRoute(nearRoad, stop, "leg-1", from = NavigationMapPoint(13.0, 100.0))
        assertEquals(2, leg.segment.points.size)
    }

    @Test fun disconnectedInteriorGeometryCannotClaimShortRoadDistance() {
        // The route starts and ends at the correct stops, but a middle vertex
        // jumps far away; accepting its short server summary would mislead guidance.
        val disconnected = routeJson.replace(
            "[[100.0,13.0],[100.01,13.0]]",
            "[[100.0,13.0],[102.0,13.0],[100.01,13.0]]"
        )
        assertThrows(IllegalArgumentException::class.java) {
            QueueGoNavigationRouteClient("https://maps.queuego.test")
                .parseQueueGoRoute(disconnected, stop, "leg-1", from = NavigationMapPoint(13.0, 100.0))
        }
    }

    @Test fun impossiblyShortReportedDistanceCannotCorruptGuidance() {
        val falseSummary = routeJson.replace("\"distance\":4494", "\"distance\":50")
        assertThrows(IllegalArgumentException::class.java) {
            QueueGoNavigationRouteClient("https://maps.queuego.test")
                .parseQueueGoRoute(falseSummary, stop, "leg-1", from = NavigationMapPoint(13.0, 100.0))
        }
    }

    @Test fun legitimateRoadDistanceAboveSimplifiedGeometryIsPreserved() {
        val leg = QueueGoNavigationRouteClient("https://maps.queuego.test")
            .parseQueueGoRoute(routeJson, stop, "leg-1", from = NavigationMapPoint(13.0, 100.0))
        assertEquals(4494.0, leg.distanceMeters, 0.001)
    }

    @Test fun absentDistanceUsesMeasuredGeometryWithoutInventingSummary() {
        val withoutDistance = routeJson.replace("\"distance\":4494,", "")
        val leg = QueueGoNavigationRouteClient("https://maps.queuego.test")
            .parseQueueGoRoute(withoutDistance, stop, "leg-1", from = NavigationMapPoint(13.0, 100.0))
        val measured = navigationHaversineMeters(
            NavigationMapPoint(13.0, 100.0),
            NavigationMapPoint(13.0, 100.01)
        )
        assertEquals(measured, leg.distanceMeters, 0.01)
    }

    @Test fun longInteriorJumpIsRejectedEvenWhenServerInflatesDistanceToMatch() {
        // A matching inflated summary cannot certify a teleport between road vertices.
        val disconnected = routeJson
            .replace("\"distance\":4494", "\"distance\":600000")
            .replace(
                "[[100.0,13.0],[100.01,13.0]]",
                "[[100.0,13.0],[102.0,13.0],[100.01,13.0]]"
            )
        assertThrows(IllegalArgumentException::class.java) {
            QueueGoNavigationRouteClient("https://maps.queuego.test")
                .parseQueueGoRoute(disconnected, stop, "leg-1", from = NavigationMapPoint(13.0, 100.0))
        }
    }

    @Test fun longInteriorJumpIsRejectedEvenWhenSummaryDistanceIsAbsent() {
        val disconnected = routeJson
            .replace("\"distance\":4494,", "")
            .replace(
                "[[100.0,13.0],[100.01,13.0]]",
                "[[100.0,13.0],[102.0,13.0],[100.01,13.0]]"
            )
        assertThrows(IllegalArgumentException::class.java) {
            QueueGoNavigationRouteClient("https://maps.queuego.test")
                .parseQueueGoRoute(disconnected, stop, "leg-1", from = NavigationMapPoint(13.0, 100.0))
        }
    }

    @Test fun cleartextServiceUrlIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            QueueGoNavigationRouteClient("http://maps.queuego.test")
        }
    }
}
