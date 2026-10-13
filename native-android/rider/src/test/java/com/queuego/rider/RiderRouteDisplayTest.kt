package com.queuego.rider

import org.junit.Assert.*
import org.junit.Test

class RiderRouteDisplayTest {
    private fun job(id: String, state: String = "ready") = RiderJob(id, null, state,
        null, 15.0, 103.0, null, 15.1, 103.1, null, null, null, null, null)
    private fun bundled(id: String, sequence: Int, drop: Int, state: String = "ready") =
        RiderBundleOrder("bundle", job(id, state), sequence, drop, null, "", 0.0, 0.0, 0.0, 0.0, 0, null)

    @Test fun pendingBundleShopsComeBeforeEveryCustomer() {
        val orders = listOf(bundled("b", 2, 1, "in_progress"), bundled("a", 1, 2))
        assertEquals(listOf("pickup:a", "drop:b", "drop:a"),
            riderRouteDisplayStops(orders.first().job, emptyList(), orders).map { it.id })
    }
    @Test fun pickupAndDropRanksAreIndependent() {
        val orders = listOf(bundled("b", 2, 1), bundled("a", 1, 2))
        assertEquals(listOf("pickup:a", "pickup:b", "drop:b", "drop:a"),
            riderRouteDisplayStops(orders.first().job, emptyList(), orders).map { it.id })
    }
    @Test fun arrivalDoesNotSkipPendingPickupProof() {
        val arrived = job("a").copy(arrivedShopAt = "2026-10-13")
        assertEquals("pickup:a", riderRouteDisplayStops(arrived, emptyList(), emptyList()).first().id)
        assertEquals(listOf("drop:a"), riderRouteDisplayStops(arrived.copy(status = "in_progress"),
            emptyList(), emptyList()).map { it.id })
    }
    @Test fun marketDoneAndCancelledPickupsDisappear() {
        val pickups = listOf(MarketPickup("a", "PICKED_UP", "", null, 0.0, 15.0, 103.0),
            MarketPickup("b", "READY", "", null, 0.0, 15.01, 103.01),
            MarketPickup("c", "CANCELLED", "", null, 0.0, 15.02, 103.02))
        assertEquals(listOf("pickup:b", "drop:m"), riderRouteDisplayStops(
            job("m").copy(marketOrderId = "market"), pickups, emptyList()).map { it.id })
    }
    @Test fun missingPendingShopNeverMakesCustomerTheFirstTarget() {
        assertTrue(riderRouteDisplayStops(job("a").copy(pickupLat = null),
            emptyList(), emptyList()).isEmpty())
    }
    @Test fun closedOrdersDoNotLeaveRoadLines() {
        for (state in listOf("completed", "cancelled", "no_rider_available"))
            assertTrue(riderRouteDisplayStops(job("a", state), emptyList(), emptyList()).isEmpty())
    }
    @Test fun onlyGpsToFirstStopGetsDarkStyle() {
        val stops = listOf(RiderRouteStop("one", RiderRoutePoint(15.0, 103.0)),
            RiderRouteStop("two", RiderRoutePoint(15.1, 103.1)))
        assertEquals(listOf(true, false), riderDisplayLegs(RiderRoutePoint(14.9, 103.0), stops).map { it.first })
        assertEquals(listOf(false), riderDisplayLegs(null, stops).map { it.first })
    }
    @Test fun serviceGeometryPreservesSeparatedRoadParts() {
        val raw = """{"meta":{"status":200},"features":[
            {"geometry":{"type":"LineString","coordinates":[[103,15],[103.01,15.01]]}},
            {"geometry":{"type":"MultiLineString","coordinates":[[[103.02,15.02],[103.03,15.03]],[[103.04,15.04],[103.05,15.05]]]}}]}"""
        val paths = riderRoadGeometry(raw)
        assertEquals(3, paths.size)
        assertEquals(RiderRoutePoint(15.0, 103.0), paths.first().first())
        assertEquals(2, paths.first().size)
    }
    @Test fun unavailableOrMalformedRoutesNeverInventStraightLines() {
        for (raw in listOf("""{"meta":{"status":404},"features":[]}""",
            """{"features":[]}""",
            """{"features":[{"geometry":{"type":"LineString","coordinates":[[103,15],[103,95],[103,16]]}}]}""")) {
            try { riderRoadGeometry(raw); fail("Invalid road path accepted") } catch (_: Exception) { }
        }
    }
}
