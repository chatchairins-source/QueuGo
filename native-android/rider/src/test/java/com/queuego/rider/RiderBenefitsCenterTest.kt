package com.queuego.rider

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderBenefitsCenterTest {
    @Test fun parserKeepsOnlyRealServerBenefits() {
        val rows = riderBenefitsFromRpc(
            JSONArray().put(
                JSONObject()
                    .put("benefit_id", "benefit-1")
                    .put("category", "fuel")
                    .put("provider_name", "Partner A")
                    .put("title", "สิทธิ์ Rider")
                    .put("starts_on", "2026-10-14")
                    .put("expires_on", "2026-12-31")
                    .put("action_url", "https://example.test/benefit")
            )
        )
        assertEquals(1, rows.size)
        assertEquals("Partner A", rows.single().providerName)
        assertEquals("น้ำมัน / พลังงาน", riderBenefitCategoryLabel(rows.single().category))
    }

    @Test fun emptyBackendDoesNotInventBenefits() {
        assertTrue(riderBenefitsFromRpc(JSONArray()).isEmpty())
    }

    @Test fun unsafeActionUrlIsNotExposed() {
        val benefit = riderBenefitsFromRpc(
            JSONArray().put(
                JSONObject()
                    .put("benefit_id", "benefit-2")
                    .put("category", "other")
                    .put("provider_name", "Partner B")
                    .put("title", "Benefit")
                    .put("starts_on", "2026-10-14")
                    .put("action_url", "http://unsafe.test")
            )
        ).single()
        assertEquals(null, benefit.actionUrl)
    }
}
