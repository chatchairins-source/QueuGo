package com.queuego.merchant

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantRouteRestorationTest {
    @Test fun editorsAndDetailsRestoreToReloadableParents() {
        assertEquals("orders", merchantRestorableRoute("order"))
        assertEquals("profile", merchantRestorableRoute("order-report"))
        for (route in listOf("product", "catalog", "grocery-catalog", "market-catalog")) {
            assertEquals("products", merchantRestorableRoute(route))
        }
        for (route in listOf("hours", "shop-setup")) assertEquals("profile", merchantRestorableRoute(route))
        assertEquals("home", merchantRestorableRoute("unknown"))
    }

    @Test fun mainDestinationsSurviveAndPolicyIsIdempotent() {
        for (route in listOf("home", "orders", "products", "pos", "revenue", "profile", "notifications")) {
            assertEquals(route, merchantRestorableRoute(route))
            assertEquals(route, merchantRestorableRoute(merchantRestorableRoute(route)))
        }
    }

    @Test fun everyMerchantDestinationHasAnExplicitBackDestination() {
        val expected = mapOf(
            "home" to "home", "orders" to "home", "products" to "home",
            "pos" to "home", "laundry" to "home", "revenue" to "home",
            "promotions" to "home", "notifications" to "home", "market-stock" to "home",
            "media" to "home", "modules" to "home", "support" to "profile",
            "profile" to "home", "shop-setup" to "profile", "hours" to "profile",
            "catalog" to "products", "grocery-catalog" to "products",
            "market-catalog" to "products", "product" to "products",
            "order" to "orders", "order-report" to "profile", "unknown-route" to "home"
        )
        expected.forEach { (route, parent) ->
            assertEquals("Back destination for $route", parent, merchantBackRoute(route))
        }
    }

    @Test fun productEditorReturnsToItsActualOriginAndInvalidOriginFallsBackSafely() {
        assertEquals("catalog", merchantBackRoute("product", "catalog"))
        assertEquals("market-catalog", merchantBackRoute("product", "market-catalog"))
        assertEquals("products", merchantBackRoute("product", "unexpected"))
    }
}
