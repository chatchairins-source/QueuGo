package com.queuego.ridernavigationmap

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class NavigationMapRuntimeTest {
    @Test
    fun gpsRefreshAndResumeReuseTheSameMapObjects() {
        ActivityScenario.launch(NavigationMapRuntimeActivity::class.java).use { scenario ->
            lateinit var runtimeActivity: NavigationMapRuntimeActivity
            scenario.onActivity { activity ->
                runtimeActivity = activity
            }
            assertTrue(
                "Standalone Longdo navigation map did not become ready",
                runtimeActivity.mapReady.await(30, TimeUnit.SECONDS)
            )

            var initialView = 0
            var initialSdkMap = 0
            var initialDiagnostics = NavigationMapDiagnostics(0, 0, 0, 0)
            scenario.onActivity { activity ->
                initialView = activity.mapViewIdentity()
                initialSdkMap = activity.sdkMapIdentity()
                initialDiagnostics = activity.latestDiagnostics
            }
            assertEquals(1, initialDiagnostics.viewCreatedCount)
            assertEquals(1, initialDiagnostics.mapCreatedCount)
            assertTrue(initialDiagnostics.routeLayerRebuildCount >= 1)
            assertTrue(initialDiagnostics.stopMutationCount >= 2)

            repeat(16) { index ->
                scenario.onActivity { it.refreshRiderFix(index + 1) }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }

            scenario.onActivity { activity ->
                assertEquals("GPS refresh recreated MapGLSurfaceView", initialView, activity.mapViewIdentity())
                assertEquals("GPS refresh recreated Longdo map", initialSdkMap, activity.sdkMapIdentity())
                assertEquals(1, activity.latestDiagnostics.viewCreatedCount)
                assertEquals(1, activity.latestDiagnostics.mapCreatedCount)
                assertEquals(
                    "GPS refresh rebuilt unchanged route layers",
                    initialDiagnostics.routeLayerRebuildCount,
                    activity.latestDiagnostics.routeLayerRebuildCount
                )
                assertEquals(
                    "GPS refresh rebuilt unchanged stop pins",
                    initialDiagnostics.stopMutationCount,
                    activity.latestDiagnostics.stopMutationCount
                )
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(300)
            scenario.moveToState(Lifecycle.State.RESUMED)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                assertEquals("Resume recreated MapGLSurfaceView", initialView, activity.mapViewIdentity())
                assertEquals("Resume recreated Longdo map", initialSdkMap, activity.sdkMapIdentity())
                assertEquals(1, activity.latestDiagnostics.viewCreatedCount)
                assertEquals(1, activity.latestDiagnostics.mapCreatedCount)
            }

            scenario.onActivity { it.advanceToSecondStop() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals("Stop progression recreated MapGLSurfaceView", initialView, activity.mapViewIdentity())
                assertEquals("Stop progression recreated Longdo map", initialSdkMap, activity.sdkMapIdentity())
                assertEquals(
                    initialDiagnostics.routeLayerRebuildCount + 1,
                    activity.latestDiagnostics.routeLayerRebuildCount
                )
                assertTrue(activity.latestDiagnostics.stopMutationCount > initialDiagnostics.stopMutationCount)
            }
        }
    }
}
