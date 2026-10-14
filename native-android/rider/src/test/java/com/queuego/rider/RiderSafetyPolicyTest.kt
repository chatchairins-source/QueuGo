package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderSafetyPolicyTest {
    @Test fun officialEmergencyContactsStayExplicitAndDialable() {
        val contacts = riderSafetyContacts()
        assertEquals(setOf("191", "1669", "1155"), contacts.map { it.number }.toSet())
        assertEquals("191", RIDER_EMERGENCY_POLICE)
        assertEquals("1669", RIDER_EMERGENCY_MEDICAL)
        assertEquals("1155", RIDER_TOURIST_POLICE)
        assertTrue(contacts.filter { it.urgent }.map { it.number }.containsAll(listOf("191", "1669")))
    }
}
