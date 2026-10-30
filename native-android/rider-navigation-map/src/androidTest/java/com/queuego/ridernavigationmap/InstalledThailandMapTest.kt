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
                val directory = File(context.getExternalFilesDir(null), "offline-map-evidence").apply { mkdirs() }
                val screenshot = File(directory, "$name.png")
                screenshot.outputStream().use {
                    assertTrue(instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                // Gradle removes the test APK and its private external directory
                // after execution. Copy generated evidence before that cleanup.
                val saved = shell("mkdir -p /sdcard/Download/QueueGoOfflineEvidence && cp '${screenshot.absolutePath}' /sdcard/Download/QueueGoOfflineEvidence/$name.png && echo QUEUEGO_SCREENSHOT_SAVED")
                assertTrue("Screenshot was not retained outside test-app data", saved.contains("QUEUEGO_SCREENSHOT_SAVED"))
                if (offlineRequired) assertNull(context.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork)
            }
            if (offlineRequired) shell("dumpsys connectivity > /sdcard/Download/QueueGoOfflineEvidence/connectivity.txt")
        }
    }
}
