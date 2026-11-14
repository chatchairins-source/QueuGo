package com.queuego.merchant

import com.queuego.shared.NativeAuth
import com.queuego.shared.NativeSession
import com.queuego.shared.NativeUser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantPushRefreshPreflightTest {
    private fun auth(
        expiry: Long = 2_000L,
        role: String = "shop",
        status: String = "active",
        userId: String = "shop-1",
        userAuthId: String = "auth-1",
        sessionAuthId: String = userAuthId
    ) = NativeAuth(
        NativeSession(sessionAuthId, "access", "refresh", expiry, "session-1"),
        NativeUser(userId, userAuthId, "Merchant", role, status)
    )

    private val validToken = "abcdefghijklmnop"

    @Test fun validActiveMerchantMayValidateRefreshedToken() {
        assertTrue(merchantPushRefreshPreflight(auth(), validToken, 1_999L))
    }

    @Test fun exactExpiryAndExpiredCacheMustNotTriggerNetworkValidation() {
        assertFalse(merchantPushRefreshPreflight(auth(), validToken, 2_000L))
        assertFalse(merchantPushRefreshPreflight(auth(), validToken, 2_001L))
    }

    @Test fun revokedOrUnboundCachedIdentityMustNotTriggerNetworkValidation() {
        assertFalse(merchantPushRefreshPreflight(auth(role = "customer"), validToken, 1_000L))
        assertFalse(merchantPushRefreshPreflight(auth(status = "inactive"), validToken, 1_000L))
        assertFalse(merchantPushRefreshPreflight(auth(userId = ""), validToken, 1_000L))
        assertFalse(merchantPushRefreshPreflight(auth(userAuthId = ""), validToken, 1_000L))
        assertFalse(merchantPushRefreshPreflight(auth(sessionAuthId = "other"), validToken, 1_000L))
    }

    @Test fun invalidTokenMustNotTriggerNetworkValidation() {
        assertFalse(merchantPushRefreshPreflight(auth(), "   ", 1_000L))
        assertFalse(merchantPushRefreshPreflight(auth(), "123456789012345", 1_000L))
        assertTrue(merchantPushRefreshPreflight(auth(), "  " + validToken + "  ", 1_000L))
    }
}
