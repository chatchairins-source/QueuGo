package com.queuego.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantFcmUnicodeTokenTest {
    private val validToken = "abcdefghijklmnopqrstuvwxyz0123456789"

    @Test fun validOpaqueTokenIsAccepted() {
        assertEquals(validToken, merchantUsableFcmToken(validToken))
    }

    @Test fun leadingAndTrailingWhitespaceIsTrimmed() {
        assertEquals(validToken, merchantUsableFcmToken("  $validToken\n"))
    }

    @Test fun invisibleUnicodeFormatCharactersAreRejected() {
        assertNull(merchantUsableFcmToken("abc\u200B$validToken"))
        assertNull(merchantUsableFcmToken("abc\u200D$validToken"))
        assertNull(merchantUsableFcmToken("abc\u202E$validToken"))
    }

    @Test fun embeddedWhitespaceAndControlsRemainRejected() {
        assertNull(merchantUsableFcmToken("abc def$validToken"))
        assertNull(merchantUsableFcmToken("abc\u0000$validToken"))
    }
}
