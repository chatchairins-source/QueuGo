package com.queuego.merchant

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MerchantGrocerySourceTest {
    private fun row(id: String) = JSONObject().put("source_product_id", id)
        .put("title_th", "มาม่า บะหมี่กึ่งสำเร็จรูป").put("brand", "มาม่า")
        .put("queuego_category", "บะหมี่กึ่งสำเร็จรูป").put("in_shop", true)
        .put("image_url", "https://images.mango-prod.siammakro.cloud/product.jpg")

    @Test fun sentinelAndSourceIdentityArePreserved() {
        val rows = JSONArray()
        repeat(51) { rows.put(row(it.toString())) }
        val page = merchantGrocerySourcePage(rows)
        assertEquals(50, page.items.size)
        assertTrue(page.hasMore)
        assertEquals("source:0", page.items.first().id)
        assertEquals("0", page.items.first().sourceProductId)
        assertEquals("บะหมี่กึ่งสำเร็จรูป", page.items.first().category)
        assertTrue(page.items.first().enabled)
        assertNull(page.items.first().sellingPrice)
        assertTrue(page.items.first().posAvailable)
        assertTrue(page.items.first().deliveryAvailable)
    }

    @Test fun merchantReferenceImagesUseOnlyPinnedCatalogHost() {
        val page = merchantGrocerySourcePage(JSONArray().put(row(""))
            .put(row("1")))
        assertEquals(1, page.items.size)
        assertEquals("https://images.mango-prod.siammakro.cloud/product.jpg", page.items.first().image)
        assertFalse(page.hasMore)

        val untrusted = merchantGrocerySourcePage(JSONArray().put(
            row("2").put("image_url", "https://untrusted.invalid/p.jpg")
        ))
        assertNull(untrusted.items.single().image)
    }

    @Test fun unpricedDraftsStayOutOfGroceryProducts() {
        fun product(price: Double, posPrice: Double? = null) = MerchantProduct(
            id = price.toString(), name = "item", description = null, price = price,
            deliveryPrice = price, image = null, available = true, posPrice = posPrice)
        val products = listOf(product(0.0), product(10.0), product(20.0, 0.0), product(Double.NaN))
        assertEquals(listOf(products[1]), merchantPricedProducts(products, true))
        assertEquals(products, merchantPricedProducts(products, false))
    }
}
