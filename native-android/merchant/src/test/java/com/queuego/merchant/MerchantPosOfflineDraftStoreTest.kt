package com.queuego.merchant

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
                listOf(PosProduct(productId, "ชาไทย", 45.0, null, true, category = "เครื่องดื่ม")),
                listOf(PosTable(UUID.randomUUID().toString(), "A1", true)))
            val store = MerchantPosOfflineDraftStore(root)
            store.saveCatalog(catalog)
            assertEquals(catalog, MerchantPosOfflineDraftStore(root).loadCatalog(actor, device))
            assertNull(store.loadCatalog(UUID.randomUUID().toString(), device))
            assertNull(store.loadCatalog(actor, UUID.randomUUID().toString()))
            assertEquals(store.deviceId(), MerchantPosOfflineDraftStore(root).deviceId())
        } finally { root.deleteRecursively() }
    }
}
