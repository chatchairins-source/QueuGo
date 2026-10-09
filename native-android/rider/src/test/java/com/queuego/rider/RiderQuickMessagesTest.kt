package com.queuego.rider

import org.junit.Assert.*
import org.junit.Test

class RiderQuickMessagesTest {
    @Test fun fullListRejectsSixthMessageAndRemovalAllowsReplacement() {
        try { addRiderQuickMessage(riderDefaultQuickMessages, "ใหม่"); fail("Sixth message accepted") }
        catch (_: IllegalArgumentException) { }
        val replacement = addRiderQuickMessage(riderDefaultQuickMessages.dropLast(1), "  แจ้งลูกค้า  ")
        assertEquals(5, replacement.size)
        assertEquals("แจ้งลูกค้า", replacement.last())
    }
    @Test fun customMessageUsesWebLengthLimitAndRejectsBlank() {
        assertEquals(100, addRiderQuickMessage(emptyList(), "ก".repeat(101)).single().length)
        try { addRiderQuickMessage(emptyList(), "  "); fail("Blank message accepted") }
        catch (_: IllegalArgumentException) { }
    }
}
