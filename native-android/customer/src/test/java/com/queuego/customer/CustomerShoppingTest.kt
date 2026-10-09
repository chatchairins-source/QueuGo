package com.queuego.customer

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerShoppingTest {
    @Test
    fun normalizesPublicShoppingSubcategories() {
        assertEquals(
            setOf("mobile_accessories", "computer_it"),
            normalizeShopSubcategories(listOf(" mobile_accessories ", "COMPUTER_IT"))
        )
        assertEquals(
            setOf("automotive_car", "automotive_motorcycle"),
            normalizeShopSubcategories(listOf("automotive_car", "automotive_motorcycle", ""))
        )
    }

    @Test
    fun filtersShoppingModesLikeWebBlueprint() {
        val shop = CustomerShop(
            id = "shop-1",
            name = "ร้าน",
            category = "shopping",
            logo = null,
            cover = null,
            address = null,
            latitude = 14.99,
            longitude = 103.10,
            open = true,
            subcategories = setOf("mobile_accessories", "automotive_car")
        )
        assertTrue(shoppingShopMatches(shop, "all"))
        assertTrue(shoppingShopMatches(shop, "mobile_accessories"))
        assertTrue(shoppingShopMatches(shop, "automotive"))
        assertTrue(shoppingShopMatches(shop, "automotive_car"))
        assertFalse(shoppingShopMatches(shop, "automotive_motorcycle"))
        assertFalse(shoppingShopMatches(shop.copy(category = "grocery"), "all"))
    }

    @Test
    fun resolvesAutomotiveTitlesAndBannerKey() {
        assertEquals("อะไหล่รถยนต์", shoppingScreenSpec("automotive_car").title)
        assertEquals("automotive", shoppingScreenSpec("automotive_car").bannerKey)
        assertEquals("ช้อปปิ้ง", shoppingScreenSpec("all").title)
        assertEquals("shopping", shoppingScreenSpec("all").bannerKey)
    }
}
