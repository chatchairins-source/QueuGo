package com.queuego.customer

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketCategoriesTest {
    @Test fun productionCategoryPrecedenceAndLabels() {
        assertEquals("seafood", marketCategoryKey("meat", "ปลา"))
        assertEquals("seasoning_herb", marketCategoryKey("", "ตะไคร้"))
        assertEquals("egg_tofu_fresh", marketCategoryKey("", "เต้าหู้"))
        assertEquals("processed_ready_to_cook", marketCategoryKey("ready-to-cook", ""))
        assertEquals("other_market", marketCategoryKey("", ""))
        assertEquals("ปลา / อาหารทะเล", marketCategories["seafood"])
        assertEquals(13, marketCategories.size)
    }
}
