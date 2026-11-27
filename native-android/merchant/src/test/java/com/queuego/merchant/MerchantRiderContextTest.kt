package com.queuego.merchant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantRiderContextTest {
    @Test fun emptyRpcMeansNoAssignedRider() {
        assertNull(merchantRiderContextFromRpc(JSONObject()))
    }

    @Test fun contextUsesOnlyServerProvidedIdentityAndContact() {
        val rider = merchantRiderContextFromRpc(
            JSONObject()
                .put("name", "QueueGo Rider")
                .put("phone", "0812345678")
                .put("photo", "data:image/jpeg;base64,abc")
                .put("vehicle_type", "motorcycle")
                .put("vehicle_plate", "1กข1234")
                .put("vehicle_verified", true)
        )!!
        assertEquals("QueueGo Rider", rider.name)
        assertEquals("0812345678", rider.phone)
        assertEquals("1กข1234", rider.vehiclePlate)
        assertTrue(rider.vehicleVerified)
    }

    @Test fun expiredContactWindowCanReturnIdentityWithoutPhone() {
        val rider = merchantRiderContextFromRpc(
            JSONObject()
                .put("name", "QueueGo Rider")
                .put("phone", JSONObject.NULL)
                .put("vehicle_verified", false)
        )!!
        assertNull(rider.phone)
    }
}
