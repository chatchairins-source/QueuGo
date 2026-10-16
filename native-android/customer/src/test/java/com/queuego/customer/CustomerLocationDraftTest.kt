package com.queuego.customer

import org.junit.Assert.*
import org.junit.Test

class CustomerLocationDraftTest {
    private val a = CustomerLocation(14.995, 103.103, "Address A")
    private val b = CustomerLocation(15.005, 103.113, "Address B")

    @Test fun changingPinClearsPreviousGeneratedAddress() {
        val next = CustomerLocationDraft(a, a.address).move(b)
        assertEquals("", next.address)
        assertEquals(b.latitude, next.point?.latitude)
        assertEquals("Address B", next.resolved(next.revision, "Address B").address)
    }

    @Test fun manualAddressWinsOverLateGeocodeAndPinChanges() {
        val initial = CustomerLocationDraft(a, "").move(b)
        val edited = initial.edit("House number supplied by user")
        assertEquals(edited, edited.resolved(initial.revision, "Late generated address"))
        assertEquals(edited.address, edited.move(a).address)
    }

    @Test fun sameCoordinatesDoNotMakeOldResponseCurrentAgain() {
        repeat(30) {
            val initial = CustomerLocationDraft(a, "")
            val roundTrip = initial.move(b).move(a)
            assertEquals("", roundTrip.resolved(initial.revision, "Stale A").address)
            assertEquals("Current A", roundTrip.resolved(roundTrip.revision, "Current A").address)
        }
    }

    @Test fun invalidPinAndBlankGeocodePreserveValidDraft() {
        val draft = CustomerLocationDraft(a, a.address)
        assertEquals(draft, draft.move(CustomerLocation(Double.NaN, 0.0, "")))
        assertEquals(draft, draft.resolved(draft.revision, "  "))
    }
}
