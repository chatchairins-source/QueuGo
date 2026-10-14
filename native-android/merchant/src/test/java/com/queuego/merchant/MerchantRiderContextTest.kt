package com.queuego.merchant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantRiderContextTest {
    @Test fun emptyBackendContextDoesNotCreatePlaceholderRider() {
        assertNull(merchantRiderContextFromJson(JSONObject()))
        assertNull(merchantRiderContextFromJson(null))
    }

    @Test fun parsesOnlyTheMinimalMerchantRiderIdentityContract() {
        val rider = merchantRiderContextFromJson(
            JSONObject()
                .put("name", "สมชาย")
                .put("phone", "0812345678")
                .put("photo", "data:image/jpeg;base64,abc")
                .put("vehicle_type", "motorcycle")
                .put("vehicle_plate", "1กข1234")
                .put("vehicle_verified", true)
        )!!

        assertEquals("สมชาย", rider.name)
        assertEquals("0812345678", rider.phone)
        assertEquals("1กข1234", rider.vehiclePlate)
        assertTrue(rider.vehicleVerified)
    }

    @Test fun expiredContactWindowKeepsIdentityButHidesPhone() {
        val rider = merchantRiderContextFromJson(
            JSONObject()
                .put("name", "Rider A")
                .put("phone", JSONObject.NULL)
                .put("vehicle_type", "motorcycle")
                .put("vehicle_verified", false)
        )!!

        assertNull(rider.phone)
        assertFalse(rider.vehicleVerified)
    }
}
