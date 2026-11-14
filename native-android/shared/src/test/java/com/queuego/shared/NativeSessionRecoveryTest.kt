package com.queuego.shared

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.*
import org.junit.Test

class NativeSessionRecoveryTest {
    @Test fun transientErrorsDoNotInvalidatePersistedCredentials() {
        listOf(IOException("offline"), SocketTimeoutException("timeout"),
            NativeAuthHttpException(429, "rate limit"), NativeAuthHttpException(503, "unavailable"),
            NativeAuthHttpException(403, "policy denied"), IllegalStateException("malformed response"))
            .forEach { assertFalse(shouldClearNativeSession(it)) }
    }
    @Test fun authoritativeRejectionRequiresNewLogin() {
        assertTrue(shouldClearNativeSession(NativeSessionInvalidException("replaced")))
        assertTrue(shouldClearNativeSession(NativeAuthHttpException(401, "expired")))
    }
}
