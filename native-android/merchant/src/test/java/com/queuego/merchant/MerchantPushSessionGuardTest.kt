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
        expiry: Long = 2_000L,
        userAuthId: String = "auth-$userId",
        sessionAuthId: String = userAuthId,
        accessToken: String = "access"
    ) = NativeAuth(
        NativeSession(sessionAuthId, accessToken, "refresh", expiry, sessionId),
        NativeUser(userId, userAuthId, "Merchant", role, status)
    )

    @Test fun sameActiveSessionAllowsTokenRefresh() {
        assertTrue(merchantPushSessionCurrent(auth(), auth(), 1_999L))
    }

    @Test fun logoutAndAccountSwitchCannotRegisterToken() {
        assertFalse(merchantPushSessionCurrent(auth(), null, 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(), auth(userId = "shop-2"), 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(), auth(sessionId = "session-2"), 1_000L))
    }

    @Test fun tokenRetrievalFinishingAfterSessionExpiryMustNotSubscribe() {
        val validatedBeforeTokenFetch = auth(expiry = 2_000L)
        assertTrue(merchantPushSessionCurrent(validatedBeforeTokenFetch, auth(), 1_999L))
        assertFalse(merchantPushSessionCurrent(validatedBeforeTokenFetch, auth(), 2_000L))
    }

    @Test fun tokenRetrievalFinishingAfterLogoutOrSessionRotationMustNotSubscribe() {
        val validatedBeforeTokenFetch = auth()
        assertFalse(merchantPushSessionCurrent(validatedBeforeTokenFetch, null, 1_000L))
        assertFalse(merchantPushSessionCurrent(validatedBeforeTokenFetch, auth(sessionId = "new-session"), 1_000L))
        assertFalse(merchantPushSessionCurrent(validatedBeforeTokenFetch, auth(userId = "shop-2"), 1_000L))
    }

    @Test fun expiredValidatedAuthCannotSubscribeEvenIfPersistedSessionWasRefreshed() {
        val staleValidatedAuth = auth(expiry = 1_000L)
        val refreshedCurrentSession = auth(expiry = 5_000L)
        assertFalse(merchantPushSessionCurrent(staleValidatedAuth, refreshedCurrentSession, 1_000L))
        assertFalse(merchantPushSessionCurrent(staleValidatedAuth, refreshedCurrentSession, 1_001L))
        assertTrue(merchantPushSessionCurrent(staleValidatedAuth, refreshedCurrentSession, 999L))
    }

    @Test fun mismatchedAuthPrincipalCannotRegisterPushForSameShopAndSession() {
        val validated = auth()
        assertFalse(merchantPushSessionCurrent(validated, auth(userAuthId = "other-principal"), 1_000L))
        assertFalse(merchantPushSessionCurrent(validated, auth(sessionAuthId = "other-principal"), 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(userAuthId = "other-principal"), auth(), 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(sessionAuthId = "other-principal"), auth(), 1_000L))
        assertTrue(merchantPushSessionCurrent(validated, auth(), 1_000L))
    }

    @Test fun expiredAndDeactivatedSessionsCannotRegisterToken() {
        assertFalse(merchantPushSessionCurrent(auth(), auth(), 2_000L))
        assertFalse(merchantPushSessionCurrent(auth(), auth(expiry = 999L), 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(), auth(status = "inactive"), 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(status = "inactive"), auth(), 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(), auth(role = "customer"), 1_000L))
    }
    @Test fun blankIdentityCannotRegisterFcmTokenEvenWhenBothSnapshotsMatch() {
        assertFalse(merchantPushSessionCurrent(auth(userId = ""), auth(userId = ""), 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(userAuthId = ""), auth(userAuthId = ""), 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(sessionAuthId = ""), auth(sessionAuthId = ""), 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(sessionId = ""), auth(sessionId = ""), 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(), auth(userId = ""), 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(), auth(sessionId = ""), 1_000L))
        assertTrue(merchantPushSessionCurrent(auth(), auth(), 1_000L))
    }

    @Test fun accessTokenRotationDuringFirebaseFetchMustNotRegisterStaleToken() {
        val validated = auth(accessToken = "old-access")
        assertFalse(merchantPushSessionCurrent(validated, auth(accessToken = "new-access"), 1_000L))
        assertFalse(merchantPushSessionCurrent(auth(accessToken = ""), auth(accessToken = ""), 1_000L))
        assertTrue(merchantPushSessionCurrent(validated, auth(accessToken = "old-access"), 1_000L))
    }

    @Test fun initialPushSyncPreflightRejectsExpiredOrUnboundSession() {
        assertTrue(merchantPushNotificationSessionActive(auth(), 1_999L))
        assertFalse(merchantPushNotificationSessionActive(auth(), 2_000L))
        assertFalse(merchantPushNotificationSessionActive(auth(accessToken = "   "), 1_000L))
        assertFalse(merchantPushNotificationSessionActive(auth(sessionId = ""), 1_000L))
        assertFalse(merchantPushNotificationSessionActive(auth(userAuthId = "different"), 1_000L))
        assertFalse(merchantPushNotificationSessionActive(auth(status = "inactive"), 1_000L))
    }

    @Test fun notificationRejectsUnboundOrBlankCachedSession() {
        assertTrue(merchantPushNotificationSessionActive(auth(), 1_999L))
        assertFalse(merchantPushNotificationSessionActive(auth(), 2_000L))
        assertFalse(merchantPushNotificationSessionActive(auth(userId = ""), 1_000L))
        assertFalse(merchantPushNotificationSessionActive(auth(userAuthId = ""), 1_000L))
        assertFalse(merchantPushNotificationSessionActive(auth(sessionAuthId = ""), 1_000L))
        assertFalse(merchantPushNotificationSessionActive(auth(sessionId = ""), 1_000L))
        assertFalse(merchantPushNotificationSessionActive(auth(accessToken = ""), 1_000L))
        assertTrue(merchantPushNotificationSessionActive(auth(accessToken = "valid-access"), 1_000L))
        assertFalse(merchantPushNotificationSessionActive(auth(userAuthId = "other"), 1_000L))
        assertFalse(merchantPushNotificationSessionActive(auth(role = "customer"), 1_000L))
        assertFalse(merchantPushNotificationSessionActive(auth(status = "inactive"), 1_000L))
    }

}
