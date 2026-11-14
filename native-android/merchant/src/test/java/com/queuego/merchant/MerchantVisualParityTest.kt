package com.queuego.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantVisualParityTest {
    @Test fun promotionsUseSingleWebStyleTopBarTitle() {
        assertEquals("โปรโมชั่น", merchantTopBarTitle("promotions"))
    }

    @Test fun primaryTabsKeepQueueGoBrandTopBar() {
        assertNull(merchantTopBarTitle("home"))
        assertNull(merchantTopBarTitle("orders"))
        assertNull(merchantTopBarTitle("products"))
        assertNull(merchantTopBarTitle("profile"))
    }
}
