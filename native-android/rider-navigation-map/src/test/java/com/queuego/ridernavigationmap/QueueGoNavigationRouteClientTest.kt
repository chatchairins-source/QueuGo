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

    @Test fun missingSpeedLimitIntervalsStayBackwardCompatible() {
        val leg = QueueGoNavigationRouteClient("https://maps.queuego.test")
            .parseQueueGoRoute(routeJson, stop, "leg-1")

        assertEquals(emptyList<NavigationSpeedLimitInterval>(), leg.speedLimitIntervals)
    }

    @Test fun missingDurationKeepsGeometryWithoutInventingEta() {
        val raw =
            """{"code":"Ok","routes":[{"distance":100,"geometry":{"type":"LineString","coordinates":[[100.0,13.0],[100.01,13.0]]},"legs":[]}]}"""
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

    @Test fun cleartextServiceUrlIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            QueueGoNavigationRouteClient("http://maps.queuego.test")
        }
    }
}
