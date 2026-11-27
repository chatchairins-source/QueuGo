package com.queuego.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomerProfileContactTest {
    @Test fun phoneMatchesWebProfilePreference() {
        assertEquals("0812345678", customerProfileContact(" 0812345678 ", "customer@example.com"))
    }

    @Test fun emailIsShownWhenNoPhoneExists() {
        assertEquals("customer@example.com", customerProfileContact("", " customer@example.com "))
    }

    @Test fun syntheticPhoneLoginEmailNeverLeaksIntoProfile() {
        assertNull(customerProfileContact(null, "0812345678@auth.queuetech.local"))
        assertNull(customerProfileContact(" ", " "))
    }
}
