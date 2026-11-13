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


    @Test fun rejectsDuplicateReplayIdsAcrossOfflinePosLinesAndBillCreation() {
        val root = Files.createTempDirectory("pos-offline-replay-uniqueness").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val replay = UUID.randomUUID().toString()
            val first = PosOfflineDraftItem(UUID.randomUUID().toString(), "กาแฟ", 40.0, 1, listOf(replay))
            val second = PosOfflineDraftItem(UUID.randomUUID().toString(), "ชา", 35.0, 1, listOf(replay))
            val original = draft(actor, shop, device).copy(items = listOf(first, second))
            val store = MerchantPosOfflineDraftStore(root)
            assertThrows(IllegalArgumentException::class.java) { store.save(original) }
            assertThrows(IllegalArgumentException::class.java) {
                store.save(original.copy(items = listOf(first.copy(requestIds = listOf(original.requestId)),
                    second.copy(requestIds = listOf(UUID.randomUUID().toString())))))
            }
            assertNull(store.loadForActor(actor, device))

            val safe = original.copy(items = listOf(first,
                second.copy(requestIds = listOf(UUID.randomUUID().toString()))))
            store.save(safe)
            val file = root.listFiles()!!.single { it.name.endsWith(".json") }
            val stored = JSONObject(file.readText())
            stored.getJSONArray("items").getJSONObject(1).getJSONArray("request_ids").put(0, replay)
            file.writeText(stored.toString())
            assertThrows(IllegalArgumentException::class.java) { store.loadForActor(actor, device) }
        } finally { root.deleteRecursively() }
    }

    @Test fun refusesToOverwritePersistedDraftWithDuplicateReplayIds() {
        val root = Files.createTempDirectory("pos-offline-replay-recovery").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val firstReplay = UUID.randomUUID().toString()
            val original = draft(actor, shop, device).copy(items = listOf(
                PosOfflineDraftItem(UUID.randomUUID().toString(), "กาแฟ", 40.0, 1, listOf(firstReplay)),
                PosOfflineDraftItem(UUID.randomUUID().toString(), "ชา", 35.0, 1,
                    listOf(UUID.randomUUID().toString()))
            ))
            val store = MerchantPosOfflineDraftStore(root)
            store.save(original)
            val file = root.listFiles()!!.single { it.name.endsWith(".json") }
            val corrupted = JSONObject(file.readText())
            corrupted.getJSONArray("items").getJSONObject(1)
                .put("request_ids", JSONArray().put(firstReplay))
            val corruptedBytes = corrupted.toString()
            file.writeText(corruptedBytes)

            val replacement = original.copy(savedAtMs = original.savedAtMs + 1)
            assertThrows(IllegalArgumentException::class.java) { store.save(replacement) }
            assertEquals(corruptedBytes, file.readText())
            assertThrows(IllegalArgumentException::class.java) {
                MerchantPosOfflineDraftStore(root).loadForActor(actor, device)
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun equalTimestampCannotReplaceDifferentOfflineCart() {
        val root = Files.createTempDirectory("pos-offline-equal-timestamp").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val store = MerchantPosOfflineDraftStore(root)
            val original = draft(actor, shop, device).copy(savedAtMs = 100L)
            store.save(original)
            val file = root.listFiles()!!.single { it.name.endsWith(".json") }
            val originalBytes = file.readText()
            val conflicting = original.copy(items = original.items.map { it.copy(quantity = 3) })
            assertThrows(IllegalStateException::class.java) { store.save(conflicting) }
            assertEquals(originalBytes, file.readText())
            assertEquals(original, store.loadForActor(actor, device))
            store.save(original)
            assertEquals(original, store.loadForActor(actor, device))
        } finally { root.deleteRecursively() }
    }

    @Test fun staleOfflineDraftUpdateCannotReplaceNewerSavedCart() {
        val root = Files.createTempDirectory("pos-offline-stale-draft").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val store = MerchantPosOfflineDraftStore(root)
            val original = draft(actor, shop, device).copy(savedAtMs = 100L)
            store.save(original)
            val newer = original.copy(savedAtMs = 200L,
                items = original.items.map { it.copy(quantity = 3) })
            store.save(newer)
            val persisted = root.listFiles()!!.single { it.name.endsWith(".json") }.readText()
            assertThrows(IllegalStateException::class.java) {
                store.save(original.copy(savedAtMs = 150L))
            }
            assertEquals(persisted, root.listFiles()!!.single { it.name.endsWith(".json") }.readText())
            assertEquals(newer, MerchantPosOfflineDraftStore(root).loadForActor(actor, device))
            store.save(newer.copy(savedAtMs = 200L))
        } finally { root.deleteRecursively() }
    }

    @Test fun refusesToOverwriteAnotherShopDraftEvenWithMatchingReplayIdentity() {
        val root = Files.createTempDirectory("pos-offline-cross-shop").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val original = draft(actor, UUID.randomUUID().toString(), device)
            val store = MerchantPosOfflineDraftStore(root)
            store.save(original)
            val file = root.listFiles()!!.single { it.name.endsWith(".json") }
            val originalBytes = file.readText()

            val differentShop = original.copy(shopId = UUID.randomUUID().toString())
            assertThrows(IllegalStateException::class.java) { store.save(differentShop) }
            assertEquals(originalBytes, file.readText())
            assertEquals(original, store.loadForActor(actor, device))
        } finally { root.deleteRecursively() }
    }

    @Test fun refusesToOverwriteOversizedPersistedDraftWithoutTruncatingIt() {
        val root = Files.createTempDirectory("pos-offline-oversized").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val original = draft(actor, UUID.randomUUID().toString(), device)
            val store = MerchantPosOfflineDraftStore(root)
            store.save(original)
            val file = root.listFiles()!!.single { it.name.endsWith(".json") }
            val oversizedBytes = "x".repeat(256 * 1024 + 1)
            file.writeText(oversizedBytes)

            assertThrows(IllegalStateException::class.java) {
                store.save(original.copy(savedAtMs = original.savedAtMs + 1))
            }
            assertEquals(oversizedBytes, file.readText())
            assertThrows(IllegalStateException::class.java) { store.loadForActor(actor, device) }
        } finally { root.deleteRecursively() }
    }

    @Test fun refusesToOverwriteOfflineCatalogFromAnotherShop() {
        val root = Files.createTempDirectory("pos-offline-catalog-cross-shop").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val original = PosOfflineCatalog(actor, UUID.randomUUID().toString(), device,
                "ร้านแรก", 123L, emptyList(), emptyList())
            val store = MerchantPosOfflineDraftStore(root)
            store.saveCatalog(original)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val originalBytes = file.readText()

            val differentShop = original.copy(shopId = UUID.randomUUID().toString(), shopName = "ร้านอื่น")
            assertThrows(IllegalStateException::class.java) { store.saveCatalog(differentShop) }
            assertEquals(originalBytes, file.readText())
            assertEquals(original, store.loadCatalog(actor, device))
        } finally { root.deleteRecursively() }
    }

    @Test fun refusesToOverwriteTamperedOfflineCatalogIdentity() {
        val root = Files.createTempDirectory("pos-offline-catalog-tampered").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val original = PosOfflineCatalog(actor, UUID.randomUUID().toString(), device,
                "ร้านแรก", 123L, emptyList(), emptyList())
            val store = MerchantPosOfflineDraftStore(root)
            store.saveCatalog(original)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val tampered = JSONObject(file.readText())
                .put("shop_id", UUID.randomUUID().toString()).toString()
            file.writeText(tampered)

            assertThrows(IllegalStateException::class.java) { store.saveCatalog(original.copy(savedAtMs = 124L)) }
            assertEquals(tampered, file.readText())
        } finally { root.deleteRecursively() }
    }


    @Test fun rejectsTamperedOfflineCatalogWithExcessiveItemCountsOrInvalidShopId() {
        val root = Files.createTempDirectory("pos-offline-catalog-bounds").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val original = PosOfflineCatalog(actor, UUID.randomUUID().toString(), device,
                "ร้าน", 123L, emptyList(), emptyList())
            val store = MerchantPosOfflineDraftStore(root)
            store.saveCatalog(original)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val valid = JSONObject(file.readText())

            file.writeText(JSONObject(valid.toString()).put("shop_id", "not-a-uuid").toString())
            assertThrows(IllegalArgumentException::class.java) { store.loadCatalog(actor, device) }

            val excessiveProducts = JSONArray()
            repeat(20_001) { excessiveProducts.put(JSONObject()) }
            file.writeText(JSONObject(valid.toString()).put("products", excessiveProducts).toString())
            assertThrows(IllegalStateException::class.java) { store.loadCatalog(actor, device) }

            val excessiveTables = JSONArray()
            repeat(301) { excessiveTables.put(JSONObject()) }
            file.writeText(JSONObject(valid.toString()).put("tables", excessiveTables).toString())
            assertThrows(IllegalStateException::class.java) { store.loadCatalog(actor, device) }
        } finally { root.deleteRecursively() }
    }
    @Test fun refusesToOverwriteCatalogWithOversizedPersistedCollections() {
        val root = Files.createTempDirectory("pos-catalog-corrupt-count").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val catalog = PosOfflineCatalog(actor, shop, device, "ร้าน", 1L,
                listOf(PosProduct(UUID.randomUUID().toString(), "ชา", 30.0, null, true)),
                emptyList())
            val store = MerchantPosOfflineDraftStore(root)
            store.saveCatalog(catalog)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val tampered = JSONObject(file.readText())
            tampered.put("tables", JSONArray().apply {
                repeat(301) { put(JSONObject().put("id", UUID.randomUUID().toString())) }
            })
            val tamperedText = tampered.toString()
            file.writeText(tamperedText)
            assertThrows(IllegalStateException::class.java) {
                store.saveCatalog(catalog.copy(savedAtMs = 2L))
            }
            assertEquals(tamperedText, file.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun refusesOversizedOrMalformedPersistedDeviceIdWithoutReplacingIt() {
        val root = Files.createTempDirectory("pos-device-id-corrupt").toFile()
        try {
            val store = MerchantPosOfflineDraftStore(root)
            val deviceFile = java.io.File(root, "device-id")
            val oversized = "x".repeat(64 * 1024)
            deviceFile.writeText(oversized)
            assertThrows(IllegalStateException::class.java) { store.deviceId() }
            assertEquals(oversized, deviceFile.readText())

            deviceFile.writeText("not-a-uuid")
            assertThrows(IllegalArgumentException::class.java) { store.deviceId() }
            assertEquals("not-a-uuid", deviceFile.readText())

            val valid = UUID.randomUUID().toString()
            deviceFile.writeText(valid)
            assertEquals(valid, MerchantPosOfflineDraftStore(root).deviceId())
        } finally { root.deleteRecursively() }
    }


    @Test fun staleOfflineCatalogCannotOverwriteNewerSnapshot() {
        val root = Files.createTempDirectory("pos-offline-stale-catalog").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val store = MerchantPosOfflineDraftStore(root)
            val current = PosOfflineCatalog(actor, shop, device, "ร้านล่าสุด", 200L,
                emptyList(), emptyList())
            store.saveCatalog(current)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val savedBytes = file.readText()
            assertThrows(IllegalStateException::class.java) {
                store.saveCatalog(current.copy(shopName = "ข้อมูลเก่า", savedAtMs = 100L))
            }
            assertEquals(savedBytes, file.readText())
            assertEquals(current, store.loadCatalog(actor, device))
            store.saveCatalog(current.copy(shopName = "ข้อมูลใหม่", savedAtMs = 201L))
            assertEquals(201L, store.loadCatalog(actor, device)!!.savedAtMs)
        } finally { root.deleteRecursively() }
    }


    @Test fun rejectsNegativeOfflineSnapshotTimestampsWithoutChangingPersistedData() {
        val root = Files.createTempDirectory("pos-negative-snapshot-time").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val store = MerchantPosOfflineDraftStore(root)
            val validDraft = draft(actor, shop, device).copy(savedAtMs = 100L)
            store.save(validDraft)
            val draftFile = root.listFiles()!!.single { it.name.endsWith(".json") }
            val draftBytes = draftFile.readText()
            assertThrows(IllegalArgumentException::class.java) {
                store.save(validDraft.copy(savedAtMs = -1L))
            }
            assertEquals(draftBytes, draftFile.readText())

            val validCatalog = PosOfflineCatalog(actor, shop, device, "ร้าน", 100L,
                emptyList(), emptyList())
            store.saveCatalog(validCatalog)
            val catalogFile = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val catalogBytes = catalogFile.readText()
            assertThrows(IllegalArgumentException::class.java) {
                store.saveCatalog(validCatalog.copy(savedAtMs = -1L))
            }
            assertEquals(catalogBytes, catalogFile.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsNegativeTimestampInPersistedCatalogOnLoadAndOverwrite() {
        val root = Files.createTempDirectory("pos-catalog-negative-persisted-time").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val store = MerchantPosOfflineDraftStore(root)
            val catalog = PosOfflineCatalog(actor, shop, device, "ร้าน", 100L,
                emptyList(), emptyList())
            store.saveCatalog(catalog)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val corrupted = JSONObject(file.readText()).put("saved_at_ms", -1L).toString()
            file.writeText(corrupted)

            assertThrows(IllegalStateException::class.java) { store.loadCatalog(actor, device) }
            assertThrows(IllegalStateException::class.java) {
                store.saveCatalog(catalog.copy(savedAtMs = 101L))
            }
            assertEquals(corrupted, file.readText())
        } finally { root.deleteRecursively() }
    }


    @Test fun sameTimestampCatalogRefreshMustBeIdempotent() {
        val root = Files.createTempDirectory("pos-catalog-same-time").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val store = MerchantPosOfflineDraftStore(root)
            val original = PosOfflineCatalog(actor, shop, device, "ร้านเดิม", 500L,
                emptyList(), emptyList())
            store.saveCatalog(original)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val originalBytes = file.readText()

            // An identical replay is safe and does not change the snapshot.
            store.saveCatalog(original)
            assertEquals(originalBytes, file.readText())

            // A different payload at the same timestamp must not overwrite it.
            assertThrows(IllegalStateException::class.java) {
                store.saveCatalog(original.copy(shopName = "ข้อมูลที่ขัดแย้ง"))
            }
            assertEquals(originalBytes, file.readText())
            assertEquals(original, store.loadCatalog(actor, device))

            // A genuinely newer refresh remains permitted.
            val newer = original.copy(shopName = "ข้อมูลใหม่", savedAtMs = 501L)
            store.saveCatalog(newer)
            assertEquals(newer, store.loadCatalog(actor, device))
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsOversizedPersistedDraftArraysBeforeDecodeWithoutReplacingFile() {
        val root = Files.createTempDirectory("pos-bounded-decode").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val store = MerchantPosOfflineDraftStore(root)
            val original = draft(actor, shop, device)
            store.save(original)
            val file = root.listFiles()!!.single { it.name.endsWith(".json") }
            val valid = JSONObject(file.readText())

            val tooManyItems = JSONObject(valid.toString())
            val item = valid.getJSONArray("items").getJSONObject(0)
            tooManyItems.put("items", JSONArray().apply { repeat(101) { put(JSONObject(item.toString())) } })
            val itemBytes = tooManyItems.toString()
            file.writeText(itemBytes)
            assertThrows(IllegalStateException::class.java) {
                store.load(PosOfflineDraftScope(actor, shop, device))
            }
            assertEquals(itemBytes, file.readText())

            val tooManyRequests = JSONObject(valid.toString())
            val requestIds = JSONArray().apply { repeat(100) { put(UUID.randomUUID().toString()) } }
            tooManyRequests.getJSONArray("items").getJSONObject(0).put("request_ids", requestIds)
            val requestBytes = tooManyRequests.toString()
            file.writeText(requestBytes)
            assertThrows(IllegalStateException::class.java) { store.loadForActor(actor, device) }
            assertEquals(requestBytes, file.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsNegativeOfflineCatalogPricesOnSaveAndLoadWithoutReplacingSnapshot() {
        val root = Files.createTempDirectory("pos-catalog-invalid-price").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val product = PosProduct(UUID.randomUUID().toString(), "ชา", 35.0, null, true)
            val catalog = PosOfflineCatalog(actor, shop, device, "ร้าน", 100L,
                listOf(product), emptyList())
            val store = MerchantPosOfflineDraftStore(root)
            store.saveCatalog(catalog)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val originalBytes = file.readText()

            assertThrows(IllegalArgumentException::class.java) {
                store.saveCatalog(catalog.copy(savedAtMs = 101L,
                    products = listOf(product.copy(price = -1.0))))
            }
            assertEquals(originalBytes, file.readText())

            val corrupted = JSONObject(originalBytes)
            corrupted.getJSONArray("products").getJSONObject(0).put("price", -5.0)
            val corruptedBytes = corrupted.toString()
            file.writeText(corruptedBytes)
            assertThrows(IllegalStateException::class.java) { store.loadCatalog(actor, device) }
            assertEquals(corruptedBytes, file.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsNegativeOfflineCatalogStockOnSaveAndLoadWithoutReplacingSnapshot() {
        val root = Files.createTempDirectory("pos-catalog-negative-stock").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val product = PosProduct(UUID.randomUUID().toString(), "ชา", 35.0, null, true)
            val catalog = PosOfflineCatalog(actor, shop, device, "ร้าน", 100L,
                listOf(product), emptyList())
            val store = MerchantPosOfflineDraftStore(root)
            store.saveCatalog(catalog)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val originalBytes = file.readText()

            assertThrows(IllegalArgumentException::class.java) {
                store.saveCatalog(catalog.copy(savedAtMs = 101L,
                    products = listOf(product.copy(stock = -1))))
            }
            assertEquals(originalBytes, file.readText())

            val corrupted = JSONObject(originalBytes)
            corrupted.getJSONArray("products").getJSONObject(0).put("stock", -2)
            val corruptedBytes = corrupted.toString()
            file.writeText(corruptedBytes)
            assertThrows(IllegalStateException::class.java) { store.loadCatalog(actor, device) }
            assertEquals(corruptedBytes, file.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsMalformedOfflineCatalogStockInsteadOfSilentlyCoercingToZero() {
        val root = Files.createTempDirectory("pos-catalog-malformed-stock").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val product = PosProduct(UUID.randomUUID().toString(), "ชา", 35.0, null, true)
            val store = MerchantPosOfflineDraftStore(root)
            store.saveCatalog(PosOfflineCatalog(actor, shop, device, "ร้าน", 100L,
                listOf(product), emptyList()))
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val original = file.readText()

            // Invalid types and fractional quantities must not become zero/truncated stock.
            for (invalid in listOf<Any>("not-a-number", "5", 1.5, 2147483648L)) {
                val corrupted = JSONObject(original)
                corrupted.getJSONArray("products").getJSONObject(0).put("stock", invalid)
                val bytes = corrupted.toString()
                file.writeText(bytes)
                assertThrows(IllegalStateException::class.java) { store.loadCatalog(actor, device) }
                assertEquals(bytes, file.readText())
            }

            // Older snapshots without stock retain the legacy zero-stock default.
            val legacy = JSONObject(original)
            legacy.getJSONArray("products").getJSONObject(0).remove("stock")
            file.writeText(legacy.toString())
            assertEquals(0, store.loadCatalog(actor, device)!!.products.single().stock)
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsMalformedPersistedDraftQuantitiesWithoutChangingBytes() {
        val root = Files.createTempDirectory("pos-draft-quantity-types").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val store = MerchantPosOfflineDraftStore(root)
            val original = draft(actor, shop, device)
            store.save(original)
            val file = root.listFiles()!!.single { it.name.endsWith(".json") }
            val valid = file.readText()

            for (invalid in listOf<Any>("2", "invalid", 2.5, 2147483648L)) {
                val tampered = JSONObject(valid)
                tampered.getJSONArray("items").getJSONObject(0).put("quantity", invalid)
                val bytes = tampered.toString()
                file.writeText(bytes)
                assertThrows(IllegalStateException::class.java) {
                    store.loadForActor(actor, device)
                }
                assertEquals(bytes, file.readText())
            }
            file.writeText(valid)
            assertEquals(original, store.loadForActor(actor, device))
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsTamperedOfflineCatalogShopNameWithoutChangingSnapshot() {
        val root = Files.createTempDirectory("pos-catalog-shop-name").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val store = MerchantPosOfflineDraftStore(root)
            val catalog = PosOfflineCatalog(actor, shop, device, "ร้านค้า", 100L,
                emptyList(), emptyList())
            store.saveCatalog(catalog)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val original = file.readText()
            for (invalidName in listOf("", " ", "x".repeat(201))) {
                val corrupted = JSONObject(original).put("shop_name", invalidName).toString()
                file.writeText(corrupted)
                assertThrows(IllegalStateException::class.java) { store.loadCatalog(actor, device) }
                assertEquals(corrupted, file.readText())
            }
            file.writeText(original)
            assertEquals(catalog, store.loadCatalog(actor, device))
        } finally { root.deleteRecursively() }
    }
    @Test fun rejectsInvalidOfflineCatalogProductNamesWithoutOverwritingSnapshot() {
        val root = Files.createTempDirectory("pos-catalog-product-name").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val product = PosProduct(UUID.randomUUID().toString(), "ชา", 30.0, null, true)
            val store = MerchantPosOfflineDraftStore(root)
            val original = PosOfflineCatalog(actor, shop, device, "ร้าน", 100L, listOf(product), emptyList())
            store.saveCatalog(original)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val saved = file.readText()
            for (badName in listOf("", "x".repeat(201))) {
                assertThrows(IllegalArgumentException::class.java) {
                    store.saveCatalog(original.copy(savedAtMs = 101L,
                        products = listOf(product.copy(name = badName))))
                }
                assertEquals(saved, file.readText())
                val tampered = JSONObject(saved)
                tampered.getJSONArray("products").getJSONObject(0).put("name", badName)
                file.writeText(tampered.toString())
                assertThrows(IllegalStateException::class.java) { store.loadCatalog(actor, device) }
                file.writeText(saved)
            }
            assertEquals(original, store.loadCatalog(actor, device))
        } finally { root.deleteRecursively() }
    }

    @Test fun refusesToOverwriteCatalogWithCorruptedPersistedShopName() {
        val root = Files.createTempDirectory("pos-catalog-corrupt-shop-name").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val store = MerchantPosOfflineDraftStore(root)
            val catalog = PosOfflineCatalog(actor, shop, device, "ร้าน", 100L,
                emptyList(), emptyList())
            store.saveCatalog(catalog)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val valid = JSONObject(file.readText())
            for (invalid in listOf("   ", "x".repeat(201))) {
                val corrupted = JSONObject(valid.toString()).put("shop_name", invalid).toString()
                file.writeText(corrupted)
                assertThrows(IllegalStateException::class.java) {
                    store.saveCatalog(catalog.copy(savedAtMs = 101L))
                }
                assertEquals(corrupted, file.readText())
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsInvalidOfflineTableLabelsOnSaveAndLoad() {
        val root = Files.createTempDirectory("pos-offline-table-label").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val store = MerchantPosOfflineDraftStore(root)
            val catalog = PosOfflineCatalog(actor, shop, device, "ร้าน", 100L,
                emptyList(), listOf(PosTable(UUID.randomUUID().toString(), "A1", true)))
            store.saveCatalog(catalog)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val original = file.readText()
            for (invalid in listOf("  ", "x".repeat(101))) {
                assertThrows(IllegalArgumentException::class.java) {
                    store.saveCatalog(catalog.copy(savedAtMs = 101L,
                        tables = listOf(catalog.tables.first().copy(label = invalid))))
                }
                assertEquals(original, file.readText())
                val tampered = JSONObject(original)
                tampered.getJSONArray("tables").getJSONObject(0).put("label", invalid)
                file.writeText(tampered.toString())
                assertThrows(IllegalStateException::class.java) {
                    store.loadCatalog(actor, device)
                }
                file.writeText(original)
            }
            assertEquals(catalog, store.loadCatalog(actor, device))
        } finally { root.deleteRecursively() }
    }

}
