package com.queuego.ridernavigationmap

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class NavigationMapRuntimeTest {
    private fun awaitRender(
        activity: NavigationMapRuntimeActivity,
        previous: Int,
        timeoutMs: Long = 5_000L
    ): NavigationMapDiagnostics {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val value = activity.latestDiagnostics
            if (value.renderPassCount > previous) return value
            Thread.sleep(25)
        }
        throw AssertionError("QueueGo MapLibre state was not rendered")
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
        throw AssertionError("MapLibre presence did not become $expected")
    }

    @Test
    fun gpsRefreshTabReturnAndOverlayUpdatesReuseSameMapView() {
        ActivityScenario.launch(NavigationMapRuntimeActivity::class.java).use { scenario ->
            lateinit var activity: NavigationMapRuntimeActivity
            scenario.onActivity { activity = it }
            assertTrue(
                "QueueGo MapLibre navigation map did not become ready",
                activity.mapReady.await(30, TimeUnit.SECONDS)
            )

            var viewIdentity = 0
            var initial = activity.latestDiagnostics
            scenario.onActivity {
                viewIdentity = it.mapViewIdentity()
                initial = it.latestDiagnostics
                assertTrue(it.mapViewUsesActivityContext())
            }

            repeat(30) { index ->
                val before = activity.latestDiagnostics.renderPassCount
                scenario.onActivity { it.refreshRiderFix(index + 1) }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                awaitRender(activity, before)
            }

            scenario.onActivity {
                assertEquals(viewIdentity, it.mapViewIdentity())
                assertEquals(1, it.latestDiagnostics.viewCreatedCount)
                assertEquals(1, it.latestDiagnostics.mapCreatedCount)
            }

            val beforeEquivalent = activity.latestDiagnostics
            scenario.onActivity { it.refreshEquivalentOrderSnapshot() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                // mutableStateOf uses structural equality: copying an equal
                // snapshot must not schedule a render just to satisfy this test.
                assertEquals(
                    "Equivalent snapshot unnecessarily rendered the map",
                    beforeEquivalent.renderPassCount,
                    it.latestDiagnostics.renderPassCount
                )
                assertEquals(viewIdentity, it.mapViewIdentity())
                assertEquals(
                    beforeEquivalent.routeLayerRebuildCount,
                    it.latestDiagnostics.routeLayerRebuildCount
                )
            }

            val beforeAdvance = activity.latestDiagnostics
            scenario.onActivity { it.advanceToSecondStop() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val advanced = awaitRender(activity, beforeAdvance.renderPassCount)
            assertTrue(
                advanced.routeLayerRebuildCount >
                    beforeAdvance.routeLayerRebuildCount
            )

            scenario.onActivity { it.hideMapForAnotherTab() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            awaitMapPresence(scenario, false)
            scenario.onActivity { it.showMapAfterReturningHome() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            awaitMapPresence(scenario, true)
            scenario.onActivity {
                assertEquals(
                    "Tab return recreated QueueGo MapLibre view",
                    viewIdentity,
                    it.mapViewIdentity()
                )
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertEquals(
                    "Background/resume recreated QueueGo MapLibre view",
                    viewIdentity,
                    it.mapViewIdentity()
                )
            }

            scenario.onActivity {
                val before = it.mapViewIdentity()
                it.showDemandHeatmapFixture(3)
                assertEquals(before, it.mapViewIdentity())
            }
        }
    }

    private fun awaitActualMapZoom(
        scenario: ActivityScenario<NavigationMapRuntimeActivity>,
        expected: Double,
        timeoutMs: Long = 12_000L
    ) {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        var lastObserved = Double.NaN
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val ready = CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.inspectMap { map ->
                    lastObserved = map.cameraPosition.zoom
                    ready.countDown()
                }
            }
            assertTrue("MapLibre camera callback timed out", ready.await(2, TimeUnit.SECONDS))
            if (abs(lastObserved - expected) <= 0.25) return
            Thread.sleep(100)
        }
        throw AssertionError(
            "Installed MapLibre FOLLOW zoom did not reach $expected; last=$lastObserved"
        )
    }

    @Test
    fun installedMapLibreFollowCameraZoomRespondsToActualRiderSpeed() {
        ActivityScenario.launch(NavigationMapRuntimeActivity::class.java).use { scenario ->
            lateinit var activity: NavigationMapRuntimeActivity
            scenario.onActivity { activity = it }
            assertTrue("Installed navigation map did not load", activity.mapReady.await(30, TimeUnit.SECONDS))
            var originalMapIdentity = 0
            scenario.onActivity { originalMapIdentity = it.mapViewIdentity() }

            // Exercise the real MapLibre camera, not just navigationZoom() pure policy.
            // This is a test fixture, not an authenticated road GPS or visual parity claim.
            scenario.onActivity { it.setRiderSpeedForCamera(0f) }
            awaitActualMapZoom(scenario, 18.0)
            scenario.onActivity { it.setRiderSpeedForCamera(20f) }
            awaitActualMapZoom(scenario, 15.0)
            scenario.onActivity { it.setRiderSpeedForCamera(8f) }
            awaitActualMapZoom(scenario, 17.0)
            scenario.onActivity {
                assertEquals(
                    "Speed-based FOLLOW camera must not recreate the installed MapLibre view",
                    originalMapIdentity,
                    it.mapViewIdentity()
                )
            }
        }
    }

    @Test
    fun manualDragReportsGestureButTapDoesNot() {
        ActivityScenario.launch(NavigationMapRuntimeActivity::class.java).use { scenario ->
            lateinit var activity: NavigationMapRuntimeActivity
            scenario.onActivity { activity = it }
            assertTrue(activity.mapReady.await(30, TimeUnit.SECONDS))

            scenario.onActivity { it.dispatchTapOnMap() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertEquals(0, it.userGestureCount)
            }

            scenario.onActivity { it.dispatchDragOnMap() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                // Existing production contract: suspend FOLLOW at drag start,
                // then restart the idle grace period when fingers are lifted.
                assertEquals(2, it.userGestureCount)
                it.dispatchTapOnMap()
                assertEquals("A tap must not restart the manual-pan timer", 2, it.userGestureCount)
                it.dispatchDragOnMap()
                assertEquals("Each drag must report exactly start and finish", 4, it.userGestureCount)
            }
        }
    }

    @Test
    fun twoFingerPinchPreservesWebFollowInsteadOfEnteringFreePan() {
        ActivityScenario.launch(NavigationMapRuntimeActivity::class.java).use { scenario ->
            lateinit var activity: NavigationMapRuntimeActivity
            scenario.onActivity { activity = it }
            assertTrue(activity.mapReady.await(30, TimeUnit.SECONDS))

            scenario.onActivity { it.dispatchPinchOnMap() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertEquals(
                    "QueueGo Web two-finger pinch must not release Rider FOLLOW",
                    0, it.userGestureCount
                )
                it.refreshRiderFix(3)
            }
            // The 110 ms re-anchor occurs after the Android pinch is committed.
            Thread.sleep(NAVIGATION_TRANSFORM_REANCHOR_MS + 250L)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertTrue("Rider MapView must remain mounted after pinch", it.hasMapView())
                assertEquals(0, it.userGestureCount)
            }
        }
    }

    @Test
    fun activityRecreationCreatesFreshMapViewForFreshActivityContext() {
        ActivityScenario.launch(NavigationMapRuntimeActivity::class.java).use { scenario ->
            lateinit var activity: NavigationMapRuntimeActivity
            scenario.onActivity { activity = it }
            assertTrue(activity.mapReady.await(30, TimeUnit.SECONDS))

            var oldActivity = 0
            var oldView = 0
            scenario.onActivity {
                oldActivity = System.identityHashCode(it)
                oldView = it.mapViewIdentity()
            }

            scenario.recreate()

            lateinit var recreated: NavigationMapRuntimeActivity
            scenario.onActivity { recreated = it }
            assertTrue(recreated.mapReady.await(30, TimeUnit.SECONDS))
            scenario.onActivity {
                assertNotEquals(oldActivity, System.identityHashCode(it))
                assertNotEquals(oldView, it.mapViewIdentity())
                assertTrue(it.mapViewUsesActivityContext())
            }
        }
    }
}
