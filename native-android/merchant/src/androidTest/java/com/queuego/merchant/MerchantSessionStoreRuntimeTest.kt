package com.queuego.merchant

import androidx.test.platform.app.InstrumentationRegistry
import com.queuego.shared.NativeAuth
import com.queuego.shared.NativeSession
import com.queuego.shared.NativeUser
import com.queuego.shared.SecureRoleSessionStore
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

/** Real Android Keystore/preferences, isolated namespace; no review-account logout,
 * fabricated FCM, remote tokens, backend calls or order writes.
 */
class MerchantSessionStoreRuntimeTest {
    @Test fun lateRefreshCannotResurrectLogoutOrOverwriteNewLogin() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "qa_session_race_" + UUID.randomUUID().toString()
        val ui = SecureRoleSessionStore(context, namespace)
        val service = SecureRoleSessionStore(context, namespace)
        fun auth(id: String, token: String) = NativeAuth(
            NativeSession("auth-$id", token, "refresh-$token", 1_000L, "session-$id"),
            NativeUser("user-$id", "auth-$id", "QA", "shop", "active")
        )
        val old = auth("a", "old")
        val renewed = auth("a", "renewed")
        val next = auth("b", "new-login")
        try {
            // Stale principal must exit the real sync path before Firebase/network.
            assertFalse(kotlinx.coroutines.runBlocking { syncMerchantNativePush(context, old) })
            // Negative control: the former unconditional callback restores logout.
            ui.save(old); ui.clear(); service.save(renewed)
            assertEquals(renewed, ui.load())
            ui.clear()
            assertFalse(service.saveIfCurrent(old, renewed))
            assertNull(ui.load())
            ui.save(old)
            assertFalse(service.saveIfCurrent(old, next))
            assertEquals(old, ui.load())
            assertTrue(service.saveIfCurrent(old, renewed))
            assertTrue(ui.isCurrent(renewed))
            assertFalse(ui.isCurrent(old))
            assertFalse(service.saveIfCurrent(old, old))
            // Independent instances race against the same actual encrypted record.
            repeat(20) { iteration ->
                ui.save(old)
                val go = CountDownLatch(1)
                val error = AtomicReference<Throwable?>()
                fun worker(block: () -> Unit) = Thread {
                    try { go.await(); block() } catch (failure: Throwable) { error.set(failure) }
                }.apply { start() }
                val refresh = worker { service.saveIfCurrent(old, renewed) }
                val login = worker { if (iteration % 2 == 0) ui.clear() else ui.save(next) }
                go.countDown()
                refresh.join(10_000); login.join(10_000)
                assertFalse("Refresh/storage deadlock", refresh.isAlive || login.isAlive)
                error.get()?.let { throw AssertionError("Real secure storage race failed", it) }
                if (iteration % 2 == 0) assertNull(ui.load()) else assertEquals(next, ui.load())
            }
            android.util.Log.i("QueueGoPushQA", "MERCHANT_SECURE_SESSION_RACE_PASS")
        } finally {
            ui.clear()
            context.deleteSharedPreferences("queuego_native_" + namespace)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .deleteEntry("queuego_native_session_" + namespace)
        }
    }
}
