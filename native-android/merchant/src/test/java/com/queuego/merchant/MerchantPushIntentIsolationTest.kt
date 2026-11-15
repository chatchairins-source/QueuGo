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
        assertEquals("fcm-1", merchantPushMessageKey("fcm-1", "order-1", "new", "fallback"))
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

}
