package com.queuego.shared

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeCustomerRegistrationTest {
    private fun form(email: String? = null) = NativeCustomerRegistration(
        " ลูกค้าทดสอบ ", "0812345678", email, "GoodPassword12!", "GoodPassword12!", 14.99, 103.10)
    private suspend fun failure(block: suspend () -> Unit): Exception {
        try { block() } catch (error: Exception) { return error }
        throw AssertionError("Expected failure")
    }

    @Test fun invalidInputsCannotSendSignup() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val api = NativeAuthApi(server.url("/").toString())
            listOf(form().copy(name = " "), form().copy(password = "weak"),
                form().copy(confirmation = "mismatch"), form().copy(latitude = Double.NaN),
                form().copy(longitude = 181.0), form(email = "invalid"), form().copy(phone = "123"))
                .forEach { invalid -> assertTrue(failure { api.registerCustomer(invalid, "device") } is IllegalArgumentException) }
            assertEquals(0, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun signupUsesProductionMetadataThenAuthorizesActualCustomer() = runBlocking {
        for (email in listOf(null, "customer@example.com")) {
            val server = MockWebServer(); server.start()
            try {
                server.enqueue(MockResponse().setBody("""{"user":{"id":"auth-id"}}"""))
                server.enqueue(MockResponse().setBody("""{"access_token":"token","refresh_token":"refresh","user":{"id":"auth-id"}}"""))
                server.enqueue(MockResponse().setBody("""[{"id":"user-id","role":"customer","status":"active","name":"ลูกค้า"}]"""))
                server.enqueue(MockResponse().setBody("true"))
                val result = NativeAuthApi(server.url("/").toString()).registerCustomer(form(email), "device")
                assertEquals("customer", result.user.role)
                val signup = server.takeRequest()
                assertEquals("/auth/v1/signup", signup.path)
                assertNull(signup.getHeader("Authorization"))
                val body = JSONObject(signup.body.readUtf8())
                assertEquals(email ?: "0812345678@auth.queuetech.local", body.getString("email"))
                val metadata = body.getJSONObject("data")
                assertEquals("customer", metadata.getString("role"))
                assertEquals("ลูกค้าทดสอบ", metadata.getString("name"))
                assertEquals(14.99, metadata.getDouble("latitude"), 0.0001)
                assertEquals(103.10, metadata.getDouble("longitude"), 0.0001)
                assertEquals("/auth/v1/token?grant_type=password", server.takeRequest().path)
                assertEquals("Bearer token", server.takeRequest().getHeader("Authorization"))
                assertEquals("/rest/v1/rpc/claim_active_session", server.takeRequest().path)
                assertEquals(4, server.requestCount)
            } finally { server.shutdown() }
        }
    }

    @Test fun createdAccountNeverReplaysSignupWhenLoginFails() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"user":{"id":"auth-id"}}"""))
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"message":"unavailable"}"""))
            assertTrue(failure { NativeAuthApi(server.url("/").toString()).registerCustomer(form(), "device") }
                is NativeCustomerSignupCompletedException)
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun ambiguousSignupDoesNotClaimSuccessOrAttemptLogin() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"message":"unavailable"}"""))
            assertTrue(failure { NativeAuthApi(server.url("/").toString()).registerCustomer(form(), "device") }
                is NativeCustomerSignupUncertainException)
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun existingAccountSignupRejectionRemainsAuthoritative() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(422).setBody("""{"message":"already registered"}"""))
            assertTrue(failure { NativeAuthApi(server.url("/").toString()).registerCustomer(form(), "device") }
                is NativeAuthHttpException)
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun signupCannotAuthorizePendingOrWrongRoleAccount() = runBlocking {
        for ((role, status) in listOf("customer" to "pending", "shop" to "active")) {
            val server = MockWebServer(); server.start()
            try {
                server.enqueue(MockResponse().setBody("{}"))
                server.enqueue(MockResponse().setBody("""{"access_token":"token","user":{"id":"auth-id"}}"""))
                server.enqueue(MockResponse().setBody("""[{"id":"user-id","role":"$role","status":"$status"}]"""))
                assertTrue(failure { NativeAuthApi(server.url("/").toString()).registerCustomer(form(), "device") }
                    is NativeCustomerSignupCompletedException)
                assertEquals(3, server.requestCount)
            } finally { server.shutdown() }
        }
    }
}
