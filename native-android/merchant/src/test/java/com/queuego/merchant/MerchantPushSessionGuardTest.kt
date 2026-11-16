package com.queuego.merchant

import com.queuego.shared.NativeAuth
import com.queuego.shared.NativeSession
import com.queuego.shared.NativeUser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantPushSessionGuardTest {
    private fun auth(
        userId: String = "shop-1",
        sessionId: String = "session-1",
        role: String = "shop",
        status: String = "active",
        expiry: Long = 2_000L
    ) = NativeAuth(
        NativeSession("auth-$userId", "access", "refresh", expiry, sessionId),
        NativeUser(userId, "auth-$userId", "Merchant", role, status)
    )

    @Test fun sameActiveSessionAllowsTokenRefresh() {
        assertTrue(merchantPushSessionCurrent(auth(), auth(), 1_999L))
    }

    @Test fun logoutAndAccountSwitchCannotRegisterToken() {
        assertFalse(merchantPushSessionCurrent(auth(), null, 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(), auth(userId = "shop-2"), 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(), auth(sessionId = "session-2"), 1_000L))
    }

    @Test fun expiredAndDeactivatedSessionsCannotRegisterToken() {
        assertFalse(merchantPushSessionCurrent(auth(), auth(), 2_000L))
        assertFalse(merchantPushSessionCurrent(auth(), auth(expiry = 999L), 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(), auth(status = "inactive"), 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(status = "inactive"), auth(), 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(), auth(role = "customer"), 1_000L))
    }
}
