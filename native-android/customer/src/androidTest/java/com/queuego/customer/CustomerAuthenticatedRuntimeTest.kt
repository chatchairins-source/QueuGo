package com.queuego.customer

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.queuego.shared.NativeAuth
import com.queuego.shared.NativeAuthApi
import com.queuego.shared.NativePushApi
import com.queuego.shared.SecureRoleSessionStore
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Test

/** Secret-backed real login UI, server session validation and actual FCM only.
 * No fabricated auth/RemoteMessage, signup/order/payment writes or UI hierarchy export.
 */
class CustomerAuthenticatedRuntimeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val result = mutableListOf(root)
        for (index in 0 until root.childCount) result.addAll(nodes(root.getChild(index)))
        return result
    }

    private fun labelled(node: AccessibilityNodeInfo, label: String): Boolean =
        node.isVisibleToUser && listOfNotNull(node.text, node.contentDescription)
            .flatMap { it.toString().lines() }.any { it.trim() == label }

    private fun waitFor(seconds: Long = 75, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + seconds * 1_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(500)
        }
        error("Required native runtime condition unavailable")
    }

    private fun clickLabel(label: String, scroll: Boolean = false) {
        var clicked = false
        repeat(if (scroll) 8 else 1) {
            if (clicked) return@repeat
            val all = nodes(instrumentation.uiAutomation.rootInActiveWindow)
            val targets = all.filter { labelled(it, label) }.mapNotNull { node ->
                var target: AccessibilityNodeInfo? = node
                repeat(5) {
                    if (target?.isClickable != true) target = target?.parent
                }
                target?.takeIf { it.isClickable && it.isEnabled && it.isVisibleToUser }
            }
            val target = targets.maxByOrNull { Rect().also(it::getBoundsInScreen).centerY() }
            if (target != null) clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (!clicked && scroll) {
                all.firstOrNull { it.isScrollable && it.isVisibleToUser }
                    ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(700)
            }
        }
        check(clicked) { "Required native control unavailable" }
    }

    @Test fun realLoginRestoreOptInAndNotificationTap() = runBlocking {
        val context = instrumentation.targetContext
        val credentialFile = File(context.filesDir, "qa-customer-credentials.json")
        val store = SecureRoleSessionStore(context, "customer")
        val api = NativeAuthApi()
        val push = NativePushApi()
        val evidence = JSONObject().put("source_commit", BuildConfig.GIT_SHA)
            .put("physical_device_tested", false).put("login_ui", false)
            .put("live_customer_session", false).put("profile_restore", false)
            .put("push_opt_in_ui", false).put("backend_created", false)
            .put("real_fcm_received", false).put("notification_tap_return", false)
        var stage = "private_credentials"
        var auth: NativeAuth? = null
        var activity: ActivityScenario<MainActivity>? = null
        try {
            check(credentialFile.isFile)
            val credentials = JSONObject(credentialFile.readText())
            check(credentialFile.delete())
            val phone = credentials.getString("phone")
            val password = credentials.getString("password")
            check(phone.isNotBlank() && password.isNotBlank())
            check(store.load() == null) { "A fresh review installation is required" }
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            activity = scenario
            stage = "login_ui"
            waitFor { nodes(instrumentation.uiAutomation.rootInActiveWindow).any { labelled(it, "เข้าสู่ระบบ") } }
            clickLabel("เข้าสู่ระบบ")
            waitFor { nodes(instrumentation.uiAutomation.rootInActiveWindow).count { it.isEditable && it.isVisibleToUser } == 2 }
            val fields = nodes(instrumentation.uiAutomation.rootInActiveWindow)
                .filter { it.isEditable && it.isVisibleToUser }
                .sortedBy { Rect().also(it::getBoundsInScreen).centerY() }
            for ((field, value) in fields.zip(listOf(phone, password))) {
                check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                }))
            }
            scenario.onActivity { current ->
                (current.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(current.window.decorView.windowToken, 0)
            }
            clickLabel("เข้าสู่ระบบ")
            waitFor { store.load()?.let { it.user.role == "customer" && it.user.status == "active" } == true }
            val saved = requireNotNull(store.load())
            auth = saved
            stage = "live_session"
            val validated = withTimeout(90_000L) { api.validate(saved, "customer") { renewed ->
                check(store.saveIfCurrent(saved, renewed))
            } }
            auth = validated
            check(validated.user.role == "customer" && validated.user.status == "active")
            evidence.put("login_ui", true).put("live_customer_session", true)
            stage = "profile_restore"
            waitFor { nodes(instrumentation.uiAutomation.rootInActiveWindow).any { labelled(it, "โปรไฟล์") } }
            clickLabel("โปรไฟล์")
            waitFor { nodes(instrumentation.uiAutomation.rootInActiveWindow).any { labelled(it, "บัญชีและการใช้งาน") } }
            scenario.recreate()
            waitFor { nodes(instrumentation.uiAutomation.rootInActiveWindow).any { labelled(it, "โปรไฟล์") } }
            clickLabel("โปรไฟล์")
            waitFor {
                val all = nodes(instrumentation.uiAutomation.rootInActiveWindow)
                all.any { labelled(it, "บัญชีและการใช้งาน") } && all.any { labelled(it, validated.user.name) }
            }
            check(store.load()?.user?.id == validated.user.id)
            evidence.put("profile_restore", true)
            stage = "push_opt_in_ui"
            check(customerFirebaseConfigured(context))
            check(!customerPushEnabled(context, validated.user.id))
            clickLabel("การแจ้งเตือนเบื้องหลัง", scroll = true)
            waitFor(90) { customerPushEnabled(context, validated.user.id) }
            evidence.put("push_opt_in_ui", true)
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            SystemClock.sleep(1_000)
            stage = "fcm_receipt"
            val started = System.currentTimeMillis()
            val monotonic = SystemClock.elapsedRealtime()
            val receipt = withTimeout(30_000L) { push.test(validated) } as? JSONObject
            check(receipt != null && receipt.optBoolean("ok") && !receipt.optBoolean("rateLimited") &&
                receipt.optString("notificationId").isNotBlank())
            evidence.put("backend_created", true)
            stage = "fcm_delivery"
            val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
            var pending: PendingIntent? = null
            val remainingSeconds = (180_000L - (SystemClock.elapsedRealtime() - monotonic)) / 1_000L
            check(remainingSeconds > 0)
            waitFor(remainingSeconds) {
                pending = manager.activeNotifications.firstOrNull {
                    it.postTime >= started && it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ==
                        "ทดสอบการแจ้งเตือน QueueGo" &&
                        it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.contains("แอปอยู่เบื้องหลัง") == true
                }?.notification?.contentIntent
                pending != null
            }
            val elapsed = SystemClock.elapsedRealtime() - monotonic
            evidence.put("real_fcm_received", true).put("elapsed_ms", elapsed)
                .put("within_fast_window_90s", elapsed <= 90_000L)
            stage = "notification_tap"
            requireNotNull(pending).send()
            waitFor { scenario.state == Lifecycle.State.RESUMED &&
                instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == context.packageName }
            check(store.load()?.user?.id == validated.user.id)
            evidence.put("notification_tap_return", true)
            stage = "PASS"
        } catch (failure: Throwable) {
            // Never dump input values, server payloads, credentials or UI hierarchy.
            evidence.put("failure_type", failure.javaClass.simpleName)
            throw AssertionError("Customer real runtime failed; inspect Boolean stage evidence")
        } finally {
            evidence.put("stage", stage)
            credentialFile.delete()
            activity?.close()
            auth?.let { current ->
                setCustomerPushEnabled(context, current.user.id, false)
                runCatching { disableCustomerNativePush(context, current) }
                runCatching { api.revoke(current.session) }
            }
            store.clear()
            File(context.filesDir, "push-evidence").apply { mkdirs() }
                .resolve("customer-authenticated.json").writeText(evidence.toString())
        }
    }
}
