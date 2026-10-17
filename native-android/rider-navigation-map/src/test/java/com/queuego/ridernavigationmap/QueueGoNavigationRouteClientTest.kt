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
