package com.queuego.ridernavigationmap

import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
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
        val intent = Intent(context, NavigationMapRuntimeActivity::class.java)
            .putExtra("installedThailandMap", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<NavigationMapRuntimeActivity>(intent).use { scenario ->
            lateinit var activity: NavigationMapRuntimeActivity
            scenario.onActivity { activity = it }
            assertTrue("Installed archive style failed", activity.mapReady.await(45, TimeUnit.SECONDS))
            // Register before moving between cities so glyph events are attributable
            // to this offline country run, not to earlier style initialization.
            scenario.onActivity { it.installOfflineGlyphDiagnostics() }
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
                    // Public map data only: no sessions, user UI, routes or contact data.
                    val manifest = JSONObject(context.assets.open("fonts/provenance.json").bufferedReader().use { it.readText() })
                    for (range in listOf("0-255", "3584-3839")) {
                        val relative = "Sans Regular/$range.pbf"
                        val bytes = context.assets.open("fonts/$relative").use { it.readBytes() }
                        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                            .joinToString("") { "%02x".format(it.toInt() and 255) }
                        Log.i("QueueGoLabelQA", JSONObject().put("font", relative)
                            .put("bytes", bytes.size).put("sha256", digest).toString())
                        assertTrue("Packaged label glyph block is empty: $range", bytes.isNotEmpty())
                        assertEquals("Packaged glyph differs from provenance: $range",
                            manifest.getJSONObject("files").getString("Noto Sans Regular/$range.pbf"), digest)
                    }
                    scenario.onActivity { it.inspectMap { map ->
                        val styleJson = JSONObject(requireNotNull(map.style).json)
                        Log.i("QueueGoLabelQA", JSONObject().put("diagnostic_only", true)
                            .put("visual_label_acceptance", false).put("glyphs", styleJson.optString("glyphs"))
                            .put("zoom", map.cameraPosition.zoom).put("pitch", map.cameraPosition.tilt)
                            .put("bearing", map.cameraPosition.bearing).put("indexed_labels", paintedLabels).toString())
                        val layers = styleJson.optJSONArray("layers") ?: JSONArray()
                        for (index in 0 until layers.length()) {
                            val layer = layers.getJSONObject(index)
                            if (layer.optString("id").startsWith("queuego-label-")) {
                                Log.i("QueueGoLabelQA", JSONObject().put("serialized_base_layer", layer.optString("id"))
                                    .put("source_layer", layer.optString("source-layer"))
                                    .put("layout", layer.optJSONObject("layout") ?: JSONObject())
                                    .put("paint", layer.optJSONObject("paint") ?: JSONObject()).toString())
                            }
                        }
                        // Style.json may contain only the original base JSON: layers
                        // appended by Style.Builder.withLayer must be inspected through
                        // the live Style API, not inferred from JSON serialization.
                        for (id in listOf("queuego-label-street_labels",
                                "queuego-label-place_labels", "queuego-label-pois")) {
                            val symbol = map.style?.getLayer(id)
                                as? org.maplibre.android.style.layers.SymbolLayer
                            Log.i("QueueGoLabelQA", JSONObject()
                                .put("runtime_layer", id)
                                .put("present", symbol != null)
                                .put("visibility", symbol?.getVisibility()?.toString() ?: "missing")
                                .put("text_field", symbol?.getTextField()?.toString() ?: "missing")
                                .put("text_font", symbol?.getTextFont()?.toString() ?: "missing")
                                .put("text_size", symbol?.getTextSize()?.toString() ?: "missing")
                                .put("text_color", symbol?.getTextColor()?.toString() ?: "missing")
                                .put("text_opacity", symbol?.getTextOpacity()?.toString() ?: "missing")
                                .put("text_halo_color", symbol?.getTextHaloColor()?.toString() ?: "missing")
                                .put("text_halo_width", symbol?.getTextHaloWidth()?.toString() ?: "missing")
                                .put("symbol_placement", symbol?.getSymbolPlacement()?.toString() ?: "missing")
                                .put("source_layer", symbol?.sourceLayer ?: "missing")
                                .put("min_zoom", symbol?.minZoom ?: -1f)
                                .toString())
                            assertNotNull("Installed map label layer missing: $id", symbol)
                        }
                        val indexed = map.queryRenderedFeatures(
                            android.graphics.RectF(0f, 0f, 1080f, 2280f),
                            "queuego-label-street_labels", "queuego-label-place_labels", "queuego-label-pois")
                        indexed.take(6).forEach { feature ->
                            val properties = JSONObject(feature.toJson()).optJSONObject("properties") ?: JSONObject()
                            val sample = JSONObject()
                            for (key in listOf("name", "name:th", "name:en", "ref")) {
                                if (properties.has(key)) sample.put(key, properties.optString(key).take(120))
                            }
                            Log.i("QueueGoLabelQA", JSONObject().put("indexed_public_name", sample).toString())
                        }
                    } }
                }
                // A symbol may enter the rendered-feature index before its fade-in
                // reaches visible opacity. Retain the settled frame as evidence.
                if (name == "bangkok" && paintedLabels > 0) Thread.sleep(5_000)
                if (name == "bangkok") {
                    scenario.onActivity {
                        Log.i("QueueGoGlyphQA", "bangkok " + it.offlineGlyphDiagnosticSummary())
                    }
                }
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
                if (offlineRequired) assertNull(context.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork)
                if (name == "bangkok") assertTrue("Offline Bangkok labels did not paint; see retained screenshot", paintedLabels > 0)
            }
        }
    }
}
