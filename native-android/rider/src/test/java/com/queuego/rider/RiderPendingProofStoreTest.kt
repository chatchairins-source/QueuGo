package com.queuego.rider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderPendingProofStoreTest {
    @Test fun serverStageNeverReopensCommittedPickup() {
        assertTrue(riderVerificationResumeAllowed("pickup", "ready", false, false))
        listOf("picked_up", "in_progress", "completed", "cancelled").forEach {
            assertFalse(riderVerificationResumeAllowed("pickup", it, false, false))
        }
    }

    @Test fun deliveryCashResumeRequiresAuthoritativeCustomerArrival() {
        assertTrue(riderVerificationResumeAllowed("delivery", "in_progress", true, false))
        assertFalse(riderVerificationResumeAllowed("delivery", "in_progress", false, false))
        assertFalse(riderVerificationResumeAllowed("delivery", "completed", true, false))
    }

    @Test fun marketResumeCannotRepeatCompletedStop() {
        assertTrue(riderVerificationResumeAllowed("marketPickup", "ready", false, true))
        assertFalse(riderVerificationResumeAllowed("marketPickup", "ready", false, false))
        assertFalse(riderVerificationResumeAllowed("marketPickup", "in_progress", false, true))
    }

    private val draft = RiderProofDraft(
        workId = "order-1",
        mode = "delivery",
        marketPickupId = null,
        uri = "content://queuego/proof.jpg",
        createdAtMs = 1_000L
    )

    @Test fun proofDraftNeverCrossesOrdersOrWorkflowStages() {
        assertTrue(riderProofDraftMatches(draft, "order-1", "delivery", null))
        assertFalse(riderProofDraftMatches(draft, "order-2", "delivery", null))
        assertFalse(riderProofDraftMatches(draft, "order-1", "pickup", null))
    }

    @Test fun marketPickupProofRequiresExactStop() {
        val market = draft.copy(mode = "marketPickup", marketPickupId = "pickup-a")
        assertTrue(riderProofDraftMatches(market, "order-1", "marketPickup", "pickup-a"))
        assertFalse(riderProofDraftMatches(market, "order-1", "marketPickup", "pickup-b"))
        assertFalse(riderProofDraftMatches(market, "order-1", "marketPickup", null))
    }

    @Test fun returnProofUsesIndependentWorkflowIdentity() {
        val returned = draft.copy(workId = "return-case-1", mode = "deliveryReturn")
        assertTrue(riderProofDraftMatches(returned, "return-case-1", "deliveryReturn", null))
        assertFalse(riderProofDraftMatches(returned, "order-1", "delivery", null))
    }

    @Test fun confirmedProofHasBoundedRecoveryLifetime() {
        assertTrue(
            riderProofDraftFresh(
                1_000L,
                1_000L + RIDER_PROOF_DRAFT_MAX_AGE_MS,
                RIDER_PROOF_DRAFT_MAX_AGE_MS
            )
        )
        assertFalse(
            riderProofDraftFresh(
                1_000L,
                1_001L + RIDER_PROOF_DRAFT_MAX_AGE_MS,
                RIDER_PROOF_DRAFT_MAX_AGE_MS
            )
        )
    }

    @Test fun futureOrInvalidTimestampCannotBeRestored() {
        assertFalse(riderProofDraftFresh(0L, 10_000L, RIDER_PROOF_DRAFT_MAX_AGE_MS))
        assertFalse(riderProofDraftFresh(20_000L, 10_000L, RIDER_PROOF_DRAFT_MAX_AGE_MS))
    }

    @Test fun deliveryAndReturnUseSeparatePersistentSlots() {
        assertTrue(RIDER_PROOF_SLOT_DELIVERY != RIDER_PROOF_SLOT_RETURN)
    }
}
