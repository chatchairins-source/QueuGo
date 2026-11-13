package com.queuego.merchant

import java.nio.file.Files
import java.util.UUID
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MerchantPosOfflineCatalogOptionalFlagsTest {
    @Test fun malformedOptionalFlagsCannotBeSilentlyCoercedOrOverwritten() {
        for (key in listOf("delivery_available", "inventory_stock_tracking", "grocery_stock_tracking")) {
            val root = Files.createTempDirectory("pos-optional-flag").toFile()
            try {
                val actor = UUID.randomUUID().toString()
                val device = UUID.randomUUID().toString()
                val catalog = PosOfflineCatalog(actor, UUID.randomUUID().toString(), device, "ร้าน", 10L,
                    listOf(PosProduct(UUID.randomUUID().toString(), "ชา", 30.0, null, true)), emptyList())
                val store = MerchantPosOfflineDraftStore(root)
                store.saveCatalog(catalog)
                val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
                val corrupt = JSONObject(file.readText()).apply {
                    getJSONArray("products").getJSONObject(0).put(key, "false")
                }.toString()
                file.writeText(corrupt)
                assertThrows(IllegalStateException::class.java) { store.loadCatalog(actor, device) }
                assertThrows(IllegalStateException::class.java) {
                    store.saveCatalog(catalog.copy(savedAtMs = 11L))
                }
                assertEquals(corrupt, file.readText())
            } finally { root.deleteRecursively() }
        }
    }

    @Test fun missingLegacyOptionalFlagsRetainTheirDefaults() {
        val root = Files.createTempDirectory("pos-legacy-flags").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val catalog = PosOfflineCatalog(actor, UUID.randomUUID().toString(), device, "ร้าน", 10L,
                listOf(PosProduct(UUID.randomUUID().toString(), "ชา", 30.0, null, true)), emptyList())
            val store = MerchantPosOfflineDraftStore(root)
            store.saveCatalog(catalog)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val json = JSONObject(file.readText())
            val product = json.getJSONArray("products").getJSONObject(0)
            product.remove("delivery_available")
            product.remove("inventory_stock_tracking")
            product.remove("grocery_stock_tracking")
            file.writeText(json.toString())
            val restored = store.loadCatalog(actor, device)!!.products.single()
            assertTrue(restored.deliveryAvailable)
            assertFalse(restored.inventoryStockTracking)
            assertFalse(restored.groceryStockTracking)
        } finally { root.deleteRecursively() }
    }
}
