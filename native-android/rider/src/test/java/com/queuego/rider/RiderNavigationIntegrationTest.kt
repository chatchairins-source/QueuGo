package com.queuego.rider

import com.queuego.ridernavigationmap.NavigationStopKind
import com.queuego.ridernavigationmap.NavigationViewMode
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

    @Test fun dynamicBundleMapUsesAuthoritativeInterleavedRanks() {
        fun bundled(id: String, sequence: Int, pickupRank: Int, dropRank: Int) = RiderBundleOrder(
            bundleId = "bundle-1",
            job = job(id = id),
            bundleSequence = sequence,
            dropRank = sequence,
            shopId = "shop-$id",
            shopName = "ร้าน $id",
            originalDeliveryFee = 25.0,
            customerSavings = 5.0,
            riderExtraFee = 3.0,
            detourKm = 0.2,
            addedMinutes = 2,
            routeSequence = null,
            pickupStopRank = pickupRank,
            dropoffStopRank = dropRank
        )

        val orders = listOf(
            bundled("a", 1, pickupRank = 1, dropRank = 2),
            bundled("b", 2, pickupRank = 3, dropRank = 4)
        )
        val stops = riderNavigationStops(orders.first().job, emptyList(), orders)

        assertEquals(listOf(1, 2, 3, 4), stops.map { it.sequence })
        assertEquals(
            listOf(
                NavigationStopKind.PICKUP,
                NavigationStopKind.DROPOFF,
                NavigationStopKind.PICKUP,
                NavigationStopKind.DROPOFF
            ),
            stops.map { it.kind }
        )
        assertEquals(listOf("pickup:a", "drop:a", "pickup:b", "drop:b"), stops.map { it.id })
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

    @Test fun idleMapNeverAutoFollowsUntilAJobExists() {
        assertFalse(riderNavigationAutoFollow(false, NavigationViewMode.FOLLOW))
        assertFalse(riderNavigationAutoFollow(false, NavigationViewMode.FREE_PAN))
        assertFalse(riderNavigationAutoFollow(false, NavigationViewMode.OVERVIEW))
    }

    @Test fun activeJobStartsFollowCameraButRespectsManualPan() {
        assertTrue(riderNavigationAutoFollow(true, NavigationViewMode.FOLLOW))
        assertFalse(riderNavigationAutoFollow(true, NavigationViewMode.FREE_PAN))
        assertFalse(riderNavigationAutoFollow(true, NavigationViewMode.OVERVIEW))
    }

    @Test fun smoothnessRoadTestCanEnableIdleFollowWithoutChangingDefaultPolicy() {
        assertTrue(riderNavigationAutoFollow(false, NavigationViewMode.FOLLOW, smoothnessTestMode = true))
        assertFalse(riderNavigationAutoFollow(false, NavigationViewMode.FREE_PAN, smoothnessTestMode = true))
        assertFalse(riderNavigationAutoFollow(false, NavigationViewMode.OVERVIEW, smoothnessTestMode = true))
    }

    @Test fun travelBearingRejectsOppositeRawHeadingWhenMotionAgrees() {
        val resolved = riderResolveTravelBearing(
            priorBearing = 90f,
            rawBearing = 270f,
            motionBearing = 92f,
            speedMetersPerSecond = 10f
        )
        assertEquals(92f, resolved!!, 0.001f)
    }

    @Test fun travelBearingKeepsNearbyRawHeading() {
        val resolved = riderResolveTravelBearing(
            priorBearing = 90f,
            rawBearing = 95f,
            motionBearing = 92f,
            speedMetersPerSecond = 10f
        )
        assertEquals(95f, resolved!!, 0.001f)
    }

    @Test fun targetedOfferCountsAsNavigationWorkAndStartsFollowCamera() {
        val offeredStops = riderNavigationStops(
            job(status = "searching_rider"),
            emptyList(),
            emptyList()
        )
        val navigationActive = offeredStops.any { !it.completed && it.point.valid }
        assertTrue(navigationActive)
        assertTrue(riderNavigationAutoFollow(navigationActive, NavigationViewMode.FOLLOW))
    }
}
