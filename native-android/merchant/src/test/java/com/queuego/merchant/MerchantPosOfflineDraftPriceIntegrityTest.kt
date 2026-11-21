package com.queuego.merchant

import java.nio.file.Files
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MerchantPosOfflineDraftPriceIntegrityTest {
    @Test fun malformedDraftPricesCannotBeLoadedOrReplaced() {
        for (field in listOf("price", "base_price")) {
            for (badValue in listOf("30.0", true, JSONObject.NULL)) {
                val root = Files.createTempDirectory("pos-draft-price").toFile()
                try {
                    val actor = UUID.randomUUID().toString()
                    val shop = UUID.randomUUID().toString()
                    val device = UUID.randomUUID().toString()
                    val draft = PosOfflineDraft(
                        UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                        actor, shop, device, "TAKEAWAY", null, 10L,
                        listOf(PosOfflineDraftItem(UUID.randomUUID().toString(), "Tea", 30.0, 1))
                    )
                    val store = MerchantPosOfflineDraftStore(root)
                    store.save(draft)
                    val file = root.listFiles()!!.single { it.name.endsWith(".json") }
                    val json = JSONObject(file.readText())
                    json.getJSONArray("items").getJSONObject(0).put(field, badValue)
                    val corrupt = json.toString()
                    file.writeText(corrupt)
                    val scope = PosOfflineDraftScope(actor, shop, device)
                    assertThrows(IllegalStateException::class.java) { store.load(scope) }
                    assertThrows(IllegalStateException::class.java) {
                        store.save(draft.copy(savedAtMs = 11L))
                    }
                    assertEquals(corrupt, file.readText())
                } finally {
                    root.deleteRecursively()
                }
            }
        }
    }

    @Test fun validDraftPriceRoundTrips() {
        val root = Files.createTempDirectory("pos-draft-valid-price").toFile()
        try {
            val actor = UUID.randomUUID().toString()
            val shop = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val draft = PosOfflineDraft(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                actor, shop, device, "TAKEAWAY", null, 10L,
                listOf(PosOfflineDraftItem(UUID.randomUUID().toString(), "Tea", 35.0, 1,
                    basePriceSnapshot = 30.0))
            )
            val store = MerchantPosOfflineDraftStore(root)
            store.save(draft)
            val item = store.load(PosOfflineDraftScope(actor, shop, device))!!.items.single()
            assertEquals(35.0, item.priceSnapshot, 0.0)
            assertEquals(30.0, item.basePriceSnapshot, 0.0)
        } finally {
            root.deleteRecursively()
        }
    }
}
