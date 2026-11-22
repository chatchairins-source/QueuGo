package com.queuego.merchant

import android.app.PendingIntent
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Actual Android token identity, no fabricated FCM or backend/order writes. */
class MerchantPendingIntentRuntimeTest {
    @Test fun collidingMessagesAndAccountSwitchKeepIndependentTapTargets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokens = mutableListOf<PendingIntent>()
        try {
            fun legacy(message: String): PendingIntent = PendingIntent.getActivity(
                context, message.hashCode(), Intent(context, MainActivity::class.java)
                    .putExtra("queuego_push_reference_id", message),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ).also { tokens.add(it) }
            // Negative control reproduces the existing Android-framework defect.
            assertEquals("Aa".hashCode(), "BB".hashCode())
            assertEquals(legacy("Aa"), legacy("BB"))
            fun actual(account: String, message: String): PendingIntent =
                merchantNotificationPendingIntent(context, merchantPushIdentity(account, message), message, "order")
                    .also { tokens.add(it) }
            val first = actual("shop-a", "Aa")
            val second = actual("shop-a", "BB")
            assertNotEquals(first, second)
            assertNotEquals(first, actual("shop-b", "Aa"))
            assertEquals(first, actual("shop-a", "Aa"))
            android.util.Log.i("QueueGoPushQA", "MERCHANT_PENDING_INTENT_IDENTITY_PASS")
        } finally {
            tokens.distinct().forEach { it.cancel() }
        }
    }
}
