package com.queuego.merchant

import java.nio.file.Files
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Exercises real persisted offline POS snapshots without replacing the storage implementation. */
class MerchantPosOfflineStockRegressionTest {
    @Test fun malformedPersistedStockCannotBeLoadedOrSilentlyReplaced() {
        val root = Files.createTempDirectory("pos-stock-integrity").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val store = MerchantPosOfflineDraftStore(root)
            val product = PosProduct(UUID.randomUUID().toString(), "ชา", 35.0, null, true)
            val original = PosOfflineCatalog(actor, shop, device, "ร้าน", 100L, listOf(product), emptyList())
            store.saveCatalog(original)
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val valid = file.readText()

            for (invalid in listOf("12", -1, 1.5, true, JSONObject.NULL)) {
                val altered = JSONObject(valid)
                altered.getJSONArray("products").getJSONObject(0).put("stock", invalid)
                val corruptedBytes = altered.toString()
                file.writeText(corruptedBytes)
                assertThrows(Exception::class.java) { store.loadCatalog(actor, device) }
                assertThrows(Exception::class.java) {
                    store.saveCatalog(original.copy(savedAtMs = 101L))
                }
                assertEquals(corruptedBytes, file.readText())
            }

            // Old snapshots without stock predate inventory tracking; they retain zero stock.
            val legacy = JSONObject(valid)
            legacy.getJSONArray("products").getJSONObject(0).remove("stock")
            file.writeText(legacy.toString())
            assertEquals(0, store.loadCatalog(actor, device)!!.products.single().stock)
        } finally {
            root.deleteRecursively()
        }
    }
}
