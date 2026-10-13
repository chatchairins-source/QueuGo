package com.queuego.ridernavigationmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LongdoNavigationRouteClientTest {
    private val stop = NavigationMapStop(
        id = "route-format-stop",
        sequence = 1,
        kind = NavigationStopKind.PICKUP,
        point = NavigationMapPoint(13.0, 100.01)
    )

    // Route-format fixtures only: no users, orders, accounts or Production writes.
    private val geometry =
        """[{"type":"Feature","properties":{"distance":100,"interval":10,"turn":1,"name":"ถนนทดสอบ"},"geometry":{"type":"LineString","coordinates":[[100.0,13.0],[100.01,13.0]]}}]"""

    @Test fun parserUsesServiceSummaryAndProducesRenderableLeg() {
        val leg = LongdoNavigationRouteClient("test-key").parseGeoJson(
            """{"type":"FeatureCollection","meta":{"status":200},"data":{"distance":4494,"interval":289},"features":$geometry}""",
            stop,
            "leg-1"
        )
        assertEquals(4494.0, leg.distanceMeters, 0.001)
        assertEquals(289.0, leg.durationSeconds!!, 0.001)
        assertEquals("route-format-stop", leg.segment.toStopId)
        assertEquals(2, leg.segment.points.size)
        assertEquals(1, leg.instructions.size)
        assertEquals(1, leg.instructions.first().turnCode)
        assertEquals("ถนนทดสอบ", leg.instructions.first().roadName)
    }

    @Test fun missingDurationKeepsMapGeometryInsteadOfInventingEta() {
        val noDuration = geometry.replace("\"interval\":10", "\"interval\":0")
        val leg = LongdoNavigationRouteClient("test-key").parseGeoJson(
            """{"type":"FeatureCollection","features":$noDuration}""",
            stop,
            "leg-1"
        )
        assertNull(leg.durationSeconds)
    }

    @Test fun serviceErrorCannotRenderAsRoute() {
        assertThrows(IllegalStateException::class.java) {
            LongdoNavigationRouteClient("test-key").parseGeoJson(
                """{"meta":{"status":403},"features":$geometry}""",
                stop,
                "leg-1"
            )
        }
    }
}
