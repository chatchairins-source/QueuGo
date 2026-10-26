package com.queuego.rider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderWorkPreferencesTest {
    private fun payload(
        auto: Boolean = false,
        bundle: Boolean = false,
        services: List<String> = listOf("food", "grocery", "market"),
        ride: Boolean = false,
        express: Boolean = true,
        bundleCap: Boolean = true
    ) = JSONObject()
        .put("auto_accept", auto)
        .put("bundle_enabled", bundle)
        .put("services", JSONArray(services))
        .put(
            "capabilities",
            JSONObject()
                .put("food", true)
                .put("grocery", true)
                .put("market", true)
                .put("express", express)
                .put("ride", ride)
                .put("bundle", bundleCap)
        )

    @Test fun parsesServerAuthoritativeWorkPreferences() {
        val parsed = riderWorkPreferencesFromRpc(
            payload(auto = true, bundle = true, services = listOf("food", "express"))
        )
        assertTrue(parsed.autoAccept)
        assertTrue(parsed.bundleEnabled)
        assertEquals(setOf("food", "express"), parsed.services)
        assertTrue(parsed.capabilities.express)
        assertFalse(parsed.capabilities.ride)
    }

    @Test fun rejectsUnknownOrEmptyServicesFailClosed() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            riderWorkPreferencesFromRpc(payload(services = listOf("food", "unknown")))
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            riderWorkPreferencesFromRpc(payload(services = emptyList()))
        }
    }

    @Test fun requiresExplicitBooleanCapabilities() {
        val bad = payload().also {
            it.getJSONObject("capabilities").remove("bundle")
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            riderWorkPreferencesFromRpc(bad)
        }
    }
}
