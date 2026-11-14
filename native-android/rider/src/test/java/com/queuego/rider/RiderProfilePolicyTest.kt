package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderProfilePolicyTest {
    @Test fun privacyPolicyUsesThePublicProductionHttpsPage() {
        assertTrue(RIDER_PRIVACY_POLICY_URL.startsWith("https://"))
        assertEquals(
            "https://chatchairins-source.github.io/QueuGo/docs/privacy.html",
            RIDER_PRIVACY_POLICY_URL
        )
    }
    @Test fun profilePhotoRejectsBlankAndNullMetadataButKeepsRealRegistrationImage() {
        assertEquals(null, riderProfilePhoto(null))
        assertEquals(null, riderProfilePhoto(org.json.JSONObject().put("profileImage", "")))
        assertEquals(null, riderProfilePhoto(org.json.JSONObject().put("profileImage", org.json.JSONObject.NULL)))
        assertEquals(
            "data:image/jpeg;base64,abc123",
            riderProfilePhoto(org.json.JSONObject().put("profileImage", "data:image/jpeg;base64,abc123"))
        )
    }
    @Test fun ratingSummaryUsesOnlyServerValuesAndHandlesEmptySummary() {
        val rated = riderRatingSummaryFromRpc(
            org.json.JSONObject()
                .put("average", 4.75)
                .put("count", 8)
                .put("distribution", org.json.JSONObject().put("5", 6).put("4", 2))
        )
        assertEquals(4.75, rated.average!!, 0.0001)
        assertEquals(8, rated.count)
        assertEquals(6, rated.distribution[5])
        assertEquals(2, rated.distribution[4])

        val empty = riderRatingSummaryFromRpc(org.json.JSONObject().put("average", org.json.JSONObject.NULL).put("count", 0))
        assertEquals(null, empty.average)
        assertEquals(0, empty.count)
    }
}
