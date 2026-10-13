package com.queuego.rider

import com.queuego.ridernavigationmap.NavigationStopKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderNavigationIntegrationTest {
    private fun job(
        id: String = "order-1",
        status: String = "rider_assigned",
        marketOrderId: String? = null
    ) = RiderJob(
        id = id,
        orderNumber = "1234",
        status = status,
        pickupAddress = "ร้าน",
        pickupLat = 14.99,
        pickupLng = 103.10,
        deliveryAddress = "ลูกค้า",
        deliveryLat = 15.00,
        deliveryLng = 103.11,
        deliveryFee = 30.0,
        marketOrderId = marketOrderId,
        arrivedShopAt = null,
        arrivedCustomerAt = null
    )

    @Test fun normalOrderKeepsStablePickupAndDropNumbers() {
        val beforePickup = riderNavigationStops(job(), emptyList(), emptyList())
        assertEquals(listOf(1, 2), beforePickup.map { it.sequence })
        assertEquals(listOf(NavigationStopKind.PICKUP, NavigationStopKind.DROPOFF), beforePickup.map { it.kind })
        assertFalse(beforePickup.first().completed)

        val delivering = riderNavigationStops(job(status = "in_progress"), emptyList(), emptyList())
        assertTrue(delivering.first().completed)
        assertEquals(2, delivering.last().sequence)
        assertFalse(delivering.last().completed)
    }

    @Test fun marketStopsPreservePickupOrderBeforeCustomer() {
        val pickups = listOf(
            MarketPickup("p1", "PICKED_UP", "ร้าน A", null, 10.0, 14.98, 103.08),
            MarketPickup("p2", "READY", "ร้าน B", null, 20.0, 14.99, 103.09)
        )
        val stops = riderNavigationStops(job(marketOrderId = "market-1"), pickups, emptyList())
        assertEquals(listOf(1, 2, 3), stops.map { it.sequence })
        assertTrue(stops[0].completed)
        assertFalse(stops[1].completed)
        assertEquals(NavigationStopKind.DROPOFF, stops[2].kind)
    }

    @Test fun unknownPendingPickupBlocksRoutingPastIt() {
        val broken = job().copy(pickupLat = null, pickupLng = null)
        val stops = riderNavigationStops(broken, emptyList(), emptyList())
        assertTrue(stops.isEmpty())
    }
}
