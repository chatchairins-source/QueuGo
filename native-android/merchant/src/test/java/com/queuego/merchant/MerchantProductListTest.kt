package com.queuego.merchant

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantProductListTest {
    private fun product(name: String, category: String, available: Boolean = true) = MerchantProduct(
        id = name,
        name = name,
        description = null,
        price = 20.0,
        deliveryPrice = 20.0,
        image = null,
        available = available,
        category = category
    )

    @Test fun foodAndCafeUseMenuCopy() {
        assertEquals("เพิ่มเมนู", merchantProductAddLabel("food"))
        assertEquals("เพิ่มเมนู", merchantProductAddLabel("cafe"))
    }

    @Test fun productShopsKeepProductCopy() {
        assertEquals("เพิ่มสินค้า", merchantProductAddLabel("shopping"))
        assertEquals("เพิ่มสินค้า", merchantProductAddLabel("other"))
        assertEquals("เพิ่มสินค้า", merchantProductAddLabel("market"))
    }

    @Test fun foodCategoryMatchingIsCaseInsensitive() {
        assertEquals("เพิ่มเมนู", merchantProductAddLabel("Food"))
        assertEquals("เพิ่มเมนู", merchantProductAddLabel("CAFE"))
    }

    @Test fun searchMatchesProductNameAndCategoryCaseInsensitively() {
        val tea = product("ชาไทย", "เครื่องดื่ม")
        assertEquals(true, merchantProductMatchesView(tea, "ชา", ""))
        assertEquals(true, merchantProductMatchesView(tea, "เครื่อง", ""))
        assertEquals(false, merchantProductMatchesView(tea, "กาแฟ", ""))
    }

    @Test fun groceryCategoryFilterUsesUncategorizedFallback() {
        val tea = product("ชาไทย", "เครื่องดื่ม")
        val uncategorized = product("แก้ว", "")
        assertEquals(true, merchantProductMatchesView(tea, "", "เครื่องดื่ม"))
        assertEquals(false, merchantProductMatchesView(tea, "", "ขนม"))
        assertEquals(true, merchantProductMatchesView(uncategorized, "", "ไม่ระบุหมวด"))
    }

    @Test fun deliveryRestrictionsIncludeProductDescription() {
        assertEquals("alcohol", merchantDeliveryRestriction("สินค้า", "ทั่วไป", "เครื่องดื่ม เบียร์"))
        assertEquals("tobacco", merchantDeliveryRestriction("สินค้า", "ทั่วไป", "มีส่วนผสมของ tobacco"))
        assertEquals("none", merchantDeliveryRestriction("สินค้า", "ทั่วไป", "แก้วน้ำ"))
    }

    @Test fun existingCustomCategoryRemainsSelectableWhenEditing() {
        val shop = MerchantShop(
            id = "s1", name = "ร้านทดสอบ", category = "grocery", logo = null, cover = null,
            address = null, status = "active", onboardingStatus = "approved", deliveryEnabled = true
        )
        val categories = merchantProductCategories(shop, listOf("หมวดเฉพาะของร้าน"))
        assertTrue("หมวดเฉพาะของร้าน" in categories)
        assertEquals(1, categories.count { it == "หมวดเฉพาะของร้าน" })
    }

    @Test fun effectiveGpRateRejectsMissingAndInvalidServerValues() {
        assertEquals(12.5, merchantEffectiveGpRate(JSONObject().put("effective_gp_rate", 12.5)), 0.0)
        assertEquals(8.0, merchantEffectiveGpRate(JSONArray().put(JSONObject().put("effective_gp_rate", "8"))), 0.0)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            merchantEffectiveGpRate(JSONObject())
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            merchantEffectiveGpRate("not-a-rate")
        }
    }
}
