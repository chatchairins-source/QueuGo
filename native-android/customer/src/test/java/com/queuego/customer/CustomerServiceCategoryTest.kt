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
    @Test fun blankOrUnsupportedImageUsesTheWebsBundledPhoto() {
        fun banner(image: String?) = ServiceBanner("food", image, null, null, true)
        assertNull(webBannerImage(null))
        listOf(null, "", "  ", "javascript:alert(1)", "file:///image.webp").forEach { assertNull(webBannerImage(banner(it))) }
        assertEquals("https://example.com/image.webp", webBannerImage(banner(" https://example.com/image.webp ")))
        assertEquals("data:image/webp;base64,AAAA", webBannerImage(banner("data:image/webp;base64,AAAA")))
    }
}
