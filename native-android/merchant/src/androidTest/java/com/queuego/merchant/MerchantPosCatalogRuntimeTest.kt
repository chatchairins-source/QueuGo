package com.queuego.merchant

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Uses Android's org.json, whose null coercion differs from the JVM test library. */
class MerchantPosCatalogRuntimeTest {
    @Test fun missingNullAndNamedCategoriesSurviveOfflineReload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "pos-catalog-test-${UUID.randomUUID()}")
        try {
            val actor = UUID.randomUUID().toString()
            val device = UUID.randomUUID().toString()
            val products = listOf(
                PosProduct(UUID.randomUUID().toString(), "ไม่มีหมวด", 20.0, null, true),
                PosProduct(UUID.randomUUID().toString(), "ชาไทย", 45.0, null, true, category = "เครื่องดื่ม"),
                PosProduct(UUID.randomUUID().toString(), "หมวดชื่อ null", 30.0, null, true, category = "null")
            )
            val catalog = PosOfflineCatalog(actor, UUID.randomUUID().toString(), device,
                "ทดสอบในเครื่อง", 123L, products, emptyList())
            val store = MerchantPosOfflineDraftStore(root)
            store.saveCatalog(catalog)
            // Prove this test exercises Android's real null-to-string behavior.
            assertEquals("null", JSONObject().put("category", JSONObject.NULL).optString("category"))
            assertEquals(catalog, MerchantPosOfflineDraftStore(root).loadCatalog(actor, device))

            // Simulate a catalog saved by the version before category was introduced.
            val file = root.listFiles()!!.single { it.name.endsWith(".catalog.json") }
            val legacy = JSONObject(file.readText())
            val rows = legacy.getJSONArray("products")
            for (i in 0 until rows.length()) rows.getJSONObject(i).remove("category")
            file.writeText(legacy.toString())
            val restored = MerchantPosOfflineDraftStore(root).loadCatalog(actor, device)!!
            assertTrue(restored.products.all { it.category == null })
            assertEquals(products.map { it.id },
                filterPosProducts(restored.products, "", posProductCategoryKey(null)).map { it.id })
            assertEquals(listOf("ไม่จัดหมวด"), posProductCategories(restored.products).map { it.label })
        } finally {
            root.deleteRecursively()
        }
    }
}
