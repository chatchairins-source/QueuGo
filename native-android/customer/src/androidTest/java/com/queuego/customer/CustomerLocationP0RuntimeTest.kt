package com.queuego.customer

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import org.junit.Assert.*
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertTextContains
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.click

/** Isolated Android UI harness: real picker/MapLibre GL, no account, backend writes or orders. */
class CustomerLocationP0RuntimeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun currentNativeMapAndPlatformLocationRuntimeClassesArePackaged() {
        listOf(
            "org.maplibre.android.MapLibre",
            "org.maplibre.android.maps.MapView",
            "android.location.LocationManager",
            "android.location.LocationListener"
        ).forEach { className ->
            assertNotNull("Missing current native map/location runtime: $className", Class.forName(className))
        }
    }

    @Test fun appCameraMovesDoNotBecomeUserDeliverySelections() {
        val ready = AtomicBoolean(false)
        val selections = AtomicInteger(0)
        val target = mutableStateOf<com.queuego.shared.QgMapPoint?>(null)
        val token = mutableStateOf(0)
        compose.setContent {
            com.queuego.shared.QgLocationPickerMap(
                initialPoint = null, recenterPoint = target.value, recenterToken = token.value,
                modifier = Modifier.fillMaxSize().testTag("camera-owner-map"),
                onPointSelected = { selections.incrementAndGet() }, onReady = { ready.set(it) })
        }
        compose.waitUntil(30_000) { ready.get() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("Fallback camera became a delivery selection", 0, selections.get())
            target.value = com.queuego.shared.QgMapPoint(14.995, 103.103, 0)
            token.value = 1
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("App GPS recenter echoed as a user pan", 0, selections.get())
        }
        compose.onNodeWithTag("camera-owner-map").performTouchInput { swipeLeft(durationMillis = 300) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("Panning must not choose a delivery stop", 0, selections.get())
        }
        compose.onNodeWithTag("camera-owner-map").performTouchInput { click(center) }
        compose.waitUntil(10_000) { selections.get() == 1 }
    }

    @Test fun thirtyOpenGpsSaveCloseCyclesAndEarlyBackground() {
        val visible = mutableStateOf(false)
        val saved = AtomicReference<CustomerLocation?>()
        val gps = mutableStateOf<CustomerLocation?>(null)
        compose.setContent {
            if (visible.value) CustomerLocationPickerScreen(
                location = gps.value, address = "", busy = false,
                searchPlaces = { emptyList() },
                readGps = { CustomerLocation(14.995, 103.103, "") },
                resolveAddress = { _, _ -> "Isolated Android test address" },
                onSave = { saved.set(it); visible.value = false },
                onBack = { visible.value = false }
            )
        }
        repeat(30) { index ->
            compose.runOnIdle { gps.value = null; visible.value = true }
            // Exercise GL creation/disposal while the Activity pauses, including before mapReady.
            if (index % 3 == 0) {
                compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            }
            compose.onNodeWithText("ใช้ตำแหน่ง GPS ปัจจุบัน").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("กำลังอ่านตำแหน่ง GPS…"))
                    .fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("customer-delivery-address")
                .performScrollTo().performTextReplacement("Cycle $index manual address")
            if (index == 0 || index == 29) {
                compose.waitUntil(30_000) {
                    compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "แผนที่พร้อมใช้งาน"))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.activityRule.scenario.onActivity { activity ->
                    val keyboard = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                    keyboard.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
                }
                compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "แผนที่พร้อมใช้งาน"))
                    .performScrollTo()
                assertRenderedRoadDetail(index)
                val directory = File(compose.activity.filesDir, "p0-evidence").apply { mkdirs() }
                requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()) { "Android screenshot was unavailable" }.let { bitmap ->
                    File(directory, "picker-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
            compose.onNodeWithText("บันทึกที่อยู่นี้").performScrollTo().performClick()
            compose.runOnIdle {
                assertFalse("Picker did not close at cycle $index", visible.value)
                assertEquals("Cycle $index manual address", saved.get()?.address)
                assertEquals(14.995, requireNotNull(saved.get()).latitude, 0.000001)
            }
        }
    }

    /** Inspect MapLibre's actual rendered pixels, excluding controls and the center pin.
     * A gray canvas or a single pin must never pass merely because semantics say ready.
     */
    private fun assertRenderedRoadDetail(index: Int) {
        val completed = java.util.concurrent.CountDownLatch(1)
        val rendered = AtomicReference<Bitmap?>()
        fun findMap(view: android.view.View): org.maplibre.android.maps.MapView? {
            if (view is org.maplibre.android.maps.MapView) return view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) {
                findMap(view.getChildAt(i))?.let { return it }
            }
            return null
        }
        compose.activityRule.scenario.onActivity { activity ->
            requireNotNull(findMap(activity.window.decorView)).getMapAsync { map ->
                map.snapshot { bitmap -> rendered.set(bitmap); completed.countDown() }
            }
        }
        assertTrue("Rendered map snapshot timed out", completed.await(30, java.util.concurrent.TimeUnit.SECONDS))
        val bitmap = requireNotNull(rendered.get())
        try {
            val colors = mutableSetOf<Int>()
            var edges = 0
            var samples = 0
            for (y in bitmap.height / 10 until bitmap.height * 9 / 10 step 3) {
                for (x in bitmap.width / 10 until bitmap.width * 9 / 10 step 3) {
                    if (kotlin.math.abs(x - bitmap.width / 2) < 30 && kotlin.math.abs(y - bitmap.height / 2) < 30) continue
                    val pixel = bitmap.getPixel(x, y)
                    colors.add(pixel and 0x00F8F8F8)
                    val next = bitmap.getPixel((x + 3).coerceAtMost(bitmap.width - 1), y)
                    if (kotlin.math.abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(next)) +
                        kotlin.math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(next)) +
                        kotlin.math.abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(next)) > 40) edges++
                    samples++
                }
            }
            val directory = File(compose.activity.filesDir, "p0-evidence").apply { mkdirs() }
            File(directory, "road-detail-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(directory, "road-detail-$index.txt").writeText("colors=${colors.size} edges=$edges samples=$samples")
            assertTrue("Map has no rendered geographic detail: ${colors.size} colors", colors.size >= 16)
            assertTrue("Map has insufficient geographic edges: $edges/$samples", edges > samples / 1000)
        } finally { bitmap.recycle() }
    }

    @Test fun selectedDraftSurvivesSavedStateRestore() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CustomerLocationPickerScreen(
                location = CustomerLocation(14.995, 103.103, "Original"),
                address = "Original", busy = false,
                searchPlaces = { emptyList() },
                readGps = { CustomerLocation(14.995, 103.103, "") },
                resolveAddress = { _, _ -> "Generated" },
                onSave = {}, onBack = {})
        }
        compose.onNodeWithTag("customer-delivery-address")
            .performScrollTo().performTextReplacement("Restored manual address")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("customer-delivery-address")
            .assertTextContains("Restored manual address")
    }

    @Test fun accountBuildInfoShowsExactCompiledSource() {
        compose.setContent {
            com.queuego.shared.QgTestBuildInfo("Customer", BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE, BuildConfig.GIT_SHA, BuildConfig.BUILT_UTC, BuildConfig.BUILD_TYPE)
        }
        compose.onNodeWithText("ข้อมูลเวอร์ชันทดสอบ").performClick()
        compose.onNodeWithText("Git SHA: " + BuildConfig.GIT_SHA, substring = true).assertExists()
        compose.onNodeWithText("versionCode: " + BuildConfig.VERSION_CODE, substring = true).assertExists()
    }
}
