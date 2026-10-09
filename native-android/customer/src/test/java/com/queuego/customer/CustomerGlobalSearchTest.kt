package com.queuego.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerGlobalSearchTest {
    private fun shop(
        id: String,
        category: String,
        name: String = id,
        lat: Double? = null,
        lon: Double? = null,
        description: String? = null,
        address: String? = null
    ) = CustomerShop(
        id = id,
        name = name,
        category = category,
        logo = null,
        cover = null,
        address = address,
        latitude = lat,
        longitude = lon,
        open = true,
        description = description
    )

    @Test
    fun excludesDedicatedMarketCategoriesButKeepsNormalDirectories() {
        val rows = customerGlobalSearchRows(
            listOf(
                shop("food", "food"),
                shop("market", "market"),
                shop("veg", "vegetable"),
                shop("laundry", "laundry"),
                shop("shopping", "shopping")
            ),
            null,
            ""
        )
        assertEquals(listOf("food", "laundry", "shopping"), rows.map { it.id })
    }

    @Test
    fun sortsByRealCoordinatesWhenCustomerLocationExists() {
        val location = CustomerLocation(15.0000, 103.0000, "จุดส่ง")
        val rows = customerGlobalSearchRows(
            listOf(
                shop("far", "food", lat = 15.20, lon = 103.20),
                shop("near", "food", lat = 15.01, lon = 103.01),
                shop("unknown", "food")
            ),
            location,
            ""
        )
        assertEquals(listOf("near", "far", "unknown"), rows.map { it.id })
    }

    @Test
    fun searchesNameCategoryDescriptionAndAddress() {
        val rows = listOf(
            shop("a", "shopping", name = "ร้านเอ", description = "เคสมือถือ"),
            shop("b", "food", name = "ครัวบ้าน", address = "สวายจีก"),
            shop("c", "cafe", name = "กาแฟ")
        )
        assertEquals(listOf("a"), customerGlobalSearchRows(rows, null, "เคส").map { it.id })
        assertEquals(listOf("b"), customerGlobalSearchRows(rows, null, "อาหาร").map { it.id })
        assertEquals(listOf("b"), customerGlobalSearchRows(rows, null, "สวายจีก").map { it.id })
        assertTrue(customerGlobalSearchRows(rows, null, "เครื่องดื่ม").any { it.id == "c" })
        assertFalse(customerGlobalSearchRows(rows, null, "ตลาดสด").isNotEmpty())
    }
}
