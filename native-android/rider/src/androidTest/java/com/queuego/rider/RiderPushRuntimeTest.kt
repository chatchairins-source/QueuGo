package com.queuego.rider

import android.app.NotificationManager
import android.app.Notification
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real FCM only, using the existing authenticated owner review account. */
class RiderPushRuntimeTest {
    @Test fun authenticatedBackgroundTestPushReachesAndroid() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
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
        // No fabricated RemoteMessage or direct notification invocation.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_HOME").close()
        SystemClock.sleep(1_000L)
        val started = System.currentTimeMillis()
        withTimeout(30_000L) { api.testNativePush(auth) }
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        val deadline = SystemClock.elapsedRealtime() + 90_000L
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
        assertTrue("Real backend FCM test notification did not reach Android in background", received)
        android.util.Log.i("QueueGoPushQA", "REAL_BACKGROUND_FCM_RECEIVED")
        Unit
    }
}
