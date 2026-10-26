package com.queuego.merchant

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantRouteRestorationTest {
    @Test fun editorsAndDetailsRestoreToReloadableParents() {
        assertEquals("orders", merchantRestorableRoute("order"))
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
}
