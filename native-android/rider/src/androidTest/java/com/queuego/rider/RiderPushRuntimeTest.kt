package com.queuego.rider

import android.app.NotificationManager
import android.app.Notification
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real backend FCM and Android notification only; no fabricated delivery. */
class RiderPushRuntimeTest {
    @Test fun authenticatedBackgroundTestPushReachesAndroid() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val metrics = JSONObject().put("source_commit", BuildConfig.GIT_SHA)
            .put("real_fcm_received", false).put("fast_window_ms", 90_000)
            .put("max_wait_ms", 180_000)
        try {
            val store = SessionStore(context)
            val auth = requireNotNull(store.load()) { "Authenticated review session required" }
            assertTrue("Only active Rider review accounts may test push",
                auth.user.role == "rider" && auth.user.status == "active")
            assertTrue("Firebase is not configured in the actual candidate", riderFirebaseConfigured(context))
            assertTrue("Android notification permission/channel disabled", riderNotificationsReady(context))
            val api = QueueGoApi()
            withTimeout(90_000L) {
                assertTrue("Real FCM device registration failed", syncRiderNativePush(context, auth, api, store))
            }
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("input keyevent KEYCODE_HOME").close()
            SystemClock.sleep(1_000L)
            val started = System.currentTimeMillis()
            val monotonicStarted = SystemClock.elapsedRealtime()
            val receipt = withTimeout(30_000L) { api.testNativePush(auth) } as? JSONObject
            val created = receipt != null && receipt.optBoolean("ok") &&
                !receipt.optBoolean("rateLimited") && !receipt.optString("notificationId").isBlank()
            metrics.put("backend_created", created)
            assertTrue("Backend did not create a new FCM test notification (invalid/rate-limited receipt)", created)
            val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
            // Verified live first retry is60s + a minute cron tick. A90s-only
            // wait expired10s before the observed retry succeeded on35c61d975.
            // Allow one scheduled retry; keep90s latency reported separately.
            val deadline = monotonicStarted + 180_000L
            var received = false
            while (SystemClock.elapsedRealtime() < deadline) {
                received = manager.activeNotifications.any {
                    it.postTime >= started &&
                        it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ==
                            "ทดสอบการแจ้งเตือน QueueGo" &&
                        it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                            ?.contains("แอปอยู่เบื้องหลัง") == true
                }
                if (received) break
                SystemClock.sleep(500L)
            }
            val elapsed = SystemClock.elapsedRealtime() - monotonicStarted
            metrics.put("real_fcm_received", received).put("elapsed_ms", elapsed)
                .put("within_fast_window", received && elapsed <= 90_000L)
            assertTrue("Real backend FCM did not reach Android within one bounded retry window", received)
            android.util.Log.i("QueueGoPushQA", "REAL_BACKGROUND_FCM_RECEIVED")
        } finally {
            val directory = File(context.filesDir, "push-evidence").apply { mkdirs() }
            File(directory, "background-fcm.json").writeText(metrics.toString())
        }
        Unit
    }
}
