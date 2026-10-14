package com.queuego.ridermaplibreosm

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class MapLibreOsmRuntimeTest {
    private fun awaitVillageRoadFeatures(
        scenario: ActivityScenario<MapLibreOsmRuntimeActivity>,
        timeoutMs: Long = 30_000L
    ): Int {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        var count = 0
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { count = it.renderedVillageRoadFeatureCount() }
            if (count > 0) return count
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(100)
        }
        throw AssertionError("OSM village road features did not render at Ban Sawai Chik")
    }

    @Test
    fun shortbreadVillageRoadsRenderAndResumeKeepsSameMapObjects() {
        ActivityScenario.launch(MapLibreOsmRuntimeActivity::class.java).use { scenario ->
            lateinit var activity: MapLibreOsmRuntimeActivity
            scenario.onActivity { activity = it }

            assertTrue(
                "MapLibre OSM style did not become ready",
                activity.mapReady.await(30, TimeUnit.SECONDS)
            )
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            var initialView = 0
            var initialMap = 0
            scenario.onActivity {
                initialView = it.mapViewIdentity()
                initialMap = requireNotNull(it.mapIdentity())
                assertEquals(
                    "Not all village road layers were installed",
                    4,
                    it.villageRoadLayerCount()
                )
            }

            val renderedRoads = awaitVillageRoadFeatures(scenario)
            assertTrue("Expected at least one rendered local OSM road", renderedRoads > 0)

            scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(250)
            scenario.moveToState(Lifecycle.State.RESUMED)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                assertEquals("Background/resume recreated MapLibre MapView", initialView, it.mapViewIdentity())
                assertEquals("Background/resume recreated MapLibre map", initialMap, it.mapIdentity())
                assertEquals(4, it.villageRoadLayerCount())
            }
            assertTrue(awaitVillageRoadFeatures(scenario) > 0)
        }
    }
}
