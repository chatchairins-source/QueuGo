package com.queuego.rider

import android.Manifest
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.queuego.shared.NativeVoiceForegroundService
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Service lifecycle only: no call, media, auth fixture or Production writes. */
class NativeVoiceForegroundRuntimeTest {
    @Test fun microphoneServiceSurvivesHomeAndStopsOnlyForItsOwner() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        val owner = UUID.randomUUID().toString()
        val stopped = AtomicBoolean(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                var ready: kotlinx.coroutines.Deferred<Unit>? = null
                scenario.onActivity {
                    ready = NativeVoiceForegroundService.prepare(context, owner) { stopped.set(true) }
                }
                runBlocking { withTimeout(10_000L) { ready!!.await() } }
                assertTrue(NativeVoiceForegroundService.isReady(owner))
                automation.executeShellCommand("input keyevent KEYCODE_HOME").close()
                Thread.sleep(1000)
                val dump = automation.executeShellCommand("dumpsys activity services ${context.packageName}").use {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
                }
                assertTrue(dump.contains("NativeVoiceForegroundService"))
                assertTrue("Android must keep the service foreground", dump.contains("isForeground=true"))
                NativeVoiceForegroundService.stop(context, "stale-owner")
                assertTrue(NativeVoiceForegroundService.isReady(owner))
                assertFalse(stopped.get())
                NativeVoiceForegroundService.stop(context, owner)
                assertFalse(NativeVoiceForegroundService.isReady(owner))
                // Wait for the actual Android service destruction, not merely a cleared lease.
                val deadline = System.nanoTime() + 5_000_000_000L
                var remaining: String
                do {
                    remaining = automation.executeShellCommand("dumpsys activity services ${context.packageName}").use {
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
                    }
                    if (!remaining.contains("NativeVoiceForegroundService")) break
                    Thread.sleep(100)
                } while (System.nanoTime() < deadline)
                assertFalse("Service must be destroyed after hangup", remaining.contains("NativeVoiceForegroundService"))
            } finally {
                NativeVoiceForegroundService.stop(context, owner)
            }
        }
    }
}
