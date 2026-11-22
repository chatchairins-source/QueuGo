package com.queuego.merchant

import org.junit.Assert.*
import org.junit.Test

class MerchantPushIdentityTest {
    @Test fun javaHashCollisionsDoNotAliasNotifications() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(merchantPushIdentity("shop-a", "Aa"), merchantPushIdentity("shop-a", "BB"))
    }
    @Test fun accountAndFieldBoundariesArePartOfIdentity() {
        assertNotEquals(merchantPushIdentity("a", "b|message:1:c"), merchantPushIdentity("a|message:1:b", "c"))
        assertNotEquals(merchantPushIdentity("shop-a", "message"), merchantPushIdentity("shop-b", "message"))
        assertEquals(merchantPushIdentity("shop-a", "message"), merchantPushIdentity("shop-a", "message"))
        assertTrue(merchantPushIdentity("shop-a", "message").matches(Regex("queuego://merchant/push/[0-9a-f]{64}")))
    }
    @Test fun legacyAndMissingReferencePayloadsRemainDistinctAndSupported() {
        fun key(id: String?, ref: String, type: String, fallback: String = "fallback") =
            merchantPushMessageKey(id, ref, type, fallback)
        assertEquals(key("fcm-id", "one", "new"), key("fcm-id", "two", "update"))
        assertNotEquals(key(null, "ab", "c"), key(null, "a", "bc"))
        assertNotEquals(key("reference:1:a|type:1:b", "", ""), key(null, "a", "b"))
        assertNotEquals(key(null, "", "broadcast", "one"), key("", "", "broadcast", "two"))
        assertTrue(key(null, "", "broadcast").startsWith("unidentified:"))
    }
}
