package com.queuego.merchant

import com.queuego.shared.QueueGoHttpException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException

class MerchantPosTaxCompatibilityTest {
    private val base = "select=id,total_amount,payment_status,bill_status"
    private val scope = "&shop_id=eq.shop-a&sales_channel=eq.POS&payment_status=eq.UNPAID&status=neq.cancelled&order=created_at.desc"

    @Test fun migratedBackendKeepsRealTaxSnapshotAndDoesNotRetry() = runBlocking {
        val queries = mutableListOf<String>()
        val server = JSONArray().put(JSONObject().put("pos_tax_amount", 7).put("total_amount", 107))
        val result = readPosBillsWithTaxCompatibility(base, scope) {
            queries.add(it)
            server
        }
        assertSame(server, result)
        assertEquals(1, queries.size)
        assertTrue(queries.single().contains("pos_tax_amount"))
        assertTrue(queries.single().endsWith(scope))
    }

    @Test fun missingTaxColumnRetriesOnlyReadWithIdenticalScopeAndServerTotals() = runBlocking {
        for (message in listOf(
            "column orders.pos_tax_enabled_snapshot does not exist",
            "column orders.pos_prices_include_tax_snapshot does not exist",
            "Could not find the 'pos_tax_amount' column of 'orders' in the schema cache"
        )) {
            val queries = mutableListOf<String>()
            val server = JSONArray().put(JSONObject().put("total_amount", 103.25))
            val result = readPosBillsWithTaxCompatibility(base, scope) {
                queries.add(it)
                if (queries.size == 1) throw QueueGoHttpException(400, message)
                server
            }
            assertSame(server, result)
            assertEquals(2, queries.size)
            assertTrue(queries.first().endsWith(scope))
            assertEquals("orders?$base$scope", queries.last())
            assertFalse(result.getJSONObject(0).has("pos_tax_amount"))
        }
    }

    @Test fun authNetworkAndUnrelatedSchemaFailuresNeverFallBack() = runBlocking {
        for (error in listOf(
            QueueGoHttpException(403, "column orders.pos_tax_amount does not exist"),
            QueueGoHttpException(400, "column orders.staff_id does not exist"),
            QueueGoHttpException(400, "permission denied for pos_tax_amount"),
            QueueGoHttpException(500, "column orders.pos_tax_amount does not exist"),
            SocketTimeoutException("timeout")
        )) {
            var calls = 0
            try {
                readPosBillsWithTaxCompatibility(base, scope) { calls++; throw error }
                fail("Expected original failure")
            } catch (actual: Exception) {
                assertSame(error, actual)
                assertEquals(1, calls)
            }
        }
    }

    @Test fun failedCompatibilityReadIsNotRepeatedOrReplacedByEmptySuccess() = runBlocking {
        var calls = 0
        val denied = QueueGoHttpException(403, "permission denied")
        try {
            readPosBillsWithTaxCompatibility(base, scope) {
                calls++
                if (calls == 1) throw QueueGoHttpException(400, "column orders.pos_tax_amount does not exist")
                throw denied
            }
            fail("Expected original read failure")
        } catch (actual: Exception) {
            assertSame(denied, actual)
            assertEquals(2, calls)
        }
    }
}
