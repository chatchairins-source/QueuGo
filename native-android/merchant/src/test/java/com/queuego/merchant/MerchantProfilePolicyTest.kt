package com.queuego.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantProfilePolicyTest {
    @Test fun privacyPolicyUsesThePublicProductionHttpsPage() {
        assertTrue(MERCHANT_PRIVACY_POLICY_URL.startsWith("https://"))
        assertEquals(
            "https://chatchairins-source.github.io/QueuGo/docs/privacy.html",
            MERCHANT_PRIVACY_POLICY_URL
        )
    }
}
