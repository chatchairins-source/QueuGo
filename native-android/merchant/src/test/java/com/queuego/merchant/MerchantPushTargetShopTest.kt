package com.queuego.merchant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantPushTargetShopTest {
    @Test fun legacyMessageWithoutAudienceRemainsCompatible() {
        assertTrue(merchantPushTargetsShop(mapOf("type" to "order"), "shop-a"))
    }

    @Test fun targetedMessageForCurrentShopIsAllowed() {
        assertTrue(merchantPushTargetsShop(mapOf("shopId" to "shop-a"), "shop-a"))
        assertTrue(merchantPushTargetsShop(mapOf("merchant_id" to "shop-a"), "shop-a"))
    }

    @Test fun messageForPreviousShopIsRejectedAfterAccountSwitch() {
        assertFalse(merchantPushTargetsShop(mapOf("shop_id" to "shop-a"), "shop-b"))
        assertFalse(merchantPushTargetsShop(mapOf("merchantId" to "shop-a"), "shop-b"))
    }

    @Test fun blankOrConflictingAudienceIsRejected() {
        assertFalse(merchantPushTargetsShop(mapOf("shopId" to ""), "shop-a"))
        assertFalse(merchantPushTargetsShop(mapOf("shopId" to "shop-a", "merchant_id" to "shop-b"), "shop-a"))
        assertFalse(merchantPushTargetsShop(emptyMap(), ""))
    }
}
