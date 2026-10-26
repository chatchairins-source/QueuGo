package com.queuego.customer

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerBackNavigationTest {
    private fun target(
        screen: String,
        shop: String = "home",
        cart: String = "home",
        orders: String = "home",
        location: String = "home",
        promotion: String = "home",
        supportHasOrder: Boolean = false,
        hasOrder: Boolean = false,
    ) = customerSystemBackTarget(
        screen = screen,
        shopReturnScreen = shop,
        cartReturnScreen = cart,
        ordersReturnScreen = orders,
        locationReturnScreen = location,
        promotionReturnScreen = promotion,
        supportHasOrder = supportHasOrder,
        hasSelectedOrder = hasOrder,
    )

    @Test fun nestedScreensReturnToTheirWebParent() {
        assertEquals("profile", target("favorites"))
        assertEquals("market", target("market-cart"))
        assertEquals("cart", target("checkout"))
        assertEquals("search", target("shop", shop = "search"))
        assertEquals("shop", target("cart", cart = "shop"))
        assertEquals("profile", target("location", location = "profile"))
        assertEquals("profile", target("promotion", promotion = "profile"))
        assertEquals("profile", target("orders", orders = "profile"))
        assertEquals("orders", target("order"))
        assertEquals("orders", target("market-trip"))
        assertEquals("orders", target("laundry-order"))
        assertEquals("order", target("chat", hasOrder = true))
        assertEquals("order", target("support", supportHasOrder = true, hasOrder = true))
    }

    @Test fun globalScreensReturnHomeAndGeneralSupportReturnsProfile() {
        assertEquals("home", target("search"))
        assertEquals("home", target("notifications"))
        assertEquals("home", target("orders"))
        assertEquals("home", target("profile"))
        assertEquals("profile", target("support"))
        assertEquals(null, target("home"))
    }
}
