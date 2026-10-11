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
}
