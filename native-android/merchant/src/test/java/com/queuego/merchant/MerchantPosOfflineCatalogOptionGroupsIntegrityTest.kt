package com.queuego.merchant

import java.nio.file.Files
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MerchantPosOfflineCatalogOptionGroupsIntegrityTest {
    @Test fun rejectsMalformedOptionGroupsWithoutReplacingPersistedCatalog() {
        for (bad in listOf(JSONObject.NULL, "[]", true, 7, JSONObject())) {
            val root = Files.createTempDirectory("pos-catalog-options-invalid").toFile()
            try {
                val actor = UUID.randomUUID().toString()
                val device = UUID.randomUUID().toString()
                val catalog = PosOfflineCatalog(actor, UUID.randomUUID().toString(), device, "Shop", 10L,
                    listOf(PosProduct(UUID.randomUUID().toString(), "Tea", 30.0, null, true)), emptyList())
                val store = MerchantPosOfflineDraftStore(root)
                store.saveCatalog(catalog)
                val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
                val json = JSONObject(file.readText())
                json.getJSONArray("products").getJSONObject(0).put("option_groups", bad)
                val corrupted = json.toString()
                file.writeText(corrupted)

                assertThrows(IllegalStateException::class.java) { store.loadCatalog(actor, device) }
                assertThrows(IllegalStateException::class.java) { store.saveCatalog(catalog.copy(savedAtMs = 11L)) }
                assertEquals(corrupted, file.readText())
            } finally {
                root.deleteRecursively()
            }
        }
    }

    @Test fun absentLegacyOptionGroupsStillLoads() {
        val root = Files.createTempDirectory("pos-catalog-options-legacy").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val catalog = PosOfflineCatalog(actor, UUID.randomUUID().toString(), device, "Shop", 10L,
                listOf(PosProduct(UUID.randomUUID().toString(), "Tea", 30.0, null, true)), emptyList())
            val store = MerchantPosOfflineDraftStore(root)
            store.saveCatalog(catalog)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val json = JSONObject(file.readText())
            json.getJSONArray("products").getJSONObject(0).remove("option_groups")
            file.writeText(json.toString())

            assertEquals(catalog, store.loadCatalog(actor, device))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun validEmptyOptionGroupsRoundTrip() {
        val root = Files.createTempDirectory("pos-catalog-options-valid").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val catalog = PosOfflineCatalog(actor, UUID.randomUUID().toString(), device, "Shop", 10L,
                listOf(PosProduct(UUID.randomUUID().toString(), "Tea", 30.0, null, true)), emptyList())
            val store = MerchantPosOfflineDraftStore(root)
            store.saveCatalog(catalog)
            assertEquals(catalog, store.loadCatalog(actor, device))
        } finally {
            root.deleteRecursively()
        }
    }
}
