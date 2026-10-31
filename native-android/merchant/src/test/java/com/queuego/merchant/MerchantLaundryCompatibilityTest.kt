package com.queuego.merchant

import com.queuego.shared.QueueGoHttpException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException

class MerchantLaundryCompatibilityTest {
    private val scope = "&hub_id=eq.owner-hub&order=created_at.desc&limit=100"

    @Test fun migratedBackendReturnsUnchangedServerRowsWithoutRetry() = runBlocking {
        val paths = mutableListOf<String>()
        val server = JSONArray().put(JSONObject().put("gp_rate_snapshot", 0.05).put("final_total_amount", 240))
        val result = readLaundryOrdersWithSchemaCompatibility(scope) { paths.add(it); server }
        assertSame(server, result)
        assertEquals(1, paths.size)
        assertTrue(paths.single().contains("gp_rate_snapshot"))
        assertTrue(paths.single().endsWith(scope))
    }

    @Test fun eachAbsentOptionalColumnAllowsOneScopedReadWithoutInventingAmounts() = runBlocking {
        for (column in listOf("pickup_staff_name", "return_staff_name", "gp_rate_snapshot", "platform_fee_amount", "shop_net_amount")) {
            for (message in listOf("column laundry_orders.$column does not exist",
                "Could not find the '$column' column of 'laundry_orders' in the schema cache")) {
                val paths = mutableListOf<String>()
                val server = JSONArray().put(JSONObject().put("id", "order-a").put("final_total_amount", 240.25))
                val result = readLaundryOrdersWithSchemaCompatibility(scope) {
                    paths.add(it)
                    if (paths.size == 1) throw QueueGoHttpException(400, message)
                    server
                }
                assertSame(server, result)
                assertEquals(2, paths.size)
                assertTrue(paths.all { it.endsWith(scope) })
                assertTrue(paths.last().contains("final_total_amount"))
                assertFalse(paths.last().contains("gp_rate_snapshot"))
                assertFalse(result.getJSONObject(0).has("platform_fee_amount"))
                assertNull(merchantLaundryGpRate(result.getJSONObject(0)))
            }
        }
    }

    @Test fun permissionNetworkAndUnrelatedSchemaFailuresPropagate() = runBlocking {
        for (error in listOf(
            QueueGoHttpException(401, "column laundry_orders.gp_rate_snapshot does not exist"),
            QueueGoHttpException(403, "column laundry_orders.gp_rate_snapshot does not exist"),
            QueueGoHttpException(500, "column laundry_orders.gp_rate_snapshot does not exist"),
            QueueGoHttpException(400, "column laundry_orders.customer_id does not exist"),
            QueueGoHttpException(400, "permission denied for gp_rate_snapshot"),
            SocketTimeoutException("timeout")
        )) {
            var calls = 0
            try {
                readLaundryOrdersWithSchemaCompatibility(scope) { calls++; throw error }
                fail("Expected original failure")
            } catch (actual: Exception) {
                assertSame(error, actual)
                assertEquals(1, calls)
            }
        }
    }

    @Test fun failedCompatibilityRetryNeverReturnsEmptySuccessOrLoops() = runBlocking {
        var calls = 0
        val denied = QueueGoHttpException(403, "permission denied")
        try {
            readLaundryOrdersWithSchemaCompatibility(scope) {
                calls++
                if (calls == 1) throw QueueGoHttpException(400, "column laundry_orders.gp_rate_snapshot does not exist")
                throw denied
            }
            fail("Expected scoped read failure")
        } catch (actual: Exception) {
            assertSame(denied, actual)
            assertEquals(2, calls)
        }
    }

    @Test fun absentNullOrInvalidGpNeverBecomesTenPercentOrZero() {
        assertNull(merchantLaundryGpRate(JSONObject()))
        assertNull(merchantLaundryGpRate(JSONObject().put("gp_rate_snapshot", JSONObject.NULL)))
        for (value in listOf(-0.01, 1.01, "NaN", "Infinity", "-Infinity", "unknown")) {
            assertNull(merchantLaundryGpRate(JSONObject().put("gp_rate_snapshot", value)))
        }
    }

    @Test fun zeroAndConfirmedGpSnapshotsRemainValid() {
        for (value in listOf(0.0, 0.05, 0.1, 1.0)) {
            assertEquals(value, merchantLaundryGpRate(JSONObject().put("gp_rate_snapshot", value))!!, 0.0)
        }
    }
}
