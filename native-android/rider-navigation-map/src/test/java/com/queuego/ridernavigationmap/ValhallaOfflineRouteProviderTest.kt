package com.queuego.ridernavigationmap

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ValhallaOfflineRouteProviderTest {
    private val from = NavigationMapPoint(14.9942, 103.1020)
    private val stop = NavigationMapStop("delivery", 1, NavigationStopKind.DROPOFF,
        NavigationMapPoint(15.0054, 103.1114))
    // Real Valhalla 3.9.1 motorcycle response, generated from Geofabrik 261104 Thailand.
    // This fixture tests the adapter; it is not Android/airplane-mode certification.
    private fun response(): String = javaClass.getResource("/offline-routing/buriram-osm-261104.json")!!.readText()

    @Test fun realRoadResponsePreservesGeometryMetricsAndNarrative() = runBlocking {
        var request: JSONObject? = null
        val provider = ValhallaOfflineRouteProvider { raw -> request = JSONObject(raw); response() }
        val leg = provider.route(from, stop, "active")
        assertEquals("motorcycle", request!!.getString("costing"))
        assertEquals(2, request!!.getJSONArray("locations").length())
        assertEquals(2522.0, leg.distanceMeters, 0.1)
        assertEquals(211.154, leg.durationSeconds!!, 0.001)
        assertEquals(48, leg.segment.points.size)
        assertEquals(5, leg.instructions.size)
        assertEquals(9, leg.instructions.last().turnCode)
        assertTrue(leg.instructions[3].instructionText!!.contains("ถนนหลักเมือง"))
        assertTrue(navigationInstructionPhrase(leg.instructions[3])!!.contains("Turn left"))
    }

    @Test fun adapterAcceptsFiveRegionsAndCrossRegionRealGraphResponses() {
        val cases = listOf(
            Triple("buriram", NavigationMapPoint(14.9942,103.1020), NavigationMapPoint(15.0054,103.1114)),
            Triple("bangkok", NavigationMapPoint(13.7563,100.5018), NavigationMapPoint(13.7465,100.5340)),
            Triple("chiangmai", NavigationMapPoint(18.7883,98.9853), NavigationMapPoint(18.7901,98.9987)),
            Triple("khonkaen", NavigationMapPoint(16.4322,102.8236), NavigationMapPoint(16.4451,102.8358)),
            Triple("hatyai", NavigationMapPoint(7.0086,100.4747), NavigationMapPoint(7.0164,100.4891)),
            Triple("bangkok-chiangmai", NavigationMapPoint(13.7563,100.5018), NavigationMapPoint(18.7883,98.9853))
        )
        for ((name, origin, destination) in cases) {
            val raw = javaClass.getResource("/offline-routing/$name-osm-261104.json")!!.readText()
            val result = parseValhallaOfflineRoute(raw, origin, stop.copy(point = destination), name)
            assertTrue(name, result.segment.renderable)
            assertTrue(name, result.instructions.size >= 2)
            assertEquals(name, 9, result.instructions.last().turnCode)
        }
    }

    @Test fun noRouteDoesNotCreateAnyGeometry() {
        assertThrows(IllegalStateException::class.java) {
            parseValhallaOfflineRoute("{\"error_code\":442,\"error\":\"No path\"}", from, stop, "active")
        }
    }

    @Test fun rejectsDifferentUnitsAndUnrelatedEndpoints() {
        val units = JSONObject(response()).apply { getJSONObject("trip").put("units", "miles") }
        assertThrows(IllegalArgumentException::class.java) {
            parseValhallaOfflineRoute(units.toString(), from, stop, "active")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseValhallaOfflineRoute(response(), from,
                stop.copy(point = NavigationMapPoint(13.75, 100.5)), "active")
        }
    }

    @Test fun rejectsMalformedShapeAndManeuverIndices() {
        assertThrows(IllegalArgumentException::class.java) { decodeValhallaPolyline6("~~~~~~") }
        val malformed = JSONObject(response()).apply {
            getJSONObject("trip").getJSONArray("legs").getJSONObject(0)
                .getJSONArray("maneuvers").getJSONObject(1).put("end_shape_index", 99999)
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseValhallaOfflineRoute(malformed.toString(), from, stop, "active")
        }
    }

    @Test fun roundaboutNarrativeRemainsEligibleForGuidance() {
        val instruction = NavigationRouteInstruction(1, 7, "", 200.0, 20.0,
            "Enter the roundabout and take the second exit.")
        val leg = parseValhallaOfflineRoute(response(), from, stop, "active")
        val updated = leg.copy(instructions = listOf(leg.instructions[0], instruction))
        assertEquals(instruction, navigationUpcomingInstructions(updated, 0.0).first().first)
    }
}
