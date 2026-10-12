package com.queuego.rider

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import android.os.Process
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertTrue
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
            captureRealFrame("initial")
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitRealMap()
            captureRealFrame("resumed")
            scenario.recreate()
            awaitRealMap()
            captureRealFrame("recreated")
        }
        Log.i("QueueGoMapRuntime", "completed_pid=${Process.myPid()}")
    }

    private fun captureRealFrame(name: String) {
        // Allow real network tiles to render; readiness alone does not certify visible tiles.
        Thread.sleep(8_000)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = instrumentation.uiAutomation.takeScreenshot()
        assertTrue("Actual Android screenshot unavailable", image != null)
        val directory = File(instrumentation.targetContext.filesDir, "map-runtime")
        assertTrue("Cannot create map evidence directory", directory.isDirectory || directory.mkdirs())
        File(directory, "$name.png").outputStream().use { image!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image!!.recycle()
        Log.i("QueueGoMapRuntime", "captured=$name")
    }
}
