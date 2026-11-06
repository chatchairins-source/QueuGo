package com.queuego.rider

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import com.queuego.ridernavigationmap.*
import kotlinx.coroutines.runBlocking
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
        assertNull((context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetwork)
    }
}
