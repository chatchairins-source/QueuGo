package com.queuego.customer

import com.queuego.shared.QgMapPoint
import org.junit.Assert.*
import org.junit.Test

class CustomerTrackingPolicyTest {
    private fun order(status: String = "in_progress") = CustomerOrder(
        id = "owned-order", number = "QT-1234", shopId = "owned-shop", status = status,
        subtotal = 0.0, deliveryFee = 0.0, total = 0.0, deliveryAddress = null, createdAt = null,
        pickupLatitude = 14.99, pickupLongitude = 103.10, deliveryLatitude = 15.01, deliveryLongitude = 103.12
    )
    private val rider = CustomerTrackingRider("Rider", null, null, null, null, 15.0, 103.11, null)

    @Test fun closedOrdersNeverExposeMapPointsEvenWithCachedRiderCoordinates() {
        listOf("completed", "cancelled", "no_rider_available", "COMPLETED").forEach {
            assertTrue(customerTrackingPoints(order(it), rider).isEmpty())
        }
    }
    @Test fun searchingOfferDoesNotExposeAnUnassignedRiderMarker() {
        assertEquals(2, customerTrackingPoints(order("searching_rider"), rider).size)
        assertEquals(3, customerTrackingPoints(order(), rider).size)
    }
    @Test fun noCoordinatesNeverBecomeZeroOrDefaultLocations() {
        val missing = order().copy(pickupLatitude = null, pickupLongitude = null, deliveryLatitude = null, deliveryLongitude = null)
        assertTrue(customerTrackingPoints(missing, null).isEmpty())
        assertEquals(1, customerTrackingPoints(missing, rider).size)
    }
    @Test fun swappedNonFiniteZeroAndOutsideThailandAreRejected() {
        listOf(103.1 to 15.0, 0.0 to 0.0, Double.NaN to 103.1, 15.0 to Double.POSITIVE_INFINITY,
            4.9 to 103.0, 15.0 to 106.1).forEach { (lat, lon) -> assertFalse(QgMapPoint(lat, lon, 0).valid) }
        assertTrue(QgMapPoint(5.0, 97.0, 0).valid)
        assertTrue(QgMapPoint(21.0, 106.0, 0).valid)
    }
    @Test fun oneMissingCoordinateDoesNotCreateAPartialMarker() {
        assertEquals(1, customerTrackingPoints(order().copy(pickupLatitude = null), null).size)
    }
    @Test fun terminalContextIsAttemptedOncePerDetailVisitEvenAfterRepeatedInvalidations() {
        val gate = TrackingContextRefreshGate()
        assertTrue(gate.shouldFetch(order()))
        assertTrue(gate.shouldFetch(order()))
        assertTrue(gate.shouldFetch(order("completed")))
        repeat(5) { assertFalse(gate.shouldFetch(order("completed"))) }
        assertTrue(TrackingContextRefreshGate().shouldFetch(order("completed")))
    }
    @Test fun allTerminalStatesStopContextRetries() {
        listOf("completed", "cancelled", "no_rider_available").forEach {
            val gate = TrackingContextRefreshGate()
            assertTrue(gate.shouldFetch(order(it)))
            assertFalse(gate.shouldFetch(order(it)))
        }
    }

    @Test fun lifecycleCancellationAllowsTheUnfinishedFinalSnapshotOnResume() {
        val gate = TrackingContextRefreshGate()
        val closed = order("completed")
        assertTrue(gate.shouldFetch(closed))
        gate.cancelled(closed)
        assertTrue(gate.shouldFetch(closed))
        assertFalse(gate.shouldFetch(closed))
    }

}
