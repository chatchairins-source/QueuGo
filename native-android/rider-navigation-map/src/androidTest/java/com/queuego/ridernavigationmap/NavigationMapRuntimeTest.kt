package com.queuego.ridernavigationmap

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class NavigationMapRuntimeTest {
    private fun awaitAppliedRender(
        activity: NavigationMapRuntimeActivity,
        previousRenderPassCount: Int,
        timeoutMs: Long = 5_000L
    ): NavigationMapDiagnostics {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val diagnostics = activity.latestDiagnostics
            if (diagnostics.renderPassCount > previousRenderPassCount) return diagnostics
            Thread.sleep(25)
        }
        throw AssertionError(
            "Navigation map state was not applied after render pass $previousRenderPassCount"
        )
    }

    private fun awaitMapPresence(
        scenario: ActivityScenario<NavigationMapRuntimeActivity>,
        expected: Boolean,
        timeoutMs: Long = 5_000L
    ) {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        var observed = !expected
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { observed = it.hasMapView() }
            if (observed == expected) return
            Thread.sleep(25)
        }
        throw AssertionError(
            "Navigation map presence did not become $expected; last observed=$observed"
        )
    }

    private fun awaitSnapshotCaptured(
        activity: NavigationMapRuntimeActivity,
        timeoutMs: Long = 6_000L
    ): NavigationMapDiagnostics {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val diagnostics = activity.latestDiagnostics
            if (diagnostics.snapshotCaptureCount > 0) return diagnostics
            Thread.sleep(25)
        }
        throw AssertionError("Navigation map never captured a continuity snapshot")
    }

    private fun awaitRendererReplacementBridge(
        activity: NavigationMapRuntimeActivity,
        previousReplacementCount: Int,
        previousBridgeCount: Int,
        timeoutMs: Long = 5_000L
    ): NavigationMapDiagnostics {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val diagnostics = activity.latestDiagnostics
            if (diagnostics.mapReplacementCount > previousReplacementCount &&
                diagnostics.snapshotBridgeCount > previousBridgeCount
            ) {
                return diagnostics
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(25)
        }
        throw AssertionError(
            "Longdo renderer replacement callback did not announce continuity bridge"
        )
    }

    private fun awaitBridgeSettled(
        activity: NavigationMapRuntimeActivity,
        previousBridgeCount: Int,
        timeoutMs: Long = 8_000L
    ): NavigationMapDiagnostics {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val diagnostics = activity.latestDiagnostics
            if (diagnostics.snapshotBridgeCount > previousBridgeCount && !diagnostics.snapshotVisible) {
                return diagnostics
            }
            Thread.sleep(25)
        }
        throw AssertionError("Navigation map continuity bridge did not settle")
    }

    private fun awaitStopProgression(
        scenario: ActivityScenario<NavigationMapRuntimeActivity>,
        activity: NavigationMapRuntimeActivity,
        initial: NavigationMapDiagnostics
    ): NavigationMapDiagnostics {
        val expectedRoutes = initial.routeLayerRebuildCount + 1
        var diagnostics = activity.latestDiagnostics
        repeat(4) { attempt ->
            val previousRender = diagnostics.renderPassCount
            if (attempt == 0) {
                scenario.onActivity { it.advanceToSecondStop() }
            } else {
                // A foreground GPS refresh is the real Production retry trigger after a
                // transient Longdo pin/line mutation failure.
                scenario.onActivity { it.refreshRiderFix(100 + attempt) }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            diagnostics = awaitAppliedRender(activity, previousRender)
            if (diagnostics.routeLayerRebuildCount == expectedRoutes &&
                diagnostics.stopMutationCount > initial.stopMutationCount
            ) return diagnostics
        }
        throw AssertionError(
            "Stop progression overlays did not converge after bounded GPS retries: $diagnostics"
        )
    }

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

            val beforeGpsRender = runtimeActivity.latestDiagnostics.renderPassCount
            repeat(16) { index ->
                scenario.onActivity { it.refreshRiderFix(index + 1) }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }
            awaitAppliedRender(runtimeActivity, beforeGpsRender)

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

            val beforeEquivalentRefresh = runtimeActivity.latestDiagnostics
            scenario.onActivity { it.refreshEquivalentOrderSnapshot() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals("Equivalent backend refresh recreated MapGLSurfaceView", initialView, activity.mapViewIdentity())
                assertEquals("Equivalent backend refresh recreated Longdo map", initialSdkMap, activity.sdkMapIdentity())
                assertEquals(
                    "Equivalent backend refresh rebuilt unchanged route layers",
                    initialDiagnostics.routeLayerRebuildCount,
                    activity.latestDiagnostics.routeLayerRebuildCount
                )
                assertEquals(
                    "Equivalent backend refresh rebuilt unchanged stop pins",
                    initialDiagnostics.stopMutationCount,
                    activity.latestDiagnostics.stopMutationCount
                )
                assertEquals(
                    "Equivalent backend refresh changed SDK map creation count",
                    beforeEquivalentRefresh.mapCreatedCount,
                    activity.latestDiagnostics.mapCreatedCount
                )
                assertEquals(
                    "Equivalent backend refresh changed map replacement count",
                    beforeEquivalentRefresh.mapReplacementCount,
                    activity.latestDiagnostics.mapReplacementCount
                )
                assertEquals(
                    "Equivalent backend refresh invoked continuity bridge",
                    beforeEquivalentRefresh.snapshotBridgeCount,
                    activity.latestDiagnostics.snapshotBridgeCount
                )
            }

            val beforeTabUnmountDiagnostics = runtimeActivity.latestDiagnostics
            val beforeTabUnmount = beforeTabUnmountDiagnostics.renderPassCount
            scenario.onActivity { it.hideMapForAnotherTab() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            awaitMapPresence(scenario, expected = false)
            scenario.onActivity { it.showMapAfterReturningHome() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            awaitMapPresence(scenario, expected = true)
            awaitAppliedRender(runtimeActivity, beforeTabUnmount)
            val tabReturnDiagnostics = runtimeActivity.latestDiagnostics
            assertEquals(
                "Healthy tab remount should not invoke the continuity bridge",
                beforeTabUnmountDiagnostics.snapshotBridgeCount,
                tabReturnDiagnostics.snapshotBridgeCount
            )
            assertFalse(
                "Healthy tab remount unexpectedly covered the retained map with a snapshot",
                tabReturnDiagnostics.snapshotVisible
            )
            scenario.onActivity { activity ->
                assertEquals("Rider tab return recreated MapGLSurfaceView", initialView, activity.mapViewIdentity())
                assertEquals("Rider tab return recreated Longdo map", initialSdkMap, activity.sdkMapIdentity())
                assertEquals(1, activity.latestDiagnostics.viewCreatedCount)
                assertEquals(1, activity.latestDiagnostics.mapCreatedCount)
                assertEquals(
                    "Rider tab return rebuilt unchanged route layers",
                    initialDiagnostics.routeLayerRebuildCount,
                    activity.latestDiagnostics.routeLayerRebuildCount
                )
            }

            awaitSnapshotCaptured(runtimeActivity)
            val beforeResumeDiagnostics = runtimeActivity.latestDiagnostics
            val beforeResumeRender = beforeResumeDiagnostics.renderPassCount
            scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(300)
            scenario.moveToState(Lifecycle.State.RESUMED)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            awaitAppliedRender(runtimeActivity, beforeResumeRender)

            var resumedSdkMap = initialSdkMap
            scenario.onActivity { activity ->
                assertEquals("Resume recreated MapGLSurfaceView", initialView, activity.mapViewIdentity())
                resumedSdkMap = activity.sdkMapIdentity()
            }

            var resumeDiagnostics = runtimeActivity.latestDiagnostics
            if (resumedSdkMap != initialSdkMap) {
                resumeDiagnostics = awaitRendererReplacementBridge(
                    runtimeActivity,
                    beforeResumeDiagnostics.mapReplacementCount,
                    beforeResumeDiagnostics.snapshotBridgeCount
                )
                resumeDiagnostics = awaitBridgeSettled(
                    runtimeActivity,
                    beforeResumeDiagnostics.snapshotBridgeCount
                )
            } else if (resumeDiagnostics.snapshotBridgeCount > beforeResumeDiagnostics.snapshotBridgeCount) {
                resumeDiagnostics = awaitBridgeSettled(
                    runtimeActivity,
                    beforeResumeDiagnostics.snapshotBridgeCount
                )
            }
            assertTrue(resumeDiagnostics.snapshotCaptureCount > 0)
            assertEquals(0, resumeDiagnostics.snapshotBridgeFailureCount)
            assertFalse("Continuity snapshot remained stuck over live map", resumeDiagnostics.snapshotVisible)

            scenario.onActivity { activity ->
                assertEquals(1, activity.latestDiagnostics.viewCreatedCount)
                if (resumedSdkMap == initialSdkMap) {
                    assertEquals(
                        "Healthy background/resume should not invoke the continuity bridge",
                        beforeResumeDiagnostics.snapshotBridgeCount,
                        activity.latestDiagnostics.snapshotBridgeCount
                    )
                    assertEquals(1, activity.latestDiagnostics.mapCreatedCount)
                } else {
                    assertTrue("Longdo map replacement was not recorded", activity.latestDiagnostics.mapReplacementCount >= 1)
                    assertTrue("Longdo map replacement did not increment map creation count", activity.latestDiagnostics.mapCreatedCount >= 2)
                    assertTrue(
                        "Longdo replacement did not invoke frozen-frame continuity bridge",
                        activity.latestDiagnostics.snapshotBridgeCount > beforeResumeDiagnostics.snapshotBridgeCount
                    )
                }
            }

            val progressedDiagnostics = awaitStopProgression(scenario, runtimeActivity, resumeDiagnostics)
            assertEquals(
                "Stop progression rebuilt route layers more than once",
                resumeDiagnostics.routeLayerRebuildCount + 1,
                progressedDiagnostics.routeLayerRebuildCount
            )
            scenario.onActivity { activity ->
                assertEquals("Stop progression recreated MapGLSurfaceView", initialView, activity.mapViewIdentity())
                assertEquals("Stop progression unexpectedly replaced the current Longdo map", resumedSdkMap, activity.sdkMapIdentity())
                assertEquals(
                    resumeDiagnostics.routeLayerRebuildCount + 1,
                    activity.latestDiagnostics.routeLayerRebuildCount
                )
                assertTrue(activity.latestDiagnostics.stopMutationCount > initialDiagnostics.stopMutationCount)
            }
        }
    }

    @Test
    fun retainedMapIsDisposedWhenActivityDiesOffHome() {
        ActivityScenario.launch(NavigationMapRuntimeActivity::class.java).use { scenario ->
            lateinit var firstActivity: NavigationMapRuntimeActivity
            scenario.onActivity { firstActivity = it }
            assertTrue(
                "Initial navigation map did not become ready",
                firstActivity.mapReady.await(30, TimeUnit.SECONDS)
            )

            var oldActivityIdentity = 0
            var oldViewIdentity = 0
            scenario.onActivity { activity ->
                oldActivityIdentity = System.identityHashCode(activity)
                oldViewIdentity = activity.mapViewIdentity()
                assertTrue("Initial map View does not use Activity context", activity.mapViewUsesActivityContext())
                activity.hideMapForAnotherTab()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.recreate()

            lateinit var recreatedActivity: NavigationMapRuntimeActivity
            scenario.onActivity { recreatedActivity = it }
            assertTrue(
                "Navigation map did not recover after Activity recreation",
                recreatedActivity.mapReady.await(30, TimeUnit.SECONDS)
            )
            scenario.onActivity { activity ->
                assertNotEquals(
                    "Activity recreation unexpectedly reused the destroyed Activity",
                    oldActivityIdentity,
                    System.identityHashCode(activity)
                )
                assertNotEquals(
                    "Activity recreation reused a MapGLSurfaceView backed by the destroyed Activity",
                    oldViewIdentity,
                    activity.mapViewIdentity()
                )
                assertTrue(
                    "Recreated map View retained the wrong Activity context",
                    activity.mapViewUsesActivityContext()
                )
                assertEquals(1, activity.latestDiagnostics.viewCreatedCount)
                assertEquals(1, activity.latestDiagnostics.mapCreatedCount)
            }
        }
    }
}
