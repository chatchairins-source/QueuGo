package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderEventFeedbackTest {
    @Test fun pickupFeedbackIsShortAndConfirming() {
        val spec = riderEventFeedbackSpec(RiderEventFeedbackKind.PICKUP_SUCCESS)
        assertTrue(spec.successHaptic)
        assertEquals(1, spec.tones.size)
        assertTrue(spec.gapsMs.isEmpty())
    }

    @Test fun deliveryFeedbackIsMoreDistinctThanPickup() {
        val pickup = riderEventFeedbackSpec(RiderEventFeedbackKind.PICKUP_SUCCESS)
        val delivery = riderEventFeedbackSpec(RiderEventFeedbackKind.DELIVERY_SUCCESS)

        assertTrue(delivery.successHaptic)
        assertEquals(2, delivery.tones.size)
        assertTrue(delivery.tones != pickup.tones)
    }

    @Test fun errorFeedbackCannotBeMistakenForSuccess() {
        val error = riderEventFeedbackSpec(RiderEventFeedbackKind.ACTION_ERROR)
        assertFalse(error.successHaptic)
        assertEquals(2, error.tones.size)
        assertEquals(1, error.gapsMs.size)
    }

    @Test fun everyPatternStaysBriefEnoughForDrivingWorkflow() {
        RiderEventFeedbackKind.entries.forEach { kind ->
            val spec = riderEventFeedbackSpec(kind)
            val duration = spec.tones.sumOf { it.second.toLong() } + spec.gapsMs.sum()
            assertTrue("$kind feedback is too long", duration <= 900L)
        }
    }
}
