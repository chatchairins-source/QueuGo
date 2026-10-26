package com.queuego.customer

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerRouteRestorationTest {
    @Test fun detailRoutesNeverRestoreWithoutTheirServerBackedSelection() {
        for (route in listOf("order", "chat", "market-trip", "laundry-order")) {
            assertEquals("orders", customerRestorableRoute(route))
        }
        assertEquals("profile", customerRestorableRoute("support"))
        assertEquals("home", customerRestorableRoute("shop"))
        assertEquals("home", customerRestorableRoute("unknown"))
    }
    @Test fun reloadableRoutesAndDurableCheckoutRemainReachable() {
        for (route in listOf("category", "shopping", "cart", "checkout", "orders", "location", "profile", "notifications")) {
            assertEquals(route, customerRestorableRoute(route))
            assertEquals(route, customerRestorableRoute(customerRestorableRoute(route)))
        }
    }
}
