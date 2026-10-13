package com.queuego.rider

import org.junit.Assert.*
import org.junit.Test

class RiderDeliveryFlowTest {
    private fun job(status: String) = RiderJob(
        id = "stage-test", orderNumber = null, status = status,
        pickupAddress = null, pickupLat = null, pickupLng = null,
        deliveryAddress = null, deliveryLat = null, deliveryLng = null,
        deliveryFee = null, marketOrderId = null, arrivedShopAt = null,
        arrivedCustomerAt = null
    )

    @Test fun automaticMapsAndReturnUiShareOneKeyPerStage() {
        for (status in listOf("rider_assigned", "assigned", "preparing", "ready")) {
            assertEquals("stage-test:shop", riderNavigationKey(job(status)))
        }
        for (status in listOf("picked_up", "in_progress")) {
            assertEquals("stage-test:customer", riderNavigationKey(job(status)))
        }
        assertNotEquals(riderNavigationKey(job("ready")), riderNavigationKey(job("in_progress")))
    }

    @Test fun marketNextShopDoesNotReusePreviousShopNavigation() {
        val first = job("ready").copy(marketOrderId = "key-test", pickupLat = 15.0, pickupLng = 103.0)
        val next = first.copy(pickupLat = 15.1)
        assertNotEquals(riderNavigationKey(first), riderNavigationKey(next))
        assertEquals(riderNavigationKey(first.copy(status = "in_progress")),
            riderNavigationKey(next.copy(status = "in_progress")))
    }

    @Test fun collectionRequiresReadableProofAndAuthoritativeAmount() {
        assertFalse(riderCollectionReady(120.5, false, false))
        assertFalse(riderCollectionReady(120.5, true, false))
        assertFalse(riderCollectionReady(null, true, true))
        assertFalse(riderCollectionReady(Double.NaN, true, true))
        assertFalse(riderCollectionReady(Double.POSITIVE_INFINITY, true, true))
        assertFalse(riderCollectionReady(-1.0, true, true))
        assertTrue(riderCollectionReady(120.5, true, true))
        assertTrue(riderCollectionReady(0.0, true, true))
    }
}
