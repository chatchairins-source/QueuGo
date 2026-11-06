package com.queuego.rider

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import com.queuego.ridernavigationmap.*
import kotlinx.coroutines.runBlocking
import com.valhalla.valhalla.Valhalla
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Requires the real Thailand Valhalla package inside the installed Rider APK. Never skips. */
class RiderOfflineRoutingRuntimeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before fun enforceNoNetworkAfterInstallation() {
        listOf("cmd connectivity airplane-mode enable", "svc wifi disable", "svc data disable").forEach {
            instrumentation.uiAutomation.executeShellCommand(it).use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { input -> input.readBytes() }
            }
        }
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (connectivity.activeNetwork != null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(250)
        }
        assertEquals(1, Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON))
        assertNull("Runtime must have no active network", connectivity.activeNetwork)
        assertTrue("Installed Rider APK must contain the real routing graph",
            context.assets.list("routing").orEmpty().contains("tiles.tar"))
    }

    @Test fun fiveRegionsAndCrossRegionRouteWithNoNetwork() = runBlocking {
        val provider = BundledOfflineRouting.provider(context, null)
        val pairs = listOf(
            14.9942 to 103.1020 to (15.0054 to 103.1114),
            13.7563 to 100.5018 to (13.7465 to 100.5340),
            18.7883 to 98.9853 to (18.7901 to 98.9987),
            16.4322 to 102.8236 to (16.4451 to 102.8358),
            7.0086 to 100.4747 to (7.0164 to 100.4891),
            13.7563 to 100.5018 to (18.7883 to 98.9853)
        )
        for ((index, pair) in pairs.withIndex()) {
            val from = NavigationMapPoint(pair.first.first, pair.first.second)
            val stop = NavigationMapStop("destination-$index", 1, NavigationStopKind.DROPOFF,
                NavigationMapPoint(pair.second.first, pair.second.second))
            val leg = provider.route(from, stop, "offline-$index")
            assertTrue(leg.segment.renderable)
            assertTrue(leg.distanceMeters > 1000)
            assertTrue(leg.instructions.size >= 2)
            assertEquals(9, leg.instructions.last().turnCode)
            assertTrue(leg.instructions.all { !it.instructionText.isNullOrBlank() })
        }
        assertNull((context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetwork)
    }

    @Test fun rerouteComputesNewRoadGeometryWithoutNetwork() = runBlocking {
        val provider = CachingNavigationRouteProvider(BundledOfflineRouting.provider(context, null))
        val stop = NavigationMapStop("destination", 1, NavigationStopKind.DROPOFF,
            NavigationMapPoint(13.7465, 100.5340))
        val first = provider.route(NavigationMapPoint(13.7563,100.5018), stop, "active")
        val rerouted = provider.route(NavigationMapPoint(13.7530,100.5050), stop, "reroute-1")
        assertTrue(rerouted.segment.renderable)
        assertNotEquals(first.segment.points, rerouted.segment.points)
        assertEquals(stop.id, rerouted.segment.toStopId)
        var rejected = false
        try {
            provider.route(NavigationMapPoint(0.0, 0.0), stop, "reroute-outside-graph")
        } catch (error: Exception) {
            rejected = true
            assertFalse("No-route failure must be explicit", error.message.isNullOrBlank())
        }
        assertTrue("A point outside the graph must not produce invented geometry", rejected)
        assertNull((context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetwork)
    }
    @Test fun originalOsmOneWaysAndTurnRestrictionsRemainEnforced() = runBlocking {
        val provider = BundledOfflineRouting.provider(context, null)
        val fixture = JSONObject(instrumentation.context.assets
            .open("thailand-261104-routing-probes.json").bufferedReader().use { it.readText() })
        val manifest = JSONObject(context.assets.open("routing/manifest.json")
            .bufferedReader().use { it.readText() })
        assertEquals(fixture.getString("dataset_sha256"),
            manifest.getJSONObject("source").getString("extract_sha256"))
        val cases = fixture.getJSONArray("cases")
        assertEquals(16, cases.length())
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val origin = case.getJSONArray("origin")
            val destination = case.getJSONArray("destination")
            val stop = NavigationMapStop("source-$index", 1, NavigationStopKind.DROPOFF,
                NavigationMapPoint(destination.getDouble(0), destination.getDouble(1)))
            // Trace the ACTUAL production provider's leg, not an alternative route request.
            val leg = provider.route(NavigationMapPoint(origin.getDouble(0), origin.getDouble(1)),
                stop, "source-$index")
            val config = File(context.noBackupFilesDir,
                "routing/${manifest.getJSONObject("graph").getString("sha256")}/valhalla.json")
            val shape = JSONArray()
            leg.segment.points.forEach { shape.put(JSONObject().put("lat", it.latitude).put("lon", it.longitude)) }
            val request = JSONObject().put("shape", shape).put("costing", "motorcycle")
                .put("shape_match", "edge_walk").put("filters", JSONObject().put("action", "include")
                    .put("attributes", JSONArray(listOf("edge.way_id", "edge.begin_shape_index",
                        "edge.end_shape_index", "edge.drive_on_right"))))
            val trace = Valhalla(config.absolutePath).use { JSONObject(it.traceAttributesRaw(request.toString())) }
            val edges = trace.getJSONArray("edges")
            assertTrue(edges.length() > 0)
            val ids = (0 until edges.length()).map { edges.getJSONObject(it).getLong("way_id") }
            val compact = ids.filterIndexed { i, id -> i == 0 || ids[i - 1] != id }
            for (i in 0 until edges.length()) assertFalse(edges.getJSONObject(i).getBoolean("drive_on_right"))
            if (case.getString("kind") == "forbidden_turn") {
                val fromWay = case.getLong("from_way")
                val toWay = case.getLong("to_way")
                assertEquals("Origin must anchor on the original source member", fromWay, compact.first())
                assertEquals("Destination must anchor on the original source member", toWay, compact.last())
                assertFalse("Do not traverse OSM restriction ${case.getLong("relation_id")}",
                    compact.zipWithNext().any { it.first == fromWay && it.second == toWay })
            } else {
                assertEquals("oneway", case.getString("kind"))
                assertEquals("yes", case.getString("oneway"))
                val way = case.getLong("way_id")
                assertEquals(way, compact.first())
                assertEquals(way, compact.last())
                assertTrue("Reverse endpoints must take a detour", compact.size >= 3)
                for (i in 0 until edges.length()) {
                    val edge = edges.getJSONObject(i)
                    if (edge.getLong("way_id") != way) continue
                    val before = sourcePosition(leg.segment.points[edge.getInt("begin_shape_index")], case.getJSONArray("way_points"))
                    val after = sourcePosition(leg.segment.points[edge.getInt("end_shape_index")], case.getJSONArray("way_points"))
                    assertTrue(before.first <= 5 && after.first <= 5)
                    assertTrue("OSM way $way was traversed backwards", after.second >= before.second - 2)
                }
            }
        }
    }

    private fun sourcePosition(point: NavigationMapPoint, way: JSONArray): Pair<Double, Double> {
        val latScale = 111195.08
        val lonScale = latScale * cos(Math.toRadians(point.latitude))
        var closest = Double.POSITIVE_INFINITY
        var position = 0.0
        var walked = 0.0
        for (i in 0 until way.length() - 1) {
            val start = way.getJSONArray(i)
            val end = way.getJSONArray(i + 1)
            val dx = (end.getDouble(1) - start.getDouble(1)) * lonScale
            val dy = (end.getDouble(0) - start.getDouble(0)) * latScale
            val length = hypot(dx, dy)
            if (length == 0.0) continue
            val px = (point.longitude - start.getDouble(1)) * lonScale
            val py = (point.latitude - start.getDouble(0)) * latScale
            val fraction = ((px * dx + py * dy) / (length * length)).coerceIn(0.0, 1.0)
            val separation = hypot(px - fraction * dx, py - fraction * dy)
            if (separation < closest) {
                closest = separation
                position = walked + fraction * length
            }
            walked += length
        }
        return closest to position
    }

}
