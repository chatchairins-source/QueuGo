package com.queuego.ridernavigationmap

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class InstalledThailandMapTest {
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun installedCountryArchivePaintsRoadsInMultipleRegions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun capture(name: String) {
            val directory = File(context.getExternalFilesDir(null), "offline-map-evidence").apply { mkdirs() }
            val screenshot = File(directory, "$name.png")
            screenshot.outputStream().use {
                assertTrue(instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            // Scoped-storage API keeps generated public evidence after UTP
            // removes the test APK, without shell access to private app data.
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$name.png")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/QueueGoOfflineEvidence/")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            resolver.openOutputStream(uri).use { target ->
                screenshot.inputStream().use { source ->
                    assertEquals(screenshot.length(), source.copyTo(requireNotNull(target)))
                }
            }
            resolver.update(uri, android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            resolver.openFileDescriptor(uri, "r").use {
                assertEquals("Retained screenshot size differs", screenshot.length(), requireNotNull(it).statSize)
            }
        }
        val offlineRequired = InstrumentationRegistry.getArguments().getString("offlineRequired") == "true"
        if (offlineRequired) {
            assertTrue("Nationwide archive must be installed", context.assets.list("maps")?.contains("thailand-shortbread.pmtiles") == true)
            // UTP installs/configures the APK after the runner's radio setup.
            // Enforce the condition in the actual test process, after installation.
            shell("cmd connectivity airplane-mode enable")
            shell("svc wifi disable")
            shell("svc data disable")
            val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
            val offlineDeadline = android.os.SystemClock.elapsedRealtime() + 15_000
            while (connectivity.activeNetwork != null && android.os.SystemClock.elapsedRealtime() < offlineDeadline) {
                Thread.sleep(200)
            }
            assertNull("Offline test still has an active network", connectivity.activeNetwork)
            assertEquals(1, android.provider.Settings.Global.getInt(context.contentResolver, "airplane_mode_on", 0))
        }
        // Existing fixture-only CI remains separate. A skipped test is not an offline PASS.
        assumeTrue(context.assets.list("maps")?.contains("thailand-shortbread.pmtiles") == true)
        // Observe actual glyph resource URLs without rewriting them or drawing labels.
        // Feature-index counts alone cannot establish visible text (Issue #750).
        instrumentation.runOnMainSync {
            org.maplibre.android.MapLibre.getInstance(context)
            org.maplibre.android.storage.FileSource.getInstance(context).setResourceTransform { kind, url ->
                if (url.startsWith("asset://fonts/")) {
                    android.util.Log.d("QueueGoLabelQA", "glyph_resource kind=$kind url=$url")
                }
                url
            }
        }
        val intent = Intent(context, NavigationMapRuntimeActivity::class.java)
            .putExtra("installedThailandMap", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<NavigationMapRuntimeActivity>(intent).use { scenario ->
            lateinit var activity: NavigationMapRuntimeActivity
            scenario.onActivity { activity = it }
            assertTrue("Installed archive style failed", activity.mapReady.await(45, TimeUnit.SECONDS))
            val locations = listOf(
                Triple("bangkok", 13.7563, 100.5018),
                Triple("buriram", 14.993, 103.1039),
                Triple("chiangmai", 18.7883, 98.9853),
                Triple("phuket", 7.8804, 98.3923),
                Triple("narathiwat", 6.4255, 101.8253)
            )
            for ((name, lat, lon) in locations) {
                if (offlineRequired) assertNull(context.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork)
                scenario.onActivity { it.showCountryPosition(lat, lon) }
                var paintedRoads = 0
                var viewportReady = false
                var previousPose: List<Double>? = null
                var stableSince = android.os.SystemClock.elapsedRealtime()
                val deadline = android.os.SystemClock.elapsedRealtime() + 45_000
                while (!viewportReady && android.os.SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity { it.inspectMap { map ->
                        val style = requireNotNull(map.style)
                        assertNull(style.getLayer("queuego-web-basemap-layer"))
                        val camera = map.cameraPosition
                        val target = camera.target ?: return@inspectMap
                        val idle = it.latestCountryIdleCamera ?: return@inspectMap
                        val idlePoint = idle.target ?: return@inspectMap
                        val pose = listOf(target.latitude, target.longitude, camera.zoom, camera.bearing, camera.tilt)
                        val previous = previousPose
                        if (previous == null || pose.zip(previous).any { pair -> kotlin.math.abs(pair.first - pair.second) > 0.000001 }) {
                            stableSince = android.os.SystemClock.elapsedRealtime()
                            previousPose = pose
                        }
                        val atDestination = kotlin.math.abs(target.latitude - lat) < 0.0005 && kotlin.math.abs(target.longitude - lon) < 0.0005
                        val fullyIdle = kotlin.math.abs(target.latitude - idlePoint.latitude) < 0.000001 &&
                            kotlin.math.abs(target.longitude - idlePoint.longitude) < 0.000001 &&
                            kotlin.math.abs(camera.zoom - idle.zoom) < 0.001 &&
                            kotlin.math.abs(camera.tilt - idle.tilt) < 0.001
                        if (atDestination && fullyIdle) paintedRoads = map.queryRenderedFeatures(
                            android.graphics.RectF(0f, 0f, 1080f, 2280f),
                            "queuego-road-residential", "queuego-road-primary",
                            "queuego-road-secondary", "queuego-road-tertiary", "queuego-road-service"
                        ).size
                        viewportReady = atDestination && fullyIdle && paintedRoads > 0 &&
                            android.os.SystemClock.elapsedRealtime() - stableSince >= 750
                        if (viewportReady) android.util.Log.i("QueueGoMapFrameQA",
                            "region=$name expected=$lat,$lon actual=${target.latitude},${target.longitude} zoom=${camera.zoom} tilt=${camera.tilt} bearing=${camera.bearing} fully_idle=true")
                    } }
                    Thread.sleep(200)
                }
                assertTrue("No fully loaded stable country viewport at expected location: $name", viewportReady)
                // Geometry can paint before glyph requests and symbol placement finish.
                // The named Bangkok streets are the label acceptance sample; do not
                // certify an empty label layer merely because roads have rendered.
                var paintedLabels = 0
                if (name == "bangkok") {
                    scenario.onActivity { it.inspectMap { map ->
                        val liveStyle = org.json.JSONObject(requireNotNull(map.style).json)
                        android.util.Log.d("QueueGoLabelQA", "live_glyphs=${liveStyle.optString("glyphs", "missing")}")
                        for (id in listOf("queuego-label-street_labels", "queuego-label-place_labels", "queuego-label-pois")) {
                            val layer = map.style?.getLayerAs<org.maplibre.android.style.layers.SymbolLayer>(id)
                            val fonts = layer?.textFont?.value?.joinToString("|") ?: "expression-or-missing"
                            android.util.Log.d("QueueGoLabelQA", "live_layer=$id fontstack=$fonts")
                        }
                    } }
                    val labelDeadline = android.os.SystemClock.elapsedRealtime() + 30_000
                    while (paintedLabels == 0 && android.os.SystemClock.elapsedRealtime() < labelDeadline) {
                        scenario.onActivity { it.inspectMap { map ->
                            paintedLabels = map.queryRenderedFeatures(
                                android.graphics.RectF(0f, 0f, 1080f, 2280f),
                                "queuego-label-street_labels", "queuego-label-place_labels", "queuego-label-pois"
                            ).size
                        } }
                        Thread.sleep(200)
                    }
                }
                scenario.onActivity { it.inspectMap { map ->
                    val labels = map.queryRenderedFeatures(
                        android.graphics.RectF(0f, 0f, 1080f, 2280f),
                        "queuego-label-street_labels", "queuego-label-place_labels", "queuego-label-pois"
                    )
                    android.util.Log.d("QueueGoLabelQA", "region=$name indexed_labels=${labels.size} properties=${labels.take(3).map { feature -> feature.properties() }}")
                } }
                // A symbol may enter the rendered-feature index before its fade-in
                // reaches visible opacity. Retain the settled frame as evidence.
                if (name == "bangkok" && paintedLabels > 0) Thread.sleep(1_000)
                capture(name)
                if (name == "bangkok") {
                    // Diagnostic comparisons only; preserve the original perspective
                    // frame above as the visual acceptance sample. Never redraw text.
                    Thread.sleep(5_000)
                    capture("bangkok-settled")
                    scenario.onActivity { it.inspectMap { map ->
                        map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder(map.cameraPosition)
                            .tilt(0.0).bearing(0.0).build()
                    } }
                    Thread.sleep(2_000)
                    capture("bangkok-flat-diagnostic")
                }
                if (offlineRequired) assertNull(context.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork)
                if (name == "bangkok") assertTrue("Offline Bangkok labels did not paint; see retained screenshot", paintedLabels > 0)
            }
        }
    }
}
