package com.queuego.rider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderInsuranceCenterTest {
    @Test fun parserKeepsOnlyRealServerPolicies() {
        val policies = riderInsurancePoliciesFromRpc(
            JSONArray().put(
                JSONObject()
                    .put("policy_id", "policy-1")
                    .put("coverage_type", "personal_accident")
                    .put("provider_name", "Provider A")
                    .put("product_name", "PA Rider")
                    .put("policy_number", "PA-100")
                    .put("starts_on", "2026-10-01")
                    .put("expires_on", "2027-09-30")
                    .put("status", "active")
                    .put("support_url", "https://example.test/policy")
            )
        )
        assertEquals(1, policies.size)
        assertEquals("Provider A", policies.single().providerName)
        assertEquals("คุ้มครองอยู่", riderInsuranceStatusLabel(policies.single().status))
        assertEquals("ประกันอุบัติเหตุ", riderInsuranceTypeLabel(policies.single().coverageType))
    }

    @Test fun emptyBackendDoesNotInventCoverage() {
        assertTrue(riderInsurancePoliciesFromRpc(JSONArray()).isEmpty())
    }

    @Test fun unsafeSupportUrlIsNotExposed() {
        val policy = riderInsurancePoliciesFromRpc(
            JSONArray().put(
                JSONObject()
                    .put("policy_id", "policy-2")
                    .put("coverage_type", "other")
                    .put("provider_name", "Provider B")
                    .put("starts_on", "2026-10-01")
                    .put("expires_on", "2027-09-30")
                    .put("status", "active")
                    .put("support_url", "http://unsafe.test")
            )
        ).single()
        assertEquals(null, policy.supportUrl)
    }
}
