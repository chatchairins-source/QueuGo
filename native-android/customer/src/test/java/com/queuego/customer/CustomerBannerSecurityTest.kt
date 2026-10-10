package com.queuego.customer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomerBannerSecurityTest {
    @Test
    fun bannerImageAllowsOnlyHttpsOrInlineImageData() {
        assertEquals("https://cdn.example.test/banner.webp", sanitizeBannerImage("  https://cdn.example.test/banner.webp  "))
        assertEquals("data:image/webp;base64,AAAA", sanitizeBannerImage("data:image/webp;base64,AAAA"))
        assertNull(sanitizeBannerImage("http://cdn.example.test/banner.webp"))
        assertNull(sanitizeBannerImage("javascript:alert(1)"))
        assertNull(sanitizeBannerImage("ftp://cdn.example.test/banner.webp"))
        assertNull(sanitizeBannerImage("   "))
    }

    @Test
    fun bannerLinkAllowsOnlyInternalHashOrHttps() {
        assertEquals("#market", sanitizeBannerLink(" #market "))
        assertEquals("https://queuego.example.test/promo", sanitizeBannerLink("https://queuego.example.test/promo"))
        assertNull(sanitizeBannerLink("http://queuego.example.test/promo"))
        assertNull(sanitizeBannerLink("javascript:alert(1)"))
        assertNull(sanitizeBannerLink("intent://unsafe"))
        assertNull(sanitizeBannerLink(null))
    }
}
