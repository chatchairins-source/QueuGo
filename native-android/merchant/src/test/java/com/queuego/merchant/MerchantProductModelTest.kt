package com.queuego.merchant

import org.junit.Assert.*
import org.junit.Test

class MerchantProductModelTest {
    @Test fun foodMenuCategoriesMatchApprovedBlueprint() {
        assertEquals(
            listOf(
                "เมนูแนะนำ / เมนูขายดี",
                "อาหารจานเดียว",
                "ข้าว",
                "เส้น / ก๋วยเตี๋ยว",
                "ของทอด",
                "ของย่าง / ปิ้งย่าง",
                "ต้ม / แกง / ซุป",
                "ผัด",
                "ส้มตำ / ยำ",
                "กับข้าว",
                "อาหารทะเล",
                "ของทานเล่น",
                "ของหวาน",
                "เครื่องดื่ม",
                "ชุดคอมโบ / เซ็ต",
                "เมนูเด็ก",
                "เมนูสุขภาพ / คลีน",
                "เพิ่มเติม / ท็อปปิ้ง",
                "อื่น ๆ"
            ),
            merchantFoodMenuCategories
        )
    }

    @Test fun defaultFoodOptionsIncludeRequestedEggToppingAndNormalSpecialSchema() {
        val state = merchantRestaurantOptionState("[]")
        assertTrue(state.toppings.any { it.name == "ใส่ไข่" && it.price == 10.0 && !it.enabled })

        val json = merchantRestaurantVariantsJson(
            portionEnabled = true,
            specialPrice = 15.0,
            toppings = listOf(MerchantToppingOption("ใส่ไข่", 10.0, true))
        )
        assertTrue(json.contains("\"key\":\"normal\""))
        assertTrue(json.contains("\"name\":\"ธรรมดา\""))
        assertTrue(json.contains("\"key\":\"special\""))
        assertTrue(json.contains("\"name\":\"พิเศษ\""))
        assertTrue(json.contains("\"name\":\"ใส่ไข่\""))
    }
}
