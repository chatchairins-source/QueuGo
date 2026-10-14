package com.queuego.rider

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderGrabParityUnifiedTest {
    @Test fun benefitsFailedDeliveryAndEventFeedbackCoexistOnReleaseSuccessor() {
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

        val online = riderEventFeedbackSpec(RiderEventFeedbackKind.GO_ONLINE)
        val offline = riderEventFeedbackSpec(RiderEventFeedbackKind.GO_OFFLINE)
        val accepted = riderEventFeedbackSpec(RiderEventFeedbackKind.JOB_ACCEPTED)
        val completed = riderEventFeedbackSpec(RiderEventFeedbackKind.DELIVERY_SUCCESS)

        assertTrue(online.successHaptic)
        assertTrue(offline.successHaptic)
        assertTrue(accepted.successHaptic)
        assertTrue(completed.successHaptic)
        assertTrue(online.tones != offline.tones)
        assertTrue(accepted.tones != online.tones)
        assertEquals(2, completed.tones.size)
    }
}
