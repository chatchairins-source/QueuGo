package com.queuego.rider

import android.Manifest
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderNavigationLocationServiceRuntimeTest {
    @Test fun locationServiceSurvivesHomeAndStopsExplicitly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation

        automation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        automation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { RiderNavigationLocationService.start(it) }
                assertTrue(
                    "Location foreground service did not start from visible Rider Activity",
                    waitForServiceState(context, expectedRunning = true, timeoutSeconds = 10)
                )

                automation.executeShellCommand("input keyevent KEYCODE_HOME").close()
                Thread.sleep(1_000L)

                val backgroundDump = dumpServices(context)
                assertTrue(
                    "Location service did not survive app background",
                    backgroundDump.contains("RiderNavigationLocationService")
                )
                assertTrue(
                    "Android did not keep navigation location service in foreground state",
                    backgroundDump.contains("isForeground=true")
                )

                RiderNavigationLocationService.stop(context)
                assertTrue(
                    "Location foreground service did not stop after navigation ended",
                    waitForServiceState(context, expectedRunning = false, timeoutSeconds = 5)
                )
            } finally {
                RiderNavigationLocationService.stop(context)
            }
        }
    }

    private fun waitForServiceState(
        context: Context,
        expectedRunning: Boolean,
        timeoutSeconds: Long
    ): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        do {
            val running = dumpServices(context).contains("RiderNavigationLocationService")
            if (running == expectedRunning) return true
            Thread.sleep(100L)
        } while (System.nanoTime() < deadline)
        return false
    }

    private fun dumpServices(context: Context): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys activity services " + context.packageName)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
    }
}
