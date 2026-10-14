package com.queuego.ridermaplibreosm

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class MapLibreOsmRuntimeTest {
    @Test
    fun realPmTilesAssetRendersVillageRoadsWhenInjected() {
        ActivityScenario.launch(MapLibreOsmRuntimeActivity::class.java).use { scenario ->
            lateinit var activity: MapLibreOsmRuntimeActivity
            scenario.onActivity { activity = it }
            assertTrue(
                "MapLibre OSM runtime map did not become ready",
                activity.mapReady.await(30, TimeUnit.SECONDS)
            )
            assumeTrue(
                "Real PMTiles asset not injected for this runtime",
                activity.isUsingRealPmTiles()
            )
            assertTrue(
                "Real PMTiles map did not fully render",
                activity.mapFullyRendered.await(30, TimeUnit.SECONDS)
            )
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertTrue(
                    "SawaiJee PMTiles rendered no village road features",
                    it.renderedVillageRoadFeatureCount() > 0
                )
                assertEquals(1, it.styleApplyCount())
            }
        }
    }

    @Test
    fun refreshTabReturnAndResumeReuseSameMapObjects() {
        ActivityScenario.launch(MapLibreOsmRuntimeActivity::class.java).use { scenario ->
            lateinit var activity: MapLibreOsmRuntimeActivity
            scenario.onActivity { activity = it }
            assertTrue(
                "MapLibre OSM runtime map did not become ready",
                activity.mapReady.await(30, TimeUnit.SECONDS)
            )

            var initialView = 0
            var initialMap = 0
            scenario.onActivity {
                initialView = it.mapViewIdentity()
                initialMap = it.mapIdentity()
                assertEquals(1, it.styleApplyCount())
            }

            repeat(60) { step ->
                scenario.onActivity { it.refreshCamera(step + 1) }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }
            scenario.onActivity {
                assertEquals("GPS-like camera refresh recreated MapView", initialView, it.mapViewIdentity())
                assertEquals("GPS-like camera refresh recreated MapLibreMap", initialMap, it.mapIdentity())
                assertEquals("Camera-only refresh reloaded Shortbread style", 1, it.styleApplyCount())
            }

            scenario.onActivity { it.refreshEquivalentConfig() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertEquals("Equivalent config reloaded style", 1, it.styleApplyCount())
                assertEquals(initialView, it.mapViewIdentity())
                assertEquals(initialMap, it.mapIdentity())
            }

            scenario.onActivity { it.detachForAnotherTab() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { it.reattachAfterReturningHome() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertEquals("Tab return recreated MapView", initialView, it.mapViewIdentity())
                assertEquals("Tab return recreated MapLibreMap", initialMap, it.mapIdentity())
                assertEquals("Tab return reloaded style", 1, it.styleApplyCount())
            }

            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertEquals("Resume recreated MapView", initialView, it.mapViewIdentity())
                assertEquals("Resume recreated MapLibreMap", initialMap, it.mapIdentity())
                assertEquals("Resume reloaded style", 1, it.styleApplyCount())
                assertFalse("MapLibre view destroyed during ordinary lifecycle", it.mapDestroyed())
            }
        }
    }
}
