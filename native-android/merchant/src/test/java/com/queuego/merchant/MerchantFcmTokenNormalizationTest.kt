package com.queuego.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantFcmTokenNormalizationTest {
    @Test fun acceptsValidFirebaseToken() {
        assertEquals("abcdefghijklmnop", merchantUsableFcmToken("abcdefghijklmnop"))
        assertEquals("abcdefghijklmnop", merchantUsableFcmToken("  abcdefghijklmnop\n"))
    }

    @Test fun rejectsEmptyWhitespaceAndShortTokens() {
        assertNull(merchantUsableFcmToken(""))
        assertNull(merchantUsableFcmToken("                   "))
        assertNull(merchantUsableFcmToken("  abcdefghijklmno  "))
        assertNull(merchantUsableFcmToken("\t\n"))
    }

    @Test fun rejectsEmbeddedWhitespaceAndControlCharacters() {
        assertNull(merchantUsableFcmToken("abcdefgh ijklmnop"))
        assertNull(merchantUsableFcmToken("abcdefgh\nijklmnop"))
        assertNull(merchantUsableFcmToken("abcdefgh\tijklmnop"))
        assertNull(merchantUsableFcmToken("abcdefghijklmnop\rqrst"))
    }

    @Test fun rejectsEmbeddedNonWhitespaceControlCharacters() {
        assertNull(merchantUsableFcmToken("abcdefgh\u0000ijklmnop"))
        assertNull(merchantUsableFcmToken("abcdefgh\u0007ijklmnop"))
        assertNull(merchantUsableFcmToken("abcdefgh\u007Fijklmnop"))
        assertEquals("abcdefghijklmnop", merchantUsableFcmToken("abcdefghijklmnop"))
    }

    @Test fun boundaryAtSixteenCharactersIsAccepted() {
        assertNull(merchantUsableFcmToken("123456789012345"))
        assertEquals("1234567890123456", merchantUsableFcmToken("1234567890123456"))
    }
}
