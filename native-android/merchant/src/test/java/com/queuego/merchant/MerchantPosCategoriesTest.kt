package com.queuego.merchant

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantPosCategoriesTest {
    private val products = listOf(
        PosProduct("1", "Latte", 55.0, null, true, category = "เครื่องดื่ม"),
        PosProduct("2", "ชาไทย", 45.0, null, true, category = "เครื่องดื่ม"),
        PosProduct("3", "ขนมปัง", 30.0, null, true, category = "เบเกอรี่"),
        PosProduct("4", "สินค้าไม่มีหมวด", 20.0, null, true)
    )

    @Test fun categoriesAreDistinctSortedAndIncludeUncategorisedProducts() {
        assertEquals(
            listOf("เบเกอรี่", "เครื่องดื่ม", "ไม่จัดหมวด"),
            posProductCategories(products).map { it.label }
        )
    }

    @Test fun categoryAndTextSearchCombineAndAllRestoresCatalog() {
        assertEquals(listOf("1"), filterPosProducts(products, "latte", "เครื่องดื่ม".lowercase()).map { it.id })
        assertEquals(listOf("3"), filterPosProducts(products, "เบเกอรี่", "เบเกอรี่".lowercase()).map { it.id })
        assertEquals(4, filterPosProducts(products, "", "all").size)
        assertEquals(emptyList<String>(), filterPosProducts(products, "latte", "เบเกอรี่".lowercase()).map { it.id })
    }
}
