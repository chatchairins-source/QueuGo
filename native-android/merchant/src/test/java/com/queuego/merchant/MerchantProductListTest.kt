package com.queuego.merchant

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantProductListTest {
    private fun product(
        id: String,
        name: String,
        category: String = "",
        available: Boolean = true,
        price: Double = 10.0
    ) = MerchantProduct(
        id = id,
        name = name,
        description = null,
        price = price,
        deliveryPrice = price,
        image = null,
        available = available,
        category = category
    )

    @Test fun searchMatchesProductNameAndCategory() {
        val rows = listOf(
            product("coffee", "กาแฟลาเต้", "เครื่องดื่ม"),
            product("cake", "ชีสเค้ก", "ของหวาน"),
            product("tea", "Green Tea", "เครื่องดื่ม")
        )
        assertEquals(listOf("coffee"), merchantProductRows(rows, "cafe", "all", "ลาเต้").map { it.id })
        assertEquals(listOf("coffee", "tea"), merchantProductRows(rows, "cafe", "all", "เครื่องดื่ม").map { it.id })
        assertEquals(listOf("tea"), merchantProductRows(rows, "cafe", "all", "green").map { it.id })
    }

    @Test fun availabilityFilterComposesWithSearch() {
        val rows = listOf(
            product("on", "ชาไทย", available = true),
            product("off", "ชาเขียว", available = false)
        )
        assertEquals(listOf("on"), merchantProductRows(rows, "cafe", "on", "ชา").map { it.id })
        assertEquals(listOf("off"), merchantProductRows(rows, "cafe", "off", "ชา").map { it.id })
    }

    @Test fun groceryStillHidesUnpricedRowsBeforeSearch() {
        val rows = listOf(
            product("priced", "น้ำดื่ม", price = 12.0),
            product("unpriced", "น้ำอัดลม", price = 0.0)
        )
        assertEquals(listOf("priced"), merchantProductRows(rows, "grocery", "all", "น้ำ").map { it.id })
    }

    @Test fun blankSearchRetainsCurrentModeOrdering() {
        val rows = listOf(
            product("a", "A", available = true),
            product("b", "B", available = false),
            product("c", "C", available = true)
        )
        assertEquals(listOf("a", "c"), merchantProductRows(rows, "cafe", "on", "   ").map { it.id })
    }
}
