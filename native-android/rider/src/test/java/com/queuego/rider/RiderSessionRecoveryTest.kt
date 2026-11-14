package com.queuego.rider

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.*
import org.junit.Test

class RiderSessionRecoveryTest {
    @Test fun offlineAndServerFailureKeepEncryptedSession() {
        listOf(IOException("offline"), SocketTimeoutException("timeout"),
            RiderHttpException(503, "unavailable"), RiderHttpException(429, "rate limit"),
            RiderHttpException(403, "RPC denied"), IllegalStateException("unexpected response"))
            .forEach { assertFalse(shouldClearRiderSession(it)) }
    }

    @Test fun revocationAndExpiredAuthenticationRequireLogin() {
        assertTrue(shouldClearRiderSession(RiderSessionInvalidException("session replaced")))
        assertTrue(shouldClearRiderSession(RiderHttpException(401, "expired")))
    }
}
