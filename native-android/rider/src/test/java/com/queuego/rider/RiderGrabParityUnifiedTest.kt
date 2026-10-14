package com.queuego.rider

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderGrabParityUnifiedTest {
    @Test fun benefitsAndFailedDeliveryContractsCoexistOnSingleSuccessor() {
        assertTrue(riderBenefitsFromRpc(JSONArray()).isEmpty())
        assertEquals(
            setOf(
                "customer_unreachable",
                "recipient_refuses_delivery",
                "recipient_refuses_cash",
                "wrong_dropoff",
                "unsafe_dropoff",
                "other"
            ),
            riderDeliveryIssueReasons.map { it.key }.toSet()
        )
    }
}
