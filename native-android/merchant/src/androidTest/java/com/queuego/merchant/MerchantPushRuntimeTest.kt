package com.queuego.merchant

import com.queuego.shared.SecureRoleSessionStore
import com.queuego.shared.NativeAuthApi
import com.queuego.shared.NativePushApi
import androidx.core.app.NotificationManagerCompat

import android.app.NotificationManager
import android.app.Notification
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real FCM only, using the existing authenticated owner review account. */
class MerchantPushRuntimeTest {
    @Test fun authenticatedBackgroundTestPushReachesAndroid() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SecureRoleSessionStore(context, "shop")
        val cached = requireNotNull(store.load()) { "Authenticated Merchant review session required" }
        val auth = NativeAuthApi().validate(cached, "shop") { store.save(it) }
        assertTrue("Only active Merchant review accounts may test push",
            auth.user.role == "shop" && auth.user.status == "active")
        assertTrue("Firebase is not configured in the actual candidate", merchantFirebaseConfigured(context))
        assertTrue("Android notification permission/channel disabled", NotificationManagerCompat.from(context).areNotificationsEnabled())
        val api = NativePushApi()
        withTimeout(90_000L) {
            assertTrue("Real FCM device registration failed", syncMerchantNativePush(context, auth))
        }
        // No fabricated RemoteMessage or direct notification invocation.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_HOME").close()
        SystemClock.sleep(1_000L)
        val started = System.currentTimeMillis()
        withTimeout(30_000L) { api.test(auth) }
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
