package com.queuego.ridernavigationmap

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class InstalledThailandMapTest {
    // Compare actual emulator framebuffer pixels, not symbol feature indexes.
    // Ignore status/navigation bars, where system time/icons can change.
    private fun changedMapPixels(first: Bitmap, second: Bitmap): Int {
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        var changed = 0
        for (y in first.height / 10 until first.height * 9 / 10 step 3) {
            for (x in first.width / 10 until first.width * 9 / 10 step 3) {
                val a = first.getPixel(x, y)
                val b = second.getPixel(x, y)
                val difference = kotlin.math.abs(Color.red(a) - Color.red(b)) +
                    kotlin.math.abs(Color.green(a) - Color.green(b)) +
                    kotlin.math.abs(Color.blue(a) - Color.blue(b))
                if (difference >= 80) changed++
            }
        }
        return changed
    }

    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun installedCountryArchivePaintsRoadsInMultipleRegions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
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
        // Probe real installed glyph bytes, not just a source-tree hash. A missing
        // range must fail before a visually blank screenshot can be misdiagnosed.
        val glyphRanges = listOf("0-255.pbf", "3584-3839.pbf")
        val glyphEvidence = glyphRanges.map { range ->
            val assetPath = "fonts/Noto Sans Regular/$range"
            val byteCount = context.assets.open(assetPath).use { it.readBytes().size }
            assertTrue("Bundled glyph range is empty: $assetPath", byteCount > 100)
            "$range:$byteCount"
        }
        Log.i("QueueGoLabelQA", "installed_glyph_ranges=" + glyphEvidence.joinToString(","))
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
                val deadline = android.os.SystemClock.elapsedRealtime() + 45_000
                while (paintedRoads == 0 && android.os.SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity { it.inspectMap { map ->
                        val style = requireNotNull(map.style)
                        assertNull(style.getLayer("queuego-web-basemap-layer"))
                        val target = map.cameraPosition.target
                        if (target != null && kotlin.math.abs(target.latitude - lat) < 0.01 && kotlin.math.abs(target.longitude - lon) < 0.01) paintedRoads = map.queryRenderedFeatures(
                            android.graphics.RectF(0f, 0f, 1080f, 2280f),
                            "queuego-road-residential", "queuego-road-primary",
                            "queuego-road-secondary", "queuego-road-tertiary", "queuego-road-service"
                        ).size
                    } }
                    Thread.sleep(200)
                }
                assertTrue("No painted vector roads: $name", paintedRoads > 0)
                // Geometry can paint before glyph requests and symbol placement finish.
                // The named Bangkok streets are the label acceptance sample; do not
                // certify an empty label layer merely because roads have rendered.
                var paintedLabels = 0
                if (name == "bangkok") {
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
                if (name == "bangkok") {
                    // Query the *same* rendered symbol layers as the visual gate,
                    // and record whether their features carry usable name values.
                    // This separates a missing tile name from a missing glyph or
                    // text-placement problem without drawing any artificial label.
                    val sourceNameEvidence = mutableListOf<String>()
                    scenario.onActivity { it.inspectMap { map ->
                        val viewport = android.graphics.RectF(0f, 0f, 1080f, 2280f)
                        listOf("street_labels", "place_labels", "pois").forEach { layer ->
                            val features = map.queryRenderedFeatures(viewport, "queuego-label-$layer")
                            val names = features.mapNotNull { feature ->
                                if (!feature.hasProperty("name")) null
                                else runCatching { feature.getStringProperty("name") }.getOrNull()
                                    ?.takeIf { it.isNotBlank() }
                            }
                            sourceNameEvidence.add(
                                "$layer:features=${features.size},named=${names.size}," +
                                    "samples=${names.take(3).joinToString("|") { it.take(45) }}"
                            )
                        }
                    } }
                    Log.i("QueueGoLabelQA", "bangkok_source_names=" + sourceNameEvidence.joinToString(";"))
                }
                // A positive rendered-feature index can still correspond to
                // completely invisible text (Issue #750). Toggle only the real
                // MapLibre symbol layers and compare stable framebuffer pixels.
                // No fake labels, screenshot overlays or Production changes.
                var visibleLabelPixelDelta = -1
                var hiddenFrameJitter = -1
                var labelDiagnosticFiles = emptyList<File>()
                val screenshotBitmap: Bitmap = if (name == "bangkok") {
                    val ids = listOf("street_labels", "place_labels", "pois")
                    scenario.onActivity { it.inspectMap { map ->
                        val style = requireNotNull(map.style)
                        ids.forEach { id ->
                            val layer = style.getLayer("queuego-label-$id")
                                as? org.maplibre.android.style.layers.SymbolLayer
                                ?: throw AssertionError("Missing actual symbol layer $id")
                            layer.setProperties(
                                org.maplibre.android.style.layers.PropertyFactory.visibility(
                                    org.maplibre.android.style.layers.Property.NONE
                                )
                            )
                        }
                    } }
                    Thread.sleep(2_000)
                    val hiddenA = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                    Thread.sleep(800)
                    val hiddenB = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                    scenario.onActivity { it.inspectMap { map ->
                        val style = requireNotNull(map.style)
                        ids.forEach { id ->
                            val layer = style.getLayer("queuego-label-$id")
                                as? org.maplibre.android.style.layers.SymbolLayer
                                ?: throw AssertionError("Missing actual symbol layer $id")
                            layer.setProperties(
                                org.maplibre.android.style.layers.PropertyFactory.visibility(
                                    org.maplibre.android.style.layers.Property.VISIBLE
                                )
                            )
                        }
                    } }
                    Thread.sleep(3_000)
                    val visible = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                    hiddenFrameJitter = changedMapPixels(hiddenA, hiddenB)
                    visibleLabelPixelDelta = changedMapPixels(hiddenB, visible)
                    Log.i("QueueGoLabelQA", "bangkok_visible_label_pixel_delta=" +
                        visibleLabelPixelDelta + " hidden_baseline=" + hiddenFrameJitter +
                        " indexed_features=" + paintedLabels + " visual_certified=false")
                    // Persist the raw hidden baseline and a *diagnostic* red/white pixel
                    // difference image. The difference image is not a map screenshot or
                    // evidence of readable text; it localizes tiny framebuffer changes.
                    val evidenceDir = File(context.getExternalFilesDir(null), "offline-map-evidence").apply { mkdirs() }
                    val hiddenFile = File(evidenceDir, "bangkok-labels-hidden.png")
                    hiddenFile.outputStream().use {
                        assertTrue(hiddenB.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    val diffBitmap = Bitmap.createBitmap(visible.width, visible.height, Bitmap.Config.ARGB_8888)
                    var rawChanged = 0
                    var minX = visible.width
                    var minY = visible.height
                    var maxX = -1
                    var maxY = -1
                    for (y in 0 until visible.height) {
                        for (x in 0 until visible.width) {
                            val a = hiddenB.getPixel(x, y)
                            val b = visible.getPixel(x, y)
                            val delta = kotlin.math.abs(Color.red(a) - Color.red(b)) +
                                kotlin.math.abs(Color.green(a) - Color.green(b)) +
                                kotlin.math.abs(Color.blue(a) - Color.blue(b))
                            if (delta >= 80) {
                                rawChanged++
                                minX = kotlin.math.min(minX, x)
                                minY = kotlin.math.min(minY, y)
                                maxX = kotlin.math.max(maxX, x)
                                maxY = kotlin.math.max(maxY, y)
                                diffBitmap.setPixel(x, y, Color.RED)
                            } else {
                                diffBitmap.setPixel(x, y, Color.WHITE)
                            }
                        }
                    }
                    val diffFile = File(evidenceDir, "bangkok-labels-diff-diagnostic.png")
                    diffFile.outputStream().use {
                        assertTrue(diffBitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    diffBitmap.recycle()
                    labelDiagnosticFiles = listOf(hiddenFile, diffFile)
                    Log.i("QueueGoLabelQA", "bangkok_label_diff_raw_pixels=$rawChanged bbox=$minX,$minY,$maxX,$maxY diagnostic_only=true")
                    hiddenA.recycle()
                    hiddenB.recycle()
                    visible
                } else {
                    requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                }
                val directory = File(context.getExternalFilesDir(null), "offline-map-evidence").apply { mkdirs() }
                val screenshot = File(directory, "$name.png")
                screenshot.outputStream().use {
                    assertTrue(screenshotBitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                screenshotBitmap.recycle()
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
                // UTP removes app-private evidence on test uninstall. Retain the
                // raw hidden baseline and labeled diagnostic in MediaStore too.
                labelDiagnosticFiles.forEach { diagnostic ->
                    val diagnosticValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, diagnostic.name)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/QueueGoOfflineEvidence/")
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val diagnosticUri = requireNotNull(
                        resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, diagnosticValues)
                    )
                    resolver.openOutputStream(diagnosticUri).use { target ->
                        diagnostic.inputStream().use { source ->
                            assertEquals(diagnostic.length(), source.copyTo(requireNotNull(target)))
                        }
                    }
                    resolver.update(diagnosticUri, android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    }, null, null)
                    resolver.openFileDescriptor(diagnosticUri, "r").use {
                        assertEquals("Retained label diagnostic size differs", diagnostic.length(), requireNotNull(it).statSize)
                    }
                }
                if (offlineRequired) assertNull(context.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork)
                if (name == "bangkok") {
                    assertTrue("Offline Bangkok labels absent from symbol index", paintedLabels > 0)
                    assertTrue(
                        "Offline Bangkok glyphs are not visibly painted: " +
                            "pixel_delta=$visibleLabelPixelDelta baseline=$hiddenFrameJitter; " +
                            "see retained screenshot and QueueGoLabelQA logcat",
                        visibleLabelPixelDelta >= kotlin.math.max(40, hiddenFrameJitter * 3 + 20)
                    )
                }
            }
        }
    }
}
