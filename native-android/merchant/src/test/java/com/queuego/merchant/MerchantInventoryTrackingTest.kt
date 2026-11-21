package com.queuego.merchant

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MerchantInventoryTrackingTest {
    @Test fun adjustmentRequestMatchesManagerRpcContract() {
        val request = MerchantInventoryAdjustmentIntent(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            -2, "damage", "สินค้าเสียหายจากการตรวจรับ")
        val body = request.body()
        assertEquals(request.productId, body.getString("p_product_id"))
        assertEquals(-2, body.getInt("p_delta"))
        assertEquals("damage", body.getString("p_reason"))
        assertEquals(request.requestKey, body.getString("p_request_key"))
    }
    @Test fun invalidAdjustmentCannotReachRpc() {
        val product = UUID.randomUUID().toString()
        for (request in listOf(
            MerchantInventoryAdjustmentIntent("bad", product, 1, "receiving", "รับเข้าคลัง"),
            MerchantInventoryAdjustmentIntent(UUID.randomUUID().toString(), product, 0, "receiving", "รับเข้าคลัง"),
            MerchantInventoryAdjustmentIntent(UUID.randomUUID().toString(), product, 1, "unknown", "รับเข้าคลัง"),
            MerchantInventoryAdjustmentIntent(UUID.randomUUID().toString(), product, 1, "receiving", "no")
        )) assertThrows(IllegalArgumentException::class.java) { request.body() }
    }
    @Test fun trackedStockCannotBeChangedOutsideAdjustmentRpc() {
        val product = MerchantProduct("p", "สินค้า", null, 10.0, 11.0, null, true, stock = 4,
            inventoryStockTracking = true)
        requireMerchantStockEditAllowed(product, 4)
        assertThrows(IllegalArgumentException::class.java) { requireMerchantStockEditAllowed(product, 5) }
        requireMerchantStockEditAllowed(product.copy(inventoryStockTracking = false), 5)
    }
    @Test fun posStockGateMatchesWebTrackedProductRules() {
        val genericTracked = PosProduct("g", "สินค้า", 10.0, null, true, stock = 0,
            inventoryStockTracking = true)
        assertTrue(posProductInventoryTracked(genericTracked))
        assertTrue(posProductSoldOut(genericTracked))

        val groceryTracked = PosProduct("m", "ของสด", 10.0, null, true, stock = 0,
            groceryMasterId = "master", groceryStockTracking = true)
        assertTrue(posProductInventoryTracked(groceryTracked))
        assertTrue(posProductSoldOut(groceryTracked))

        assertFalse(posProductInventoryTracked(groceryTracked.copy(groceryMasterId = null)))
        assertFalse(posProductSoldOut(PosProduct("u", "ไม่ติดตาม", 10.0, null, true, stock = 0)))
        assertFalse(posProductSoldOut(genericTracked.copy(stock = 3)))
    }

    @Test fun receiptAndHistoryPreserveAuthoritativeMovement() {
        val receipt = merchantInventoryAdjustmentReceipt(JSONObject().put("id", "movement")
            .put("stock_before", 4).put("stock_after", 2).put("replayed", true))
        assertEquals(2, receipt.stockAfter)
        assertTrue(receipt.replayed)
        val page = merchantInventoryAdjustmentPage(JSONObject().put("has_more", true)
            .put("entries", JSONArray().put(JSONObject().put("id", "movement").put("event_type", "manual_adjustment")
                .put("delta", -2).put("stock_before", 4).put("stock_after", 2).put("reason", "damage")
                .put("note", "สินค้าเสียหาย").put("created_at", "2026-10-26T10:00:00Z").put("actor_name", "เจ้าของร้าน"))))
        assertTrue(page.hasMore)
        assertEquals(2, page.entries.single().stockAfter)
        assertEquals("เจ้าของร้าน", page.entries.single().actorName)
    }
}
