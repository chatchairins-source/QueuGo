package com.queuego.merchant

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MerchantProductCategoryTest {
    private fun product(category: Any?, metadata: Any? = null) =
        merchantProductFromRow(JSONObject().put("id", "product")
            .put("category", category ?: JSONObject.NULL)
            .put("metadata", JSONObject().put("category", metadata ?: JSONObject.NULL)))!!

    @Test fun jsonNullUsesMetadataCategory() {
        assertEquals("ผักสด", product(null, "ผักสด").category)
    }
    @Test fun bothNullNeverRenderNullText() {
        assertEquals("", product(null).category)
    }
    @Test fun currentCategoryWinsOverMetadata() {
        assertEquals("เครื่องดื่ม", product("เครื่องดื่ม", "อาหาร").category)
    }
    @Test fun missingAndBlankRetainExistingFallback() {
        assertEquals("", merchantProductFromRow(JSONObject().put("id", "p"))!!.category)
        assertEquals("อาหาร", product("", "อาหาร").category)
    }
}
