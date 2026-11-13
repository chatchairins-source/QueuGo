package com.queuego.merchant

import java.nio.file.Files
import java.util.UUID
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MerchantPosOfflineCatalogPriceIntegrityTest {
    @Test fun rejectsMalformedPersistedPricesWithoutReplacingSnapshot() {
        for (field in listOf("price", "delivery_price")) {
            for (bad in if (field == "price") listOf("30.0", true, JSONObject.NULL, -1) else listOf("30.0", true, -1)) {
                val root = Files.createTempDirectory("pos-catalog-price").toFile()
                try {
                    val actor = UUID.randomUUID().toString()
                    val device = UUID.randomUUID().toString()
                    val catalog = PosOfflineCatalog(actor, UUID.randomUUID().toString(), device, "Shop", 10L,
                        listOf(PosProduct(UUID.randomUUID().toString(), "Tea", 30.0, null, true,
                            deliveryPrice = 35.0)), emptyList())
                    val store = MerchantPosOfflineDraftStore(root)
                    store.saveCatalog(catalog)
                    val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
                    val json = JSONObject(file.readText())
                    json.getJSONArray("products").getJSONObject(0).put(field, bad)
                    val corrupted = json.toString()
                    file.writeText(corrupted)

                    assertThrows(IllegalStateException::class.java) { store.loadCatalog(actor, device) }
                    assertThrows(IllegalStateException::class.java) {
                        store.saveCatalog(catalog.copy(savedAtMs = 11L))
                    }
                    assertEquals(corrupted, file.readText())
                } finally {
                    root.deleteRecursively()
                }
            }
        }
    }

    @Test fun validCatalogPricesRoundTrip() {
        val root = Files.createTempDirectory("pos-catalog-valid-price").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val catalog = PosOfflineCatalog(actor, UUID.randomUUID().toString(), device, "Shop", 10L,
                listOf(PosProduct(UUID.randomUUID().toString(), "Tea", 30.0, null, true,
                    deliveryPrice = 35.0)), emptyList())
            val store = MerchantPosOfflineDraftStore(root)
            store.saveCatalog(catalog)
            assertEquals(catalog, store.loadCatalog(actor, device))
        } finally {
            root.deleteRecursively()
        }
    }
}
