package com.queuego.rider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test

class RiderLaundryModelTest {
    private fun job(
        status: String,
        fromLat: Double? = 14.99,
        fromLng: Double? = 103.10,
        toLat: Double? = 15.00,
        toLng: Double? = 103.11
    ) = RiderLaundryJob(
        jobId = "job-1",
        laundryOrderId = "laundry-1",
        orderNumber = "1234",
        leg = "pickup",
        jobStatus = status,
        orderStatus = "pickup_assigned",
        hubId = "hub-1",
        hubName = "ศูนย์ฝากซัก",
        shopName = "ร้านซัก",
        serviceName = "ซักอบ",
        actualQuantity = null,
        pricingType = "kg",
        jobFee = 25.0,
        fromAddress = "ลูกค้า",
        fromLatitude = fromLat,
        fromLongitude = fromLng,
        toAddress = "ร้าน",
        toLatitude = toLat,
        toLongitude = toLng,
        customerAmount = 120.0
    )

    @Test
    fun assignedLaundryNavigatesToPickup() {
        assertEquals(14.99 to 103.10, job("assigned").navigationTarget)
    }

    @Test
    fun collectedLaundryNavigatesToDestination() {
        assertEquals(15.00 to 103.11, job("collected").navigationTarget)
    }

    @Test
    fun missingCurrentCoordinatesDoNotInventNavigation() {
        assertNull(job("assigned", fromLat = null, fromLng = null).navigationTarget)
    }

    @Test
    fun laundryUsesCanonicalVisibleOrderPrefix() {
        assertEquals("QT-1234", job("assigned").numberLabel)
    }

    @Test
    fun laundryRpcStatePreservesFeatureModeInvitesAndOwnerVisibleJobs() {
        val state = riderLaundryStateFromRpc(JSONObject(
            """{"feature_enabled":true,"mode_enabled":true,"invites":[{"invite_id":"invite-1","hub_id":"hub-1","hub_name":"ศูนย์","shop_name":"ร้านซัก","status":"pending"}],"active_jobs":[{"job_id":"job-1","laundry_order_id":"laundry-1","job_status":"collected","leg":"pickup","from_address":"บ้าน","from_latitude":14.9,"from_longitude":103.1,"to_address":"ร้าน","to_latitude":15.0,"to_longitude":103.2,"job_fee":25.0,"actual_kg":2.5}],"pool":[{"job_id":"job-2","laundry_order_id":"laundry-2"}]}"""
        ))

        assertTrue(state.featureEnabled)
        assertTrue(state.modeEnabled)
        assertEquals("invite-1", state.invites.single().inviteId)
        assertEquals("ศูนย์", state.invites.single().hubName)
        assertEquals("job-1", state.activeJob?.jobId)
        assertEquals("collected", state.activeJob?.jobStatus)
        assertEquals(2.5, state.activeJob?.actualKg!!, 0.0)
        assertEquals(15.0 to 103.2, state.activeJob?.navigationTarget)
        assertEquals("waiting", state.pool.single().jobStatus)
    }

    @Test
    fun malformedLaundryRpcStateFailsClosedWithoutInventingJobs() {
        val state = riderLaundryStateFromRpc(JSONObject("""{"feature_enabled":false,"mode_enabled":false,"invites":[{}],"active_jobs":[{}],"pool":[{}]}"""))

        assertTrue(!state.featureEnabled)
        assertTrue(!state.modeEnabled)
        assertTrue(state.invites.isEmpty())
        assertNull(state.activeJob)
        assertTrue(state.pool.isEmpty())
    }
}
