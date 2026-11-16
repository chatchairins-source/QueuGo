package com.queuego.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MerchantPushIntentIsolationTest {

    @Test fun notificationPreviewDoesNotSplitSupplementaryUnicode() {
        val rocket = String(Character.toChars(0x1F680))
        val text = "abc" + rocket + "tail"
        assertEquals("abc", merchantPushSafePreview(text, 4))
        assertEquals("abc" + rocket, merchantPushSafePreview(text, 5))
        assertEquals(text, merchantPushSafePreview(text, 20))
        assertEquals("", merchantPushSafePreview(text, 0))
    }


    @Test fun notificationPreviewReplacesIsolatedSurrogates() {
        val high = 0xD800.toChar()
        val low = 0xDC00.toChar()
        assertEquals("a\uFFFDb", merchantPushSafePreview("a" + high + "b", 80))
        assertEquals("\uFFFDx", merchantPushSafePreview(low + "x", 80))
        assertEquals("a\uFFFD", merchantPushSafePreview("a" + high + "b", 2))
    }

    @Test fun notificationPreviewHonorsLimitWithSupplementaryAndMalformedUnits() {
        val rocket = String(Character.toChars(0x1F680))
        assertEquals("", merchantPushSafePreview(rocket + "tail", 1))
        assertEquals(rocket, merchantPushSafePreview(rocket + "tail", 2))
        assertEquals(rocket + "\uFFFD", merchantPushSafePreview(rocket + 0xD800.toChar() + "tail", 3))
        assertEquals("", merchantPushSafePreview("abc", -1))
    }

    @Test fun notificationPreviewNeutralizesBidiSpoofingControls() {
        val dangerous = listOf('\u200E', '\u200F', '\u202A', '\u202B', '\u202C', '\u202D', '\u202E', '\u2066', '\u2067', '\u2068', '\u2069')
        for (control in dangerous) {
            assertEquals("order\uFFFD123", merchantPushSafePreview("order" + control + "123", 80))
            assertEquals("order\uFFFD", merchantPushSafePreview("order" + control + "123", 6))
        }
    }

    @Test fun notificationPreviewNeutralizesDeprecatedDirectionalControls() {
        // U+206A..U+206F are deprecated directional controls, not visible order text.
        for (control in '\u206A'..'\u206F') {
            assertEquals("order\\uFFFD123", merchantPushSafePreview("order" + control + "123", 80))
            assertEquals("order\\uFFFD", merchantPushSafePreview("order" + control + "123", 6))
        }
        assertEquals("สั่งซื้อ", merchantPushSafePreview("สั่งซื้อ", 80))
    }

    @Test fun notificationPreviewNeutralizesArabicLetterMark() {
        // U+061C is a bidirectional formatting mark not covered by the U+200E/U+200F ranges.
        assertEquals("order\uFFFD123", merchantPushSafePreview("order\u061C123", 80))
        assertEquals("order\uFFFD", merchantPushSafePreview("order\u061C123", 6))
        assertEquals("สั่งซื้อ", merchantPushSafePreview("สั่งซื้อ", 80))
    }

    @Test fun notificationPreviewPreservesEmojiJoinerAndNormalUnicode() {
        val family = "👩\u200D👩\u200D👧"
        assertEquals(family, merchantPushSafePreview(family, 80))
        assertEquals("สั่งซื้อ " + family, merchantPushSafePreview("สั่งซื้อ " + family, 80))
    }

    @Test fun notificationPreviewPreservesBoundaryWhenNotSurrogatePair() {
        assertEquals("abcd", merchantPushSafePreview("abcdef", 4))
        assertEquals("abc", merchantPushSafePreview("abc", 3))
    }

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

    @Test fun malformedFcmMessageIdIsRejectedBeforeHashing() {
        assertEquals(false, merchantPushMetadataWellFormed("fcm" + 0xD800.toChar(), "new"))
        assertEquals(false, merchantPushMetadataWellFormed(0xDC00.toChar() + "fcm", "new"))
    }

    @Test fun malformedPushTypeIsRejectedBeforeNavigation() {
        assertEquals(false, merchantPushMetadataWellFormed("fcm-1", "order" + 0xDC00.toChar()))
        assertEquals(false, merchantPushMetadataWellFormed(null, 0xD800.toChar() + "order"))
    }

    @Test fun invisibleFcmMessageIdCannotBecomeNotificationIdentity() {
        assertEquals(false, merchantPushMetadataWellFormed("fcm\u00001", "new"))
        assertEquals(false, merchantPushMetadataWellFormed("fcm\u200B1", "new"))
        assertEquals(false, merchantPushMetadataWellFormed("fcm 1", "new"))
    }

    @Test fun invisibleTypeCannotBeForwardedToOrderNavigation() {
        assertEquals(false, merchantPushMetadataWellFormed("fcm-1", "order\tupdated"))
        assertEquals(false, merchantPushMetadataWellFormed("fcm-1", "order\u202Eupdated"))
        assertEquals(false, merchantPushMetadataWellFormed(null, "order\nupdated"))
        assertEquals(true, merchantPushMetadataWellFormed("fcm-1", "order_updated"))
    }

    @Test fun nonBreakingUnicodeSpaceSeparatorsCannotEnterPushRouting() {
        // Java Character.isWhitespace is false for NBSP, figure space and narrow NBSP.
        for (separator in listOf('\u00A0', '\u2007', '\u202F')) {
            assertEquals(false, Character.isWhitespace(separator))
            assertEquals(true, Character.isSpaceChar(separator))
            assertEquals(true, merchantPushHasInvisibleCodePoint("id" + separator))
            assertEquals(false, merchantPushMetadataWellFormed("fcm-" + separator, "updated"))
            assertEquals(false, merchantPushMetadataWellFormed("fcm-1", "type-" + separator))
            assertEquals(null, merchantPushReferenceId(mapOf("referenceId" to "order-" + separator)))
            assertEquals(null, merchantUsableFcmToken("abcdefghijklmnop" + separator))
        }
    }

    @Test fun supplementaryUnicodeFormatCharactersAreRejectedAcrossPushFields() {
        // U+E0001 LANGUAGE TAG is FORMAT but neither surrogate code unit is FORMAT.
        val invisibleTag = String(Character.toChars(0xE0001))
        assertEquals(true, merchantPushHasInvisibleCodePoint("id" + invisibleTag))
        assertEquals(false, merchantPushMetadataWellFormed("fcm-" + invisibleTag, "new"))
        assertEquals(false, merchantPushMetadataWellFormed("fcm-1", "type-" + invisibleTag))
        assertEquals(null, merchantPushReferenceId(mapOf("referenceId" to "order-" + invisibleTag)))
        assertEquals(null, merchantUsableFcmToken("abcdefghijklmnop" + invisibleTag))
    }

    @Test fun visibleSupplementaryUnicodeRemainsAllowedInPushFields() {
        val rocket = String(Character.toChars(0x1F680))
        assertEquals(false, merchantPushHasInvisibleCodePoint("id" + rocket))
        assertEquals(true, merchantPushMetadataWellFormed("fcm-" + rocket, "type-" + rocket))
        assertEquals("order-" + rocket, merchantPushReferenceId(mapOf("referenceId" to "order-" + rocket)))
        assertEquals("abcdefghijklmnop" + rocket, merchantUsableFcmToken("abcdefghijklmnop" + rocket))
    }

    @Test fun validSupplementaryMetadataRemainsSupported() {
        val emoji = 0xD83D.toChar().toString() + 0xDE80.toChar()
        assertEquals(true, merchantPushMetadataWellFormed("fcm-" + emoji, "type-" + emoji))
        assertEquals(true, merchantPushMetadataWellFormed(null, "new"))
    }

    @Test fun malformedUtf16FcmTokenIsRejectedBeforeRegistration() {
        val validToken = "abcdefghijklmnop"
        assertEquals(validToken, merchantUsableFcmToken(validToken))
        assertEquals(null, merchantUsableFcmToken(validToken + 0xD800.toChar()))
        assertEquals(null, merchantUsableFcmToken(0xDC00.toChar() + validToken))
        assertEquals(null, merchantUsableFcmToken("abc" + 0xD800.toChar() + "def" + 0xDC00.toChar() + "ghijklmnop"))
    }

    @Test fun firebaseTokenWhitespaceIsRejectedInsteadOfSilentlyTrimmed() {
        val token = "abcdefghijklmnop"
        assertEquals(token, merchantUsableFcmToken(token))
        assertEquals(null, merchantUsableFcmToken(" " + token))
        assertEquals(null, merchantUsableFcmToken(token + " "))
        assertEquals(null, merchantUsableFcmToken("\t" + token))
        assertEquals(null, merchantUsableFcmToken(token + "\n"))
    }

    @Test fun validSurrogatePairFcmTokenIsNotCorrupted() {
        val token = "abcdefghijklmnop" + 0xD83D.toChar() + 0xDE80.toChar()
        assertEquals(token, merchantUsableFcmToken(token))
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
