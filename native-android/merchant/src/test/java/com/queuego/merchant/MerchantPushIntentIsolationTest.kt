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
}
