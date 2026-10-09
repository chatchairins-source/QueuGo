package com.queuego.shared

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class NativeAuthRecoveryTest {
    private fun cached(expiry: Long = Long.MAX_VALUE) = NativeAuth(
        NativeSession("auth-id", "old-access", "old-refresh", expiry, "session-id"),
        NativeUser("user-id", "auth-id", "Customer", "customer", "active")
    )
    private suspend fun failure(block: suspend () -> Unit): Throwable {
        try { block() } catch (error: Exception) { return error }
        throw AssertionError("Expected validation to fail")
    }

    @Test fun rotatedTokenSurvivesDownstreamServerFailure() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"access_token":"new-access","refresh_token":"new-refresh","expires_at":9999999999}"""))
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"message":"unavailable"}"""))
            var preserved: NativeAuth? = null
            val error = failure {
                NativeAuthApi(server.url("/").toString()).validate(cached(0), "customer") { preserved = it }
            }
            assertFalse(shouldClearNativeSession(error))
            assertEquals("new-refresh", preserved?.session?.refreshToken)
            assertEquals("new-access", preserved?.session?.accessToken)
            assertEquals("/auth/v1/token?grant_type=refresh_token", server.takeRequest().path)
            assertEquals("Bearer new-access", server.takeRequest().getHeader("Authorization"))
        } finally { server.shutdown() }
    }

    @Test fun replacedSessionIsAuthoritativeAndDoesNotReadAccount() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("false"))
            val error = failure { NativeAuthApi(server.url("/").toString()).validate(cached(), "customer") }
            assertTrue(shouldClearNativeSession(error))
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun invalidRefreshTokenClearsButRateLimitKeepsCredentials() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = NativeAuthApi(server.url("/").toString())
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error_code":"refresh_token_not_found","message":"invalid refresh"}"""))
            assertTrue(shouldClearNativeSession(failure { api.validate(cached(0), "customer") }))
            server.enqueue(MockResponse().setResponseCode(429).setBody("""{"message":"rate limited"}"""))
            assertFalse(shouldClearNativeSession(failure { api.validate(cached(0), "customer") }))
        } finally { server.shutdown() }
    }

    @Test fun roleAndSuspendedStatusCannotRestoreAccount() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = NativeAuthApi(server.url("/").toString())
            for ((role, status) in listOf("merchant" to "active", "customer" to "suspended", "customer" to "deleted")) {
                server.enqueue(MockResponse().setBody("true"))
                server.enqueue(MockResponse().setBody("""[{"id":"user-id","name":"Account","role":"$role","status":"$status"}]"""))
                assertTrue(shouldClearNativeSession(failure { api.validate(cached(), "customer") }))
            }
        } finally { server.shutdown() }
    }
}
