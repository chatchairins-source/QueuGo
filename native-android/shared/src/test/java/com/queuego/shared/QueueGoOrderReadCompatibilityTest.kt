package com.queuego.shared

import org.junit.Assert.*
import org.junit.Test

class QueueGoOrderReadCompatibilityTest {
    private class Http(val code: Int, message: String) : Exception(message)
    private val status: (Throwable) -> Int? = { (it as? Http)?.code }

    @Test fun productionSchemaLoadsAndRemembersOnlyConfirmedMissingFields() {
        val reader = QueueGoOrderReadCompatibility()
        val path = "/rest/v1/orders?select=id,ride_distance_km,ride_duration_minutes,rider_delivery_payout,next_after_order_id,delivery_fee,total_amount&or=(order_type.eq.shopping,and(order_type.eq.service,fulfillment_vertical.eq.ride))&rider_id=eq.owner&status=in.(ready,in_progress)&order=next_after_order_id.asc.nullsfirst,created_at.asc&limit=5"
        val missing = listOf("ride_distance_km", "ride_duration_minutes", "rider_delivery_payout", "next_after_order_id")
        val calls = mutableListOf<String>()
        val result = reader.read(path, status) { attempted ->
            calls.add(attempted)
            val column = missing.firstOrNull { attempted.substringAfter("select=").substringBefore('&').split(',').contains(it) }
            if (column != null) throw Http(400, "column orders.$column does not exist")
            "live response"
        }
        assertEquals("live response", result)
        assertEquals(5, calls.size)
        val compatible = calls.last()
        assertTrue(compatible.contains("rider_id=eq.owner&status=in.(ready,in_progress)"))
        assertTrue(compatible.contains("&or=(order_type.eq.shopping,and(order_type.eq.service,fulfillment_vertical.eq.ride))"))
        assertTrue(compatible.contains("delivery_fee,total_amount"))
        assertTrue(compatible.contains("&order=created_at.asc&limit=5"))
        assertEquals(compatible, reader.read(path, status) { it })
    }

    @Test fun deployedFeatureFieldsAndPayoutRemainAuthoritative() {
        val reader = QueueGoOrderReadCompatibility()
        val path = "orders?select=id,ride_distance_km,rider_delivery_payout,delivery_fee&rider_id=eq.owner"
        assertEquals(path, reader.read(path, status) { it })
        var attempts = 0
        val adapted = reader.read(path, status) {
            if (attempts++ == 0) throw Http(400, "column orders.ride_distance_km does not exist")
            it
        }
        assertTrue(adapted.contains("rider_delivery_payout,delivery_fee"))
    }

    @Test fun authNetworkCoreFieldsRpcAndNonOrderReadsNeverFallback() {
        for ((code, message) in listOf(401 to "expired", 403 to "denied", 500 to "column orders.ride_distance_km does not exist", 400 to "column orders.total_amount does not exist")) {
            var calls = 0
            try {
                QueueGoOrderReadCompatibility().read("orders?select=total_amount,ride_distance_km", status) { calls++; throw Http(code, message) }
                fail("must propagate")
            } catch (_: Http) { assertEquals(1, calls) }
        }
        for (path in listOf("/rest/v1/rpc/action", "rider_profiles?select=id,ride_distance_km")) {
            var calls = 0
            try {
                QueueGoOrderReadCompatibility().read(path, status) { calls++; throw Http(400, "column orders.ride_distance_km does not exist") }
            } catch (_: Http) { assertEquals(1, calls) }
        }
    }

    @Test fun sharedMerchantReadPreservesArrivalAndReadyRevisionWhenDeployed() {
        val reader = QueueGoOrderReadCompatibility()
        var calls = 0
        val result = reader.read("orders?select=id,rider_arrived_customer_at,merchant_ready_estimate_at,merchant_ready_revision&id=in.(owned)", status) {
            if (calls++ == 0) throw Http(400, "column orders.merchant_ready_estimate_at does not exist")
            if (calls == 2) throw Http(400, "column orders.merchant_ready_revision does not exist")
            it
        }
        assertEquals("orders?select=id,rider_arrived_customer_at&id=in.(owned)", result)
    }

    @Test fun riderJobsStillLoadWhenExpressMigrationIsNotDeployed() {
        val reader = QueueGoOrderReadCompatibility()
        val fields = listOf(
            "express_distance_km", "express_duration_minutes", "express_item_type", "express_weight_kg",
            "express_size", "express_vehicle_type", "express_sender_name", "express_sender_phone",
            "express_receiver_name", "express_receiver_phone", "express_instructions"
        )
        val path = "orders?select=id,order_number,status,fulfillment_vertical," + fields.joinToString(",") +
            ",delivery_fee,total_amount&rider_id=eq.owned&status=in.(ready,in_progress)&order=created_at.asc"
        var calls = 0
        val compatible = reader.read(path, status) { attempted ->
            calls++
            val selected = attempted.substringAfter("select=").substringBefore('&').split(',')
            val missing = fields.firstOrNull { it in selected }
            if (missing != null) throw Http(400, "column orders.$missing does not exist")
            attempted
        }
        assertEquals(fields.size + 1, calls)
        assertTrue(compatible.contains("select=id,order_number,status,fulfillment_vertical,delivery_fee,total_amount"))
        assertTrue(compatible.contains("rider_id=eq.owned&status=in.(ready,in_progress)&order=created_at.asc"))
        assertEquals(compatible, reader.read(path, status) { it })
    }

    @Test fun anotherReadDiscoveringTheSameColumnDoesNotBreakRecovery() {
        val reader = QueueGoOrderReadCompatibility()
        val path = "orders?select=id,ride_distance_km&rider_id=eq.owner"
        var outerCalls = 0
        val result = reader.read(path, status) { attempted ->
            if (outerCalls++ == 0) {
                var innerCalls = 0
                reader.read(path, status) {
                    if (innerCalls++ == 0) throw Http(400, "column orders.ride_distance_km does not exist")
                    it
                }
                throw Http(400, "column orders.ride_distance_km does not exist")
            }
            attempted
        }
        assertEquals("orders?select=id&rider_id=eq.owner", result)
        assertEquals(2, outerCalls)
    }

}
