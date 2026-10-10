package com.queuego.customer

import org.junit.Assert.*
import org.junit.Test

class CustomerLocationStoreTest {
    @Test fun savedGuestSelectionSurvivesSerializationAndWinsOverAccountProfile() {
        val guest = CustomerLocation(14.995, 103.102, "บ้านเลขที่ 12/3 จุดสังเกตหน้าร้าน")
        val reopened = decodeCustomerDeliveryLocation(encodeCustomerDeliveryLocation(guest))
        assertEquals(guest, reopened)
        assertEquals(guest, preferredCustomerDeliveryLocation(reopened, CustomerLocation(15.0, 103.0, "ที่อยู่เดิม")))
        assertEquals(guest, preferredCustomerDeliveryLocation(reopened, null))
    }

    @Test fun missingOrCorruptCacheDoesNotFabricateCoordinatesAndAllowsProfileFallback() {
        val profile = CustomerLocation(15.0, 103.0, "ที่อยู่บัญชี")
        for (raw in listOf(null, "", "{", "{}", "{\"lat\":15}", "{\"lat\":103,\"lng\":15}", "{\"lat\":0,\"lng\":0}")) {
            assertNull(decodeCustomerDeliveryLocation(raw))
            assertEquals(profile, preferredCustomerDeliveryLocation(decodeCustomerDeliveryLocation(raw), profile))
        }
        assertNull(preferredCustomerDeliveryLocation(null, CustomerLocation(Double.NaN, 103.0, "")))
        assertNull(preferredCustomerDeliveryLocation(null, null))
    }

    @Test fun invalidPointsCannotBePersisted() {
        for (point in listOf(CustomerLocation(Double.NaN, 103.0, ""), CustomerLocation(15.0, Double.POSITIVE_INFINITY, ""), CustomerLocation(0.0, 0.0, ""))) {
            assertThrows(IllegalArgumentException::class.java) { encodeCustomerDeliveryLocation(point) }
        }
    }
}
