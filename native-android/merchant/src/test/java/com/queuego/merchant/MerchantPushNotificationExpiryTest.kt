package com.queuego.merchant

import com.queuego.shared.NativeAuth
import com.queuego.shared.NativeSession
import com.queuego.shared.NativeUser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantPushNotificationExpiryTest {
    private fun auth(role: String = "shop", status: String = "active", expiry: Long = 2_000L) =
        NativeAuth(
            NativeSession("auth-shop-1", "access", "refresh", expiry, "session-1"),
            NativeUser("shop-1", "auth-shop-1", "Merchant", role, status)
        )

    @Test fun activeSessionBeforeExpiryMayDisplayNotification() {
        assertTrue(merchantPushNotificationSessionActive(auth(), 1_999L))
    }

    @Test fun expiryBoundaryAndPastExpiryRejectNotification() {
        assertFalse(merchantPushNotificationSessionActive(auth(), 2_000L))
        assertFalse(merchantPushNotificationSessionActive(auth(), 2_001L))
    }

    @Test fun inactiveOrWrongRoleCannotDisplayNotification() {
        assertFalse(merchantPushNotificationSessionActive(auth(status = "inactive"), 1_000L))
        assertFalse(merchantPushNotificationSessionActive(auth(role = "customer"), 1_000L))
    }

    @Test fun extremeExpiryValuesDoNotOverflow() {
        assertFalse(merchantPushNotificationSessionActive(auth(expiry = Long.MIN_VALUE), Long.MAX_VALUE))
        assertTrue(merchantPushNotificationSessionActive(auth(expiry = Long.MAX_VALUE), Long.MIN_VALUE))
    }
}
