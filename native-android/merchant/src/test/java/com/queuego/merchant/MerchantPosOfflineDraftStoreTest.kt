package com.queuego.merchant

import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class MerchantPosOfflineDraftStoreTest {
    private fun draft(actor: String, shop: String, device: String) = PosOfflineDraft(
        UUID.randomUUID().toString(), UUID.randomUUID().toString(), actor, shop, device,
        "TAKEAWAY", null, 123L,
        listOf(PosOfflineDraftItem(UUID.randomUUID().toString(), "ชาไทย", 45.0, 2))
    )

    @Test fun persistsAcrossStoreInstancesAndScopesByActorShopAndDevice() {
        val root = Files.createTempDirectory("pos-offline-draft-test").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val scope = PosOfflineDraftScope(actor, shop, device)
            val original = draft(actor, shop, device)
            MerchantPosOfflineDraftStore(root).save(original)
            assertEquals(original, MerchantPosOfflineDraftStore(root).load(scope))
            assertNull(MerchantPosOfflineDraftStore(root).loadForActor(UUID.randomUUID().toString(), device))
            assertEquals(original, MerchantPosOfflineDraftStore(root).loadForActor(actor, device))
            assertNull(MerchantPosOfflineDraftStore(root).load(scope.copy(shopId = UUID.randomUUID().toString())))
            assertNull(MerchantPosOfflineDraftStore(root).load(scope.copy(deviceId = UUID.randomUUID().toString())))
        } finally { root.deleteRecursively() }
    }

    @Test fun stableRequestIdsSurviveReloadAndOnlyMatchingDraftCanBeRemoved() {
        val root = Files.createTempDirectory("pos-offline-draft-test").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val scope = PosOfflineDraftScope(actor, shop, device)
            val original = draft(actor, shop, device).let { d ->
                d.copy(items = d.items.map { it.copy(requestIds = List(it.quantity) { UUID.randomUUID().toString() }) })
            }
            val store = MerchantPosOfflineDraftStore(root)
            store.save(original)
            assertEquals(original, MerchantPosOfflineDraftStore(root).load(scope))
            assertThrows(IllegalStateException::class.java) { store.remove(scope, UUID.randomUUID().toString()) }
            assertEquals(original, store.load(scope))
            store.remove(scope, original.id)
            assertNull(store.load(scope))
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsDuplicateRequestIdsAndCorruptStoredDraftsWithoutOverwriting() {
        val root = Files.createTempDirectory("pos-offline-draft-test").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val request = UUID.randomUUID().toString()
            val bad = draft(actor, shop, device).let { d ->
                d.copy(items = d.items.map { it.copy(requestIds = listOf(request, request)) })
            }
            assertThrows(IllegalArgumentException::class.java) { MerchantPosOfflineDraftStore(root).save(bad) }
            val store = MerchantPosOfflineDraftStore(root)
            store.save(draft(actor, shop, device))
            root.listFiles()!!.first { it.name.endsWith(".json") }.writeText("broken")
            assertThrows(Exception::class.java) {
                store.save(draft(actor, shop, device))
            }
            assertEquals("broken", root.listFiles()!!.first { it.name.endsWith(".json") }.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun cachedCatalogPersistsForSameActorAndDeviceOnly() {
        val root = Files.createTempDirectory("pos-offline-catalog-test").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val productId = UUID.randomUUID().toString()
            val catalog = PosOfflineCatalog(actor, shop, device, "ร้านทดสอบ", 456L,
                listOf(PosProduct(productId, "ชาไทย", 45.0, null, true)),
                listOf(PosTable(UUID.randomUUID().toString(), "A1", true)))
            val store = MerchantPosOfflineDraftStore(root)
            store.saveCatalog(catalog)
            assertEquals(catalog, MerchantPosOfflineDraftStore(root).loadCatalog(actor, device))
            assertNull(store.loadCatalog(UUID.randomUUID().toString(), device))
            assertNull(store.loadCatalog(actor, UUID.randomUUID().toString()))
            assertEquals(store.deviceId(), MerchantPosOfflineDraftStore(root).deviceId())
        } finally { root.deleteRecursively() }
    }

    @Test fun offlineCatalogPreservesStockAndOptionGroupsForSafeReroute() {
        val root = Files.createTempDirectory("pos-offline-catalog-options").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val master = UUID.randomUUID().toString()
            val groups = parsePosProductOptionGroups(JSONArray().put(
                JSONObject().put("schema", "queuego.menu-options.v1").put("key", "size").put("name", "ขนาด")
                    .put("type", "single").put("required", true).put("options", JSONArray()
                        .put(JSONObject().put("key", "normal").put("name", "ธรรมด").put("price_delta", 0)))))
            val product = PosProduct(UUID.randomUUID().toString(), "เมนู", 35.0, null, true,
                stock = 0, inventoryStockTracking = true, groceryMasterId = master,
                groceryStockTracking = true, optionGroups = groups)
            val store = MerchantPosOfflineDraftStore(root)
            store.saveCatalog(PosOfflineCatalog(actor, shop, device, "ร้าน", 1L, listOf(product), emptyList()))
            val restored = store.loadCatalog(actor, device)!!.products.single()
            assertEquals(0, restored.stock)
            assertTrue(restored.inventoryStockTracking)
            assertEquals(master, restored.groceryMasterId)
            assertTrue(restored.groceryStockTracking)
            assertEquals(groups, restored.optionGroups)
        } finally { root.deleteRecursively() }
    }

    @Test fun selectedOptionsPricesAndRetryIdsSurviveDraftReload() {
        val root = Files.createTempDirectory("pos-offline-options-roundtrip").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val product = UUID.randomUUID().toString()
            val original = draft(actor, shop, device).copy(items = listOf(
                PosOfflineDraftItem(product, "ชาไทย", 65.0, 2,
                    requestIds = List(2) { UUID.randomUUID().toString() },
                    basePriceSnapshot = 45.0, optionsJson = "{\"size\":\"large\"}", optionLabel = "ขนาด: ใหญ่"),
                PosOfflineDraftItem(product, "ชาไทย", 45.0, 1,
                    requestIds = listOf(UUID.randomUUID().toString()),
                    basePriceSnapshot = 45.0, optionsJson = "{\"size\":\"normal\"}", optionLabel = "ขนาด: ปกติ")
            ))
            val store = MerchantPosOfflineDraftStore(root)
            store.save(original)
            val restored = MerchantPosOfflineDraftStore(root).load(PosOfflineDraftScope(actor, shop, device))
            assertEquals(original, restored)
            assertEquals(2, restored!!.items.size)
            assertNotEquals(restored.items[0].optionsJson, restored.items[1].optionsJson)
            val json = JSONObject(root.listFiles()!!.single { it.name.endsWith(".json") }.readText())
            assertEquals("large", json.getJSONArray("items").getJSONObject(0).getJSONObject("options").getString("size"))
        } finally { root.deleteRecursively() }
    }

    @Test fun olderPlainDraftsKeepTheirPriceAndEmptyOptions() {
        val root = Files.createTempDirectory("pos-offline-legacy-draft").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val original = draft(actor, shop, device)
            val store = MerchantPosOfflineDraftStore(root)
            store.save(original)
            val file = root.listFiles()!!.single { it.name.endsWith(".json") }
            val json = JSONObject(file.readText())
            val item = json.getJSONArray("items").getJSONObject(0)
            item.remove("base_price")
            item.remove("options")
            item.remove("option_label")
            file.writeText(json.toString())
            assertEquals(original, store.load(PosOfflineDraftScope(actor, shop, device)))
        } finally { root.deleteRecursively() }
    }

}
