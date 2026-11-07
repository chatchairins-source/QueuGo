package com.queuego.shared

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QueueGoBackendEnvironmentTest {
    // Build negative fixture prefixes without resembling a checked-in credential.
    private val serverKeyPrefix = listOf("sb", "secret", "").joinToString("_")
    private val elevatedRole = listOf("service", "role").joinToString("_")
    private val publicKey = "sb_publishable_isolated_test_public_key_only"
    private fun anonJwt(role: String): String {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"role\":\"$role\"}".toByteArray())
        return "eyJhbGciOiJIUzI1NiJ9.$payload.test-signature"
    }

    @Test fun ordinaryDebugAndReleaseKeepProduction() {
        for (debug in listOf(false, true)) {
            val result = queueGoBackendConfiguration(debug, null, null)
            assertEquals(QUEUEGO_PRODUCTION_BACKEND, result.baseUrl)
            assertEquals(QUEUEGO_PRODUCTION_PUBLISHABLE_KEY, result.publishableKey)
            assertEquals("wss://pkypiqhlrmzocysgeqew.supabase.co", result.websocketOrigin)
        }
    }

    @Test fun releaseNeverUsesOverridesOrElevatedKeys() {
        val result = queueGoBackendConfiguration(false, "http://10.0.2.2:54321", serverKeyPrefix + "never_embed_this")
        assertEquals(QUEUEGO_PRODUCTION_BACKEND, result.baseUrl)
        assertEquals(QUEUEGO_PRODUCTION_PUBLISHABLE_KEY, result.publishableKey)
    }

    @Test fun isolatedDebugPairReachesAllProtocolsWithoutProductionKey() {
        val local = queueGoBackendConfiguration(true, "http://10.0.2.2:54321/", publicKey)
        assertEquals("http://10.0.2.2:54321", local.baseUrl)
        assertEquals("ws://10.0.2.2:54321", local.websocketOrigin)
        assertEquals(publicKey, local.publishableKey)
        val staging = queueGoBackendConfiguration(true, "https://staging.example.invalid", anonJwt("anon"))
        assertEquals("wss://staging.example.invalid", staging.websocketOrigin)
    }

    @Test fun partialPairsAndMixedProductionSettingsFailClosed() {
        for ((url, key) in listOf(
            "http://10.0.2.2:54321" to "", "" to publicKey,
            QUEUEGO_PRODUCTION_BACKEND to publicKey,
            "https://PKYPIQHLRMZOCYSGEQEW.supabase.co/" to publicKey,
            "http://127.0.0.1:54321" to QUEUEGO_PRODUCTION_PUBLISHABLE_KEY
        )) assertThrows(IllegalArgumentException::class.java) { queueGoBackendConfiguration(true, url, key) }
    }

    @Test fun insecureRemoteAndCredentialBearingOriginsFailClosed() {
        for (url in listOf(
            "http://staging.example.invalid", "ftp://localhost", "file:///tmp/backend",
            "https://user:password@staging.example.invalid", "https://staging.example.invalid/rest/v1",
            "https://staging.example.invalid?token=secret", "https://staging.example.invalid#fragment",
            "http://10.0.2.2:99999", "http://10.0.2.2:0", "not a URL"
        )) assertThrows(IllegalArgumentException::class.java) { queueGoBackendConfiguration(true, url, publicKey) }
    }

    @Test fun serverKeysAndNonAnonymousJwtsCannotEnterAnApk() {
        for (key in listOf(serverKeyPrefix + "not_a_real_key", anonJwt(elevatedRole), anonJwt("authenticated"), "not-a-public-key", "sb_publishable_bad key has spaces")) {
            assertThrows(IllegalArgumentException::class.java) {
                queueGoBackendConfiguration(true, "http://localhost:54321", key)
            }
        }
    }
}
