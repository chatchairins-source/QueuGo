package com.queuego.shared

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePosStaffAuthTest {
    @Test
    fun merchantLoginPrefersActivePosStaffBeforePublicUserRole() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody(
                """{"access_token":"token","refresh_token":"refresh","expires_at":9999999999,"user":{"id":"auth-staff"}}"""
            ))
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody(
                """[{"user_id":"auth-staff","shop_id":"shop-id","display_name":"แคชเชียร์","active":true}]"""
            ))
            val auth = NativeAuthApi(server.url("/").toString())
                .signInMerchantOrStaff("staff@example.com", "password123", "device")
            assertEquals("pos_staff", auth.user.role)
            assertEquals("แคชเชียร์", auth.user.name)
            assertEquals(3, server.requestCount)
            assertEquals("/auth/v1/token?grant_type=password", server.takeRequest().path)
            assertTrue(server.takeRequest().path!!.startsWith("/rest/v1/users?select="))
            assertTrue(server.takeRequest().path!!.startsWith("/rest/v1/pos_staff?select="))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun merchantOwnerLoginStillClaimsNativeActiveSession() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody(
                """{"access_token":"token","refresh_token":"refresh","expires_at":9999999999,"user":{"id":"auth-shop"}}"""
            ))
            server.enqueue(MockResponse().setBody(
                """[{"id":"user-id","name":"เจ้าของร้าน","role":"shop","status":"active","auth_user_id":"auth-shop"}]"""
            ))
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("true"))
            val auth = NativeAuthApi(server.url("/").toString())
                .signInMerchantOrStaff("0812345678", "password123", "device")
            assertEquals("shop", auth.user.role)
            assertEquals(4, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun staffJoinUsesExistingAccountAndInviteRpc() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody(
                """{"access_token":"token","refresh_token":"refresh","expires_at":9999999999,"user":{"id":"auth-staff"}}"""
            ))
            server.enqueue(MockResponse().setBody(""""shop-id""""))
            server.enqueue(MockResponse().setBody(
                """[{"user_id":"auth-staff","shop_id":"shop-id","display_name":"พนักงาน","active":true}]"""
            ))
            val auth = NativeAuthApi(server.url("/").toString())
                .joinPosStaff("พนักงาน", "staff@example.com", "password123", "secret-code")
            assertEquals("pos_staff", auth.user.role)
            server.takeRequest()
            val join = server.takeRequest()
            assertEquals("/rest/v1/rpc/pos_join_shop", join.path)
            val payload = JSONObject(join.body.readUtf8())
            assertEquals("secret-code", payload.getString("p_secret"))
            assertEquals("พนักงาน", payload.getString("p_display_name"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun staffJoinCanCreateAccountThenJoinWithoutFakeShopProfile() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"message":"invalid credentials"}"""))
            server.enqueue(MockResponse().setBody(
                """{"access_token":"token","refresh_token":"refresh","expires_at":9999999999,"user":{"id":"auth-new"}}"""
            ))
            server.enqueue(MockResponse().setBody(""""shop-id""""))
            server.enqueue(MockResponse().setBody(
                """[{"user_id":"auth-new","shop_id":"shop-id","display_name":"ใหม่","active":true}]"""
            ))
            val auth = NativeAuthApi(server.url("/").toString())
                .joinPosStaff("ใหม่", "new@example.com", "password123", "secret")
            assertEquals("pos_staff", auth.user.role)
            server.takeRequest()
            val signup = server.takeRequest()
            assertEquals("/auth/v1/signup", signup.path)
            val data = JSONObject(signup.body.readUtf8()).getJSONObject("data")
            assertEquals("customer", data.getString("role"))
        } finally {
            server.shutdown()
        }
    }

    @Test(expected = NativeSessionInvalidException::class)
    fun suspendedPublicProfileCannotBypassThroughActivePosStaff() {
        runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody(
                """{"access_token":"token","user":{"id":"auth-staff"}}"""
            ))
            server.enqueue(MockResponse().setBody(
                """[{"id":"user-id","name":"ระงับ","role":"customer","status":"suspended","auth_user_id":"auth-staff"}]"""
            ))
            NativeAuthApi(server.url("/").toString())
                .signInMerchantOrStaff("staff@example.com", "password123", "device")
        } finally {
            server.shutdown()
        }
        }
    }

    @Test(expected = NativeSessionInvalidException::class)
    fun inactiveStaffCannotEnterMerchantPos() {
        runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody(
                """{"access_token":"token","user":{"id":"auth-staff"}}"""
            ))
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody(
                """[{"user_id":"auth-staff","shop_id":"shop-id","display_name":"ปิด","active":false}]"""
            ))
            NativeAuthApi(server.url("/").toString())
                .signInMerchantOrStaff("staff@example.com", "password123", "device")
        } finally {
            server.shutdown()
        }
        }
    }
}
