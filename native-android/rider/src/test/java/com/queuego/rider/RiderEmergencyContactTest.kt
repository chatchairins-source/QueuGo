package com.queuego.rider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderEmergencyContactTest {
    @Test fun emptyRpcDoesNotInventEmergencyContact() {
        val contact = riderEmergencyContactFromRpc(JSONObject().put("configured", false))
        assertFalse(contact.configured)
        assertNull(contact.name)
        assertNull(contact.phone)
    }

    @Test fun configuredRpcKeepsOnlyReturnedContact() {
        val contact = riderEmergencyContactFromRpc(
            JSONObject()
                .put("configured", true)
                .put("name", "แม่")
                .put("phone", "+66812345678")
                .put("updated_at", "2026-10-14T06:50:58Z")
        )
        assertTrue(contact.configured)
        assertEquals("แม่", contact.name)
        assertEquals("+66812345678", contact.phone)
    }

    @Test fun officialEmergencyNumbersRemainSeparateFromPersonalContact() {
        assertEquals(setOf("191", "1669", "1155"), riderSafetyContacts().map { it.number }.toSet())
    }
}
