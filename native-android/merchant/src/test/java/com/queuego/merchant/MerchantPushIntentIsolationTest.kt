package com.queuego.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MerchantPushIntentIsolationTest {
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

    @Test fun validOpaqueReferenceStillRoutes() {
        assertEquals("order-7_A", merchantPushReferenceId(mapOf("referenceId" to "order-7_A")))
    }

    @Test fun absentReferencePreservesNonOrderPush() {
        assertEquals("", merchantPushReferenceId(emptyMap()))
    }

}
