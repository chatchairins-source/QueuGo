package com.queuego.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MerchantPushIntentIsolationTest {

    @Test fun javaStringHashCollisionDoesNotReuseNotificationTapTarget() {
        // "Aa" and "BB" have identical Java String.hashCode values under the same prefix.
        val first = "shop:shop-a|message:Aa"
        val second = "shop:shop-a|message:BB"
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(
            merchantPushIntentRequestCode("shop-a", "Aa"),
            merchantPushIntentRequestCode("shop-a", "BB")
        )
    }
    @Test fun delimiterInShopIdentityCannotAliasDifferentShopAndMessage() {
        // Both pairs produced the same concatenated string before length-prefixing.
        val firstShop = "shop-a"
        val firstMessage = "foo|message:bar"
        val secondShop = "shop-a|message:foo"
        val secondMessage = "bar"
        assertEquals(
            "shop:$firstShop|message:$firstMessage",
            "shop:$secondShop|message:$secondMessage"
        )
        assertNotEquals(
            merchantPushIntentRequestCode(firstShop, firstMessage),
            merchantPushIntentRequestCode(secondShop, secondMessage)
        )
    }

    @Test fun pendingIntentUriUsesFullDigestAndSeparatesShopAndMessage() {
        val first = merchantPushIntentUri("shop-a", "Aa")
        val second = merchantPushIntentUri("shop-a", "BB")
        assertNotEquals(first, second)
        assertNotEquals(first, merchantPushIntentUri("shop-b", "Aa"))
        assertEquals(first, merchantPushIntentUri("shop-a", "Aa"))
        assertEquals(64, first.substringAfterLast('/').length)
        assertEquals(true, first.substringAfterLast('/').all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test fun pendingIntentUriCannotAliasDelimiterBearingFields() {
        assertNotEquals(
            merchantPushIntentUri("shop-a", "foo|message:bar"),
            merchantPushIntentUri("shop-a|message:foo", "bar")
        )
    }

    @Test fun sameShopAndMessageKeepStableRequestCode() {
        assertEquals(
            merchantPushIntentRequestCode("shop-a", "order-123"),
            merchantPushIntentRequestCode("shop-a", "order-123")
        )
    }

    @Test fun accountSwitchDoesNotReusePendingIntentForSameMessage() {
        assertNotEquals(
            merchantPushIntentRequestCode("shop-a", "order-123"),
            merchantPushIntentRequestCode("shop-b", "order-123")
        )
    }

    @Test fun differentMessagesInSameShopHaveDifferentRequestCodes() {
        assertNotEquals(
            merchantPushIntentRequestCode("shop-a", "order-123"),
            merchantPushIntentRequestCode("shop-a", "order-124")
        )
    }

    @Test fun notificationIdForSameMessageIsScopedToShop() {
        val messageId = "fcm-message-42"
        val firstShopNotificationId = merchantPushIntentRequestCode("shop-a", messageId)
        val secondShopNotificationId = merchantPushIntentRequestCode("shop-b", messageId)
        assertNotEquals(firstShopNotificationId, secondShopNotificationId)
        assertEquals(firstShopNotificationId, merchantPushIntentRequestCode("shop-a", messageId))
    }

    @Test fun notificationIdForFallbackReferenceIsScopedToShop() {
        val fallbackReference = "order-123"
        assertNotEquals(
            merchantPushIntentRequestCode("shop-a", fallbackReference),
            merchantPushIntentRequestCode("shop-b", fallbackReference)
        )
    }
    @Test fun messageIdTakesPriorityOverReferenceAndType() {
        assertEquals("fcm:fcm-1", merchantPushMessageKey("fcm-1", "order-1", "new", "fallback"))
    }


    @Test fun fcmMessageIdCannotCollideWithReferenceFallbackKey() {
        val fallback = merchantPushMessageKey(null, "order-7", "accepted", "fallback")
        val explicit = merchantPushMessageKey("reference:order-7|type:accepted", "other", "new", "fallback")
        assertNotEquals(fallback, explicit)
        assertNotEquals(
            merchantPushIntentRequestCode("shop-a", fallback),
            merchantPushIntentRequestCode("shop-a", explicit)
        )
    }

    @Test fun delimiterCharactersCannotAliasDifferentFallbackIdentityFields() {
        val first = merchantPushMessageKey(null, "order|type:paid", "updated", "unused")
        val second = merchantPushMessageKey(null, "order", "paid|type:updated", "unused")
        assertNotEquals(first, second)
        assertNotEquals(
            merchantPushIntentRequestCode("shop-a", first),
            merchantPushIntentRequestCode("shop-a", second)
        )
    }

    @Test fun sameReferenceAndTypeHaveStableIdentityWithoutFcmId() {
        assertEquals(
            merchantPushMessageKey(null, "order-1", "accepted", "first"),
            merchantPushMessageKey("", "order-1", "accepted", "second")
        )
    }

    @Test fun differentTypesOnSameReferenceCannotReplaceTapTarget() {
        assertNotEquals(
            merchantPushMessageKey(null, "order-1", "accepted", "first"),
            merchantPushMessageKey(null, "order-1", "cancelled", "second")
        )
    }

    @Test fun unidentifiedMessagesDoNotReusePreviousTapTarget() {
        assertNotEquals(
            merchantPushMessageKey(null, "", "", "arrival-1"),
            merchantPushMessageKey(null, "", "", "arrival-2")
        )
    }

    @Test fun matchingReferenceAliasesAreAccepted() {
        assertEquals("order-7", merchantPushReferenceId(mapOf("referenceId" to "order-7", "reference_id" to "order-7")))
    }

    @Test fun singleLegacyReferenceAliasIsAccepted() {
        assertEquals("order-7", merchantPushReferenceId(mapOf("reference_id" to "order-7")))
    }

    @Test fun conflictingReferenceAliasesAreRejected() {
        assertEquals(null, merchantPushReferenceId(mapOf("referenceId" to "order-a", "reference_id" to "order-b")))
    }

    @Test fun blankReferenceAliasCannotOverrideValidReference() {
        assertEquals(null, merchantPushReferenceId(mapOf("referenceId" to "", "reference_id" to "order-7")))
    }

    @Test fun referenceWithLeadingOrTrailingWhitespaceIsRejected() {
        assertEquals(null, merchantPushReferenceId(mapOf("referenceId" to " order-7")))
        assertEquals(null, merchantPushReferenceId(mapOf("reference_id" to "order-7 ")))
    }

    @Test fun referenceWithEmbeddedWhitespaceIsRejected() {
        assertEquals(null, merchantPushReferenceId(mapOf("referenceId" to "order 7")))
        assertEquals(null, merchantPushReferenceId(mapOf("referenceId" to "order\t7")))
    }

    @Test fun referenceWithInvisibleControlCharactersIsRejected() {
        assertEquals(null, merchantPushReferenceId(mapOf("referenceId" to "order\u00007")))
        assertEquals(null, merchantPushReferenceId(mapOf("reference_id" to "order\u007f7")))
    }

    @Test fun referenceWithInvisibleUnicodeFormatCharactersIsRejected() {
        // U+200B zero-width space, U+200D zero-width joiner, U+202E bidi override.
        assertEquals(null, merchantPushReferenceId(mapOf("referenceId" to "order\u200B7")))
        assertEquals(null, merchantPushReferenceId(mapOf("reference_id" to "order\u200D7")))
        assertEquals(null, merchantPushReferenceId(mapOf("referenceId" to "order\u202E7")))
    }

    @Test fun isolatedUtf16SurrogatesAreRejectedBeforeRouting() {
        assertEquals(null, merchantPushReferenceId(mapOf("referenceId" to "order${0xD800.toChar()}")))
        assertEquals(null, merchantPushReferenceId(mapOf("reference_id" to "order${0xDC00.toChar()}")))
        assertEquals(null, merchantPushReferenceId(mapOf("referenceId" to "${0xD800.toChar()}order${0xDC00.toChar()}")))
    }

    @Test fun validUtf16SurrogatePairRemainsDistinctAndAccepted() {
        assertEquals(true, merchantPushWellFormedUtf16("order${0xD83D.toChar()}${0xDE80.toChar()}"))
        assertEquals("order${0xD83D.toChar()}${0xDE80.toChar()}", merchantPushReferenceId(mapOf("referenceId" to "order${0xD83D.toChar()}${0xDE80.toChar()}")))
    }

    @Test fun validOpaqueReferenceStillRoutes() {
        assertEquals("order-7_A", merchantPushReferenceId(mapOf("referenceId" to "order-7_A")))
    }

    @Test fun absentReferencePreservesNonOrderPush() {
        assertEquals("", merchantPushReferenceId(emptyMap()))
    }

    @Test fun expiredFcmAlertIsNotDisplayed() {
        assertEquals(false, merchantPushWithinDeliveryLifetime(1_000L, 30, 31_001L))
    }

    @Test fun alertAtDeliveryDeadlineIsAllowed() {
        assertEquals(true, merchantPushWithinDeliveryLifetime(1_000L, 30, 31_000L))
    }

    @Test fun missingTimestampAndImmediateDeliveryRemainCompatible() {
        assertEquals(true, merchantPushWithinDeliveryLifetime(0L, 30, 500_000L))
        assertEquals(true, merchantPushWithinDeliveryLifetime(1_000L, 0, 500_000L))
    }

    @Test fun extremeFutureTimestampDoesNotOverflowIntoExpiredAlert() {
        assertEquals(true, merchantPushWithinDeliveryLifetime(Long.MAX_VALUE, 30, 1_000L))
        assertEquals(true, merchantPushWithinDeliveryLifetime(Long.MAX_VALUE, 30, Long.MIN_VALUE))
    }

    @Test fun largeElapsedLifetimeStillExpiresWithoutOverflow() {
        assertEquals(false, merchantPushWithinDeliveryLifetime(1L, Int.MAX_VALUE, Long.MAX_VALUE))
    }

    @Test fun futureSentTimestampDoesNotPrematurelyExpire() {
        assertEquals(true, merchantPushWithinDeliveryLifetime(60_000L, 30, 59_000L))
    }

}
