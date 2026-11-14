package com.queuego.merchant

import java.nio.file.Files
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MerchantPosOfflineCatalogBooleanIntegrityTest {
    @Test fun rejectsCoercedProductAvailabilityWithoutOverwritingCorruptSnapshot() {
        val root = Files.createTempDirectory("pos-catalog-availability").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val catalog = PosOfflineCatalog(actor, UUID.randomUUID().toString(), device, "ร้าน", 10L,
                listOf(PosProduct(UUID.randomUUID().toString(), "ชา", 30.0, null, true)),
                emptyList())
            val store = MerchantPosOfflineDraftStore(root)
            store.saveCatalog(catalog)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val corrupt = JSONObject(file.readText())
            corrupt.getJSONArray("products").getJSONObject(0).put("available", "false")
            val bytes = corrupt.toString()
            file.writeText(bytes)

            assertThrows(IllegalStateException::class.java) { store.loadCatalog(actor, device) }
            assertThrows(IllegalStateException::class.java) {
                store.saveCatalog(catalog.copy(savedAtMs = 11L))
            }
            assertEquals(bytes, file.readText())
            file.writeText(JSONObject(bytes).apply {
                getJSONArray("products").getJSONObject(0).put("available", true)
            }.toString())
            assertEquals(catalog, store.loadCatalog(actor, device))
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsCoercedTableActiveFlagAndPreservesExistingFile() {
        val root = Files.createTempDirectory("pos-catalog-table-active").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val catalog = PosOfflineCatalog(actor, UUID.randomUUID().toString(), device, "ร้าน", 10L,
                emptyList(), listOf(PosTable(UUID.randomUUID().toString(), "โต๊ะ 1", true)))
            val store = MerchantPosOfflineDraftStore(root)
            store.saveCatalog(catalog)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val corrupt = JSONObject(file.readText())
            corrupt.getJSONArray("tables").getJSONObject(0).put("active", 1)
            val bytes = corrupt.toString()
            file.writeText(bytes)

            assertThrows(IllegalStateException::class.java) { store.loadCatalog(actor, device) }
            assertThrows(IllegalStateException::class.java) {
                store.saveCatalog(catalog.copy(savedAtMs = 11L))
            }
            assertEquals(bytes, file.readText())
            file.writeText(JSONObject(bytes).apply {
                getJSONArray("tables").getJSONObject(0).put("active", true)
            }.toString())
            assertEquals(catalog, store.loadCatalog(actor, device))
        } finally { root.deleteRecursively() }
    }
}
