package com.queuego.customer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerExpressContactPhoneTest {
    @Test fun acceptsDialableThaiAndInternationalNumbers() {
        listOf("0812345678", "021234567", "+66812345678", "+66 81-234-5678", "(02) 123-4567")
            .forEach { assertTrue("Expected valid phone: $it", validExpressContactPhone(it)) }
    }

    @Test fun rejectsNamesExtensionsControlCharactersAndImpossibleLengths() {
        listOf(
            "", "abcde", "12345", "12345678", "0812345678901234", "+66abc123456",
            "0812345678 ext 9", "++66812345678", "0812345678\n123",
            "0812345678+9", "0812345678\u0000"
        ).forEach { assertFalse("Expected invalid phone: $it", validExpressContactPhone(it)) }
    }
}
