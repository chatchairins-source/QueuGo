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
        ActivityScenario.launch(RiderMapRuntimeActivity::class.java).use { scenario ->
            fun awaitRealMap() {
                var ready: CountDownLatch? = null
                scenario.onActivity { ready = it.mapReady }
                assertTrue("Longdo did not create the actual native map", ready!!.await(30, TimeUnit.SECONDS))
            }
            awaitRealMap()
            scenario.onActivity { it.recenterMap() }
            val initialCamera = captureRealFrame("initial", scenario)
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
            scenario.recreate()
            awaitRealMap()
            val recreatedCamera = captureRealFrame("recreated", scenario)
            assertSameCamera(initialCamera, recreatedCamera)
        }
        Log.i("QueueGoMapRuntime", "completed_pid=${Process.myPid()}")
    }

    private fun assertSameCamera(expected: DoubleArray, actual: DoubleArray) {
        assertEquals("Actual SDK longitude jumped", expected[0], actual[0], 0.0001)
        assertEquals("Actual SDK latitude jumped", expected[1], actual[1], 0.0001)
        assertEquals("Actual SDK zoom changed", expected[2], actual[2], 0.0)
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
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        var camera: DoubleArray? = null
        scenario.onActivity { camera = it.actualMapCamera() }
        val actual = requireNotNull(camera)
        File(directory, "$name-camera.json").writeText(JSONObject()
            .put("longitude", actual[0]).put("latitude", actual[1]).put("zoom", actual[2])
            .put("first_frame_gray_fraction", firstFraction).put("final_gray_fraction", finalFraction)
            .put("capture_after_ms", System.currentTimeMillis() - started).toString())
        assertTrue("Real map tiles still incomplete after 60 seconds", finalFraction <= 0.01)
        Log.i("QueueGoMapRuntime", "captured=$name; actual_sdk_camera_recorded; capture_after_ms=${System.currentTimeMillis() - started}")
        return actual
    }
}
