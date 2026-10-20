package com.queuego.merchant

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.UUID

class MerchantMarketCatalogTest {
    private val actor = UUID.randomUUID().toString()
    private val shop = UUID.randomUUID().toString()
    private fun intent() = MarketCatalogIntent(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 100.0,
        "กิโลกรัม", 2.25, 2.25, 65.0, 4.5, 1.0, true)
    private fun row() = JSONObject().put("id", UUID.randomUUID().toString()).put("name_th", "กุ้งขาว")
        .put("category", "กุ้ง").put("source_unit", "1 กก.").put("suggested_unit", "กิโลกรัม")
        .put("suggested_pack_size", 1.0).put("catalog_preview_image", "https://images.mango-prod.siammakro.cloud/product.jpg")

    @Test fun replacedOrMissingShopCannotReplayOldShopRequests() {
        requireMerchantMarketCatalogShop(shop, shop)
        assertThrows(IllegalStateException::class.java) { requireMerchantMarketCatalogShop(UUID.randomUUID().toString(), shop) }
        assertThrows(IllegalStateException::class.java) { requireMerchantMarketCatalogShop(null, shop) }
        assertThrows(IllegalStateException::class.java) { requireMerchantMarketCatalogShop(JSONObject(), shop) }
    }
    @Test fun pageShowsOnly50AndUsesCurrentCatalogPreview() {
        val rows = JSONArray()
        repeat(51) { rows.put(row()) }
        val page = merchantMarketCatalogPage(rows)
        assertEquals(50, page.items.size)
        assertTrue(page.hasMore)
        assertEquals("https://images.mango-prod.siammakro.cloud/product.jpg", page.items.first().image)
    }
    @Test fun emptyAndExistingProductRowsAreTruthful() {
        assertFalse(merchantMarketCatalogPage(JSONArray()).hasMore)
        val product = UUID.randomUUID().toString()
        val page = merchantMarketCatalogPage(JSONArray().put(row().put("product_id", product).put("catalog_preview_image", JSONObject.NULL)))
        assertEquals(product, page.items.single().productId)
        assertNull(page.items.single().image)
    }
    @Test fun malformedIdentityOrCategoryIsNotShownAsValidProduct() {
        assertThrows(IllegalArgumentException::class.java) { merchantMarketCatalogPage(JSONArray().put(row().put("id", "bad"))) }
        assertThrows(IllegalArgumentException::class.java) { merchantMarketCatalogPage(JSONArray().put(row().put("category", "unknown"))) }
    }
    @Test fun decimalWeightsAndFractionalStockSurviveRpcRoundTrip() {
        val request = intent()
        val body = request.body()
        assertEquals(2.25, body.getDouble("p_weight_kg"), 0.0)
        assertEquals(4.5, body.getDouble("p_initial_stock"), 0.0)
        assertEquals(request, MarketCatalogIntent.fromBody(body))
        assertEquals(10, body.length())
    }
    @Test fun invalidValuesCannotReachTheMutation() {
        val request = intent()
        for (invalid in listOf(request.copy(price = 0.0), request.copy(price = Double.NaN), request.copy(weight = Double.POSITIVE_INFINITY),
            request.copy(pack = -1.0), request.copy(stock = -1.0), request.copy(cost = -1.0), request.copy(unit = "unknown"))) {
            assertThrows(IllegalArgumentException::class.java) { invalid.body() }
        }
    }
    @Test fun lostReplyKeepsTheSameDurableRequestAcrossRecreation() {
        val root = Files.createTempDirectory("market-catalog-unit").toFile()
        try {
            val first = MerchantMarketCatalogPendingStore(root, actor, shop)
            val request = first.prepare(intent())
            val recreated = MerchantMarketCatalogPendingStore(root, actor, shop)
            assertEquals(request, recreated.load())
            assertEquals(request.requestId, recreated.prepare(request.copy(requestId = UUID.randomUUID().toString())).requestId)
            assertEquals(1, root.listFiles()!!.count { it.extension == "json" })
        } finally { root.deleteRecursively() }
    }
    @Test fun changedBodyCannotReplaceAnUnconfirmedMutation() {
        val root = Files.createTempDirectory("market-catalog-unit").toFile()
        try {
            val store = MerchantMarketCatalogPendingStore(root, actor, shop)
            val request = store.prepare(intent())
            assertThrows(IllegalStateException::class.java) { store.prepare(request.copy(price = 200.0)) }
            assertEquals(request, store.load())
            assertThrows(IllegalStateException::class.java) { store.clear(UUID.randomUUID().toString()) }
            assertEquals(request, store.load())
            store.clear(request.requestId)
            assertNull(store.load())
        } finally { root.deleteRecursively() }
    }
    @Test fun actorAndShopIsolationPreventsCrossAccountReplay() {
        val root = Files.createTempDirectory("market-catalog-unit").toFile()
        try {
            MerchantMarketCatalogPendingStore(root, actor, shop).prepare(intent())
            assertNull(MerchantMarketCatalogPendingStore(root, UUID.randomUUID().toString(), shop).load())
            assertNull(MerchantMarketCatalogPendingStore(root, actor, UUID.randomUUID().toString()).load())
        } finally { root.deleteRecursively() }
    }
    @Test fun invalidStoredOwnershipAndOversizedPayloadFailClosed() {
        val root = Files.createTempDirectory("market-catalog-unit").toFile()
        try {
            val store = MerchantMarketCatalogPendingStore(root, actor, shop)
            store.prepare(intent())
            val file = root.listFiles()!!.single()
            val json = JSONObject(file.readText()).put("actor", UUID.randomUUID().toString())
            file.writeText(json.toString())
            assertThrows(IllegalStateException::class.java) { store.load() }
            file.writeText(" ".repeat(17000))
            assertThrows(IllegalStateException::class.java) { store.load() }
        } finally { root.deleteRecursively() }
    }
}
