package com.queuego.customer

import org.junit.Assert.*
import org.junit.Test

class CustomerServiceCategoryTest {
    @Test fun threeCategoriesHaveTheOriginalDistinctContentAndPhotoResources() {
        val food = webServiceCategory("food")!!
        val cafe = webServiceCategory("cafe")!!
        val grocery = webServiceCategory("grocery")!!
        assertEquals("ร้านอาหาร", food.heading)
        assertEquals("อาหาร", food.bannerTitle)
        assertEquals("ร้านเครื่องดื่มใกล้คุณ", cafe.sectionTitle)
        assertEquals("ซื้อของใกล้บ้าน เงินหมุนเวียนในชุมชน", grocery.bannerSubtitle)
        assertEquals(3, setOf(food.drawable, cafe.drawable, grocery.drawable).size)
    }
    @Test fun categoryRowsMatchProductionNearestShopOrderingWhenLocationExists() {
        fun shop(id: String, category: String, lat: Double?, lng: Double?) = CustomerShop(
            id = id,
            name = id,
            category = category,
            logo = null,
            cover = null,
            address = null,
            latitude = lat,
            longitude = lng,
            open = true
        )
        val location = CustomerLocation(14.99, 103.10, "Buriram")
        val far = shop("far", "food", 15.20, 103.30)
        val unrelated = shop("cafe", "cafe", 14.991, 103.101)
        val unknown = shop("unknown", "food", null, null)
        val near = shop("near", "food", 14.995, 103.105)

        assertEquals(
            listOf("near", "far", "unknown"),
            serviceCategoryRows("food", listOf(far, unrelated, unknown, near), location).map { it.id }
        )
    }

    @Test fun categoryRowsPreserveBackendOrderWithoutLocation() {
        fun shop(id: String) = CustomerShop(
            id, id, "grocery", null, null, null, null, null, true
        )
        assertEquals(
            listOf("b", "a"),
            serviceCategoryRows("grocery", listOf(shop("b"), shop("a")), null).map { it.id }
        )
    }

    @Test fun blankOrUnsupportedImageUsesTheWebsBundledPhoto() {
        fun banner(image: String?) = ServiceBanner("food", image, null, null, true)
        assertNull(webBannerImage(null))
        listOf(null, "", "  ", "javascript:alert(1)", "file:///image.webp").forEach { assertNull(webBannerImage(banner(it))) }
        assertEquals("https://example.com/image.webp", webBannerImage(banner(" https://example.com/image.webp ")))
        assertEquals("data:image/webp;base64,AAAA", webBannerImage(banner("data:image/webp;base64,AAAA")))
    }
}
