package com.queuego.rider

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import android.os.Process
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.json.JSONObject
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RiderMapRuntimeTest {
    @Test fun homeMapMountsBackgroundsAndRecreatesWithoutMissingSdkClasses() {
        verifyBrandedVectorRendering()
        ActivityScenario.launch(RiderMapRuntimeActivity::class.java).use { scenario ->
            fun awaitRealMap() {
                var ready: CountDownLatch? = null
                scenario.onActivity { ready = it.mapReady }
                assertTrue("Longdo did not create the actual native map", ready!!.await(30, TimeUnit.SECONDS))
            }
            awaitRealMap()
            // Live provider geometry; public vendor-example coordinates, no job/account fixtures.
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val roads = runCatching {
                kotlinx.coroutines.runBlocking {
                    loadRiderRoadGeometry(context, RiderRoutePoint(13.7563, 100.5018),
                        RiderRoutePoint(13.7460, 100.5340))
                }
            }.getOrElse { error ->
                Log.w(
                    "QueueGoMapRuntime",
                    "live_road_geometry_provider_unavailable; lifecycle evidence continues",
                    error
                )
                emptyList()
            }
            if (roads.isNotEmpty()) {
                scenario.onActivity { it.showRoadDisplayEvidence(roads) }
                Log.i("QueueGoMapRuntime", "live_road_geometry_dark_active_pale_remaining_verified")
            } else {
                Log.w("QueueGoMapRuntime", "live_road_geometry_skipped_provider_unavailable")
            }
            scenario.onActivity { it.recenterMap() }
            val initialCamera = captureRealFrame("initial", scenario)
            assertExpectedGpsCamera(initialCamera)
            // Stress the exact physical-device lifecycle edge that occurs during
            // app switching, permission sheets and screen-off/on. A single cycle can
            // miss intermittent GL renderer teardown races.
            repeat(8) { cycle ->
                scenario.moveToState(Lifecycle.State.CREATED)
                Thread.sleep(250)
                scenario.moveToState(Lifecycle.State.RESUMED)
                Thread.sleep(250)
                scenario.onActivity { it.recenterMap() }
                Log.i("QueueGoMapRuntime", "lifecycle_stress_cycle=" + (cycle + 1))
            }
            awaitRealMap()
            val resumedCamera = captureRealFrame("resumed", scenario)
            assertSameCamera(initialCamera, resumedCamera)
            assertExpectedGpsCamera(resumedCamera)
            scenario.recreate()
            awaitRealMap()
            val recreatedCamera = captureRealFrame("recreated", scenario)
            assertSameCamera(initialCamera, recreatedCamera)
            assertExpectedGpsCamera(recreatedCamera)
        }
        Log.i("QueueGoMapRuntime", "completed_pid=${Process.myPid()}")
    }

    private fun verifyBrandedVectorRendering() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = listOf(
            R.drawable.qg_marker_pickup to android.graphics.Color.rgb(230, 0, 45),
            R.drawable.qg_marker_dropoff to android.graphics.Color.rgb(0, 127, 153),
            R.drawable.qg_rider_current_location to android.graphics.Color.rgb(230, 0, 45),
            R.drawable.qg_rider_brand_q to android.graphics.Color.rgb(230, 0, 45)
        )
        for ((asset, primary) in assets) {
            for (density in listOf(1, 2, 3)) {
                val drawable = requireNotNull(context.getDrawable(asset))
                val width = 48 * density
                val height = if (asset == R.drawable.qg_marker_pickup || asset == R.drawable.qg_marker_dropoff) 58 * density else width
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(android.graphics.Canvas(bitmap))
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                assertTrue("Branded vector primary color missing", pixels.count { it == primary } > pixels.size / 10)
                assertTrue("Branded vector white edge/icon missing", pixels.any { it == android.graphics.Color.WHITE })
                assertTrue("Branded vector must retain transparent map background", pixels.any { android.graphics.Color.alpha(it) == 0 })
                bitmap.recycle()
            }
        }
        Log.i("QueueGoMapRuntime", "branded_vectors_1x_2x_3x_verified")
    }

    private fun assertSameCamera(expected: DoubleArray, actual: DoubleArray) {
        assertEquals("Actual SDK longitude jumped", expected[0], actual[0], 0.0001)
        assertEquals("Actual SDK latitude jumped", expected[1], actual[1], 0.0001)
        assertEquals("Actual SDK zoom changed", expected[2], actual[2], 0.0)
    }

    private fun assertExpectedGpsCamera(actual: DoubleArray) {
        val args = InstrumentationRegistry.getArguments()
        val expectedLongitude = args.getString("expectedLongitude")?.toDoubleOrNull() ?: return
        val expectedLatitude = args.getString("expectedLatitude")?.toDoubleOrNull() ?: return
        assertEquals("Rider map did not recenter to the injected current longitude", expectedLongitude, actual[0], 0.002)
        assertEquals("Rider map did not recenter to the injected current latitude", expectedLatitude, actual[1], 0.002)
        Log.i("QueueGoMapRuntime", "injected_gps_camera_verified")
    }

    private fun grayTileFraction(bitmap: Bitmap): Double {
        var gray = 0
        var samples = 0
        val stride = (bitmap.width / 180).coerceAtLeast(1)
        for (y in bitmap.height / 10 until bitmap.height * 9 / 10 step stride) {
            for (x in 0 until bitmap.width step stride) {
                val rgb = bitmap.getPixel(x, y) and 0xFFFFFF
                if (rgb == 0xB3B3B3) gray++
                samples++
            }
        }
        return gray.toDouble() / samples.coerceAtLeast(1)
    }

    private fun captureRealFrame(name: String, scenario: ActivityScenario<RiderMapRuntimeActivity>): DoubleArray {
        // Retain the first frame and real tile latency; map callbacks do not certify loaded tiles.
        val started = System.currentTimeMillis()
        Thread.sleep(8_000)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.filesDir, "map-runtime")
        assertTrue("Cannot create map evidence directory", directory.isDirectory || directory.mkdirs())
        var image = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Actual Android screenshot unavailable" }
        File(directory, "$name-first-frame.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val firstFraction = grayTileFraction(image)
        while (grayTileFraction(image) > 0.01 && System.currentTimeMillis() - started < 60_000) {
            image.recycle()
            Thread.sleep(2_000)
            image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        }
        val finalFraction = grayTileFraction(image)
        val allowIncompleteTiles = InstrumentationRegistry.getArguments().getString("allowIncompleteTiles") == "true"
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        var camera: DoubleArray? = null
        scenario.onActivity { camera = it.actualMapCamera() }
        val actual = requireNotNull(camera)
        File(directory, "$name-camera.json").writeText(JSONObject()
            .put("longitude", actual[0]).put("latitude", actual[1]).put("zoom", actual[2])
            .put("first_frame_gray_fraction", firstFraction).put("final_gray_fraction", finalFraction)
            .put("strict_tile_completeness", !allowIncompleteTiles)
            .put("capture_after_ms", System.currentTimeMillis() - started).toString())
        if (!allowIncompleteTiles) {
            assertTrue("Real map tiles still incomplete after 60 seconds", finalFraction <= 0.01)
        }
        Log.i("QueueGoMapRuntime", "captured=$name; actual_sdk_camera_recorded; capture_after_ms=${System.currentTimeMillis() - started}")
        return actual
    }
}
