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
}
