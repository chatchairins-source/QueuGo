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
import androidx.compose.ui.test.click

/** Isolated Android UI harness: real picker/MapLibre GL, no account, backend writes or orders. */
class CustomerLocationP0RuntimeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun onlineMapUsesTextureOnLegacyAndroidAndDefaultSurfaceOnAndroid12Plus() {
        val ready = AtomicBoolean(false)
        compose.setContent {
            com.queuego.shared.QgLocationPickerMap(
                initialPoint = null, recenterPoint = null, recenterToken = 0,
                modifier = Modifier.fillMaxSize(),
                onPointSelected = {}, onReady = { ready.set(it) })
        }
        compose.waitUntil(30_000) { ready.get() }
        fun contains(view: android.view.View, kind: Class<*>): Boolean {
            if (kind.isInstance(view)) return true
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount) {
                if (contains(view.getChildAt(index), kind)) return true
            }
            return false
        }
        compose.activityRule.scenario.onActivity { activity ->
            val root = activity.window.decorView
            val legacyAndroid = android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.R
            assertEquals("Actual online map TextureView mode must match legacy Android workaround",
                legacyAndroid, contains(root, android.view.TextureView::class.java))
            assertEquals("Legacy Android must not retain a map SurfaceView",
                !legacyAndroid, contains(root, android.view.SurfaceView::class.java))
        }
    }

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
        val invalidations = AtomicInteger(0)
        val requested = InstrumentationRegistry.getArguments().getString("mapSurface")
        val texture = when (requested) { null -> null; "surface" -> false; "texture" -> true; else -> error("Unknown mapSurface") }
        val beforePan = AtomicInteger(-1)
        fun diag(event: String) {
            android.util.Log.i("MAPDIAG", "event=$event mode=$requested beforePan=${beforePan.get()} invalidations=${invalidations.get()} ready=${ready.get()} selections=${selections.get()}")
        }
        val target = mutableStateOf<com.queuego.shared.QgMapPoint?>(null)
        val token = mutableStateOf(0)
        compose.setContent {
            com.queuego.shared.QgLocationPickerMap(
                initialPoint = null, recenterPoint = target.value, recenterToken = token.value,
                modifier = Modifier.fillMaxSize().testTag("camera-owner-map"),
                diagnosticTexture = texture, diagnosticEvent = if (texture != null) ::diag else null,
                onPointSelected = { selections.incrementAndGet() },
                onReady = { ready.set(it); if (!it) invalidations.incrementAndGet(); diag("readyChange") })
        }
        compose.waitUntil(30_000) { ready.get() }
        compose.waitForIdle()
        val beforeRecenter = invalidations.get()
        compose.runOnIdle {
            assertEquals("Fallback camera became a delivery selection", 0, selections.get())
            target.value = com.queuego.shared.QgMapPoint(14.995, 103.103, 0)
            token.value = 1
        }
        compose.waitUntil(30_000) { invalidations.get() > beforeRecenter && ready.get() }
        compose.runOnIdle {
            assertEquals("App GPS recenter echoed as a user pan", 0, selections.get())
        }
        beforePan.set(invalidations.get())
        diag("panStart")
        // Android 11 MapLibre uses TextureView. Inject the pan into the actual
        // platform MapView instead of a Compose wrapper semantics node: the
        // latter may consume a synthetic Compose swipe without ever starting
        // the native MapLibre gesture on API 30.
        swipeOnActualMapView()
        diag("waitAfterPan")
        compose.waitUntil(30_000) { invalidations.get() > beforePan.get() && ready.get() }
        compose.runOnIdle {
            assertEquals("Panning must not choose a delivery stop", 0, selections.get())
        }
        compose.onNodeWithTag("camera-owner-map").performTouchInput { click(center) }
        compose.waitUntil(10_000) { selections.get() == 1 }
    }

    /** A real screen-coordinate touch stream must reach MapLibre's platform view.
     * Compose's gesture helper on the outer Box is not sufficient proof that
     * the AndroidView/TextureView received a drag on legacy Android.
     */
    private fun swipeOnActualMapView() {
        val bounds = android.graphics.Rect()
        compose.activityRule.scenario.onActivity { activity ->
            fun findMap(view: android.view.View): org.maplibre.android.maps.MapView? {
                if (view is org.maplibre.android.maps.MapView) return view
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) {
                    findMap(view.getChildAt(i))?.let { return it }
                }
                return null
            }
            val map = requireNotNull(findMap(activity.window.decorView)) {
                "Native MapLibre MapView is absent during pan regression"
            }
            assertTrue("Native MapLibre MapView must be visible", map.getGlobalVisibleRect(bounds))
        }
        assertTrue("Native map is too narrow for a drag", bounds.width() >= 200)
        assertTrue("Native map is too short for a drag", bounds.height() >= 200)
        val startX = bounds.left + bounds.width() * 0.75f
        val endX = bounds.left + bounds.width() * 0.25f
        val y = bounds.top + bounds.height() * 0.5f
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = android.os.SystemClock.uptimeMillis()
        fun inject(action: Int, x: Float) {
            val event = android.view.MotionEvent.obtain(
                downTime, android.os.SystemClock.uptimeMillis(), action, x, y, 0
            )
            try {
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                assertTrue("Failed to inject native MapView touch action $action",
                    automation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
        inject(android.view.MotionEvent.ACTION_DOWN, startX)
        for (step in 1..15) {
            android.os.SystemClock.sleep(20)
            inject(android.view.MotionEvent.ACTION_MOVE,
                startX + (endX - startX) * step / 15f)
        }
        inject(android.view.MotionEvent.ACTION_UP, endX)
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
