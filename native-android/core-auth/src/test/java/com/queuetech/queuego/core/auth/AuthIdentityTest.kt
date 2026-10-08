package com.queuetech.queuego.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AuthIdentityTest {
    @Test
    fun phoneMatchesProductionAuthEmailRule() {
        assertEquals(
            "0812345678@auth.queuetech.local",
            AuthIdentity.toSupabaseEmail("081-234-5678"),
        )
    }

    @Test
    fun emailIsTrimmedAndNormalized() {
        assertEquals(
            "owner@example.com",
            AuthIdentity.toSupabaseEmail("  OWNER@Example.COM  "),
        )
    }

    @Test
    fun blankIdentifierIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AuthIdentity.toSupabaseEmail("   ")
        }
    }
}
