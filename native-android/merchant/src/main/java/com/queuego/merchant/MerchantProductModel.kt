package com.queuego.merchant

import org.json.JSONArray
import org.json.JSONObject

internal val merchantFoodMenuCategories = listOf(
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
)

internal val merchantMarketProductCategories = listOf(
    "ผักสด",
    "ผลไม้",
    "เนื้อหมู",
    "เนื้อวัว",
    "ไก่ / เป็ด",
    "ปลา",
    "อาหารทะเล",
    "ไข่",
    "เต้าหู้ / เส้นสด / ลูกชิ้น",
    "ของสดพร้อมปรุง",
    "อาหารแช่เย็น / แช่แข็ง",
    "พริกแกง / เครื่องแกง",
    "เครื่องปรุง / ซอส",
    "ข้าวสาร / ธัญพืช",
    "ของแห้ง",
    "อาหารปรุงสำเร็จ",
    "ขนม / ของหวาน",
    "เครื่องดื่ม",
    "ของใช้ในครัวเรือน",
    "ดอกไม้ / ของไหว้",
    "อื่น ๆ"
)

internal val merchantRetailProductCategories = listOf(
    "เครื่องดื่ม",
    "บะหมี่กึ่งสำเร็จรูป",
    "ข้าวสาร",
    "อาหารกระป๋อง",
    "ขนมอบ",
    "ของใช้ในบ้าน",
    "ของใช้ส่วนตัว",
    "เครื่องปรุง",
    "แช่เย็น/แช่แข็ง",
    "อื่นๆ"
)

private val merchantShoppingProductGroups = linkedMapOf(
    "mobile_accessories" to listOf(
        "โทรศัพท์มือถือ", "เคส / ฟิล์ม", "สายชาร์จ / อะแดปเตอร์",
        "หูฟัง / ลำโพง", "พาวเวอร์แบงก์ / แบตเตอรี่"
    ),
    "computer_it" to listOf(
        "คอมพิวเตอร์ / โน้ตบุ๊ก", "เมาส์ / คีย์บอร์ด", "อุปกรณ์จัดเก็บข้อมูล",
        "เน็ตเวิร์ก / เราเตอร์", "เครื่องพิมพ์ / หมึก"
    ),
    "automotive_car" to listOf(
        "น้ำมันเครื่อง / ของเหลว", "แบตเตอรี่", "ระบบเบรก", "ระบบไฟ",
        "ยาง / ล้อ", "เครื่องยนต์", "ช่วงล่าง", "อุปกรณ์ตกแต่งรถยนต์"
    ),
    "automotive_motorcycle" to listOf(
        "น้ำมันเครื่อง / ของเหลว", "แบตเตอรี่", "ระบบเบรก", "ระบบไฟ",
        "ยาง / ล้อ", "เครื่องยนต์", "โซ่ / สเตอร์", "อุปกรณ์ตกแต่งมอเตอร์ไซค์"
    ),
    "fashion_accessories" to listOf(
        "เสื้อผ้าผู้หญิง", "เสื้อผ้าผู้ชาย", "เสื้อผ้าเด็ก", "รองเท้า",
        "กระเป๋า", "นาฬิกา", "เครื่องประดับ"
    ),
    "toys" to listOf(
        "ของเล่นเด็กเล็ก", "ตุ๊กตา", "ตัวต่อ / บล็อก", "รถของเล่น / โมเดล",
        "เกม / ของเล่นเสริมทักษะ", "ของเล่นกลางแจ้ง"
    ),
    "home_decor" to listOf(
        "ของตกแต่งบ้าน", "เฟอร์นิเจอร์ขนาดเล็ก", "เครื่องนอน",
        "ห้องครัว / โต๊ะอาหาร", "โคมไฟ / แสงสว่าง",
        "จัดเก็บ / ออร์แกไนเซอร์", "สวน / ระเบียง"
    )
)

internal fun merchantIsFoodProductShop(shop: MerchantShop): Boolean =
    shop.category.lowercase() in setOf("food", "cafe")

internal fun merchantIsMarketProductShop(shop: MerchantShop): Boolean =
    shop.category.lowercase() in setOf("market", "meat", "fish", "vegetable", "fruit")

internal fun merchantProductCategories(shop: MerchantShop): List<String> {
    if (merchantIsFoodProductShop(shop)) return merchantFoodMenuCategories
    if (merchantIsMarketProductShop(shop)) return merchantMarketProductCategories
    if (shop.category.lowercase() == "shopping") {
        val seen = linkedSetOf<String>()
        shop.shoppingSubcategories.forEach { key ->
            merchantShoppingProductGroups[key]?.forEach(seen::add)
        }
        seen.add("อื่นๆ")
        if (seen.size > 1) return seen.toList()
    }
    return merchantRetailProductCategories
}

internal fun merchantDefaultProductCategory(shop: MerchantShop): String = when (shop.category.lowercase()) {
    "vegetable" -> "ผักสด"
    "fruit" -> "ผลไม้"
    "fish" -> "ปลา"
    else -> ""
}

data class MerchantToppingOption(
    val name: String,
    val price: Double,
    val enabled: Boolean
)

data class MerchantRestaurantOptionState(
    val portionEnabled: Boolean,
    val specialPrice: Double,
    val toppings: List<MerchantToppingOption>
)

internal fun merchantRestaurantOptionState(rawJson: String): MerchantRestaurantOptionState {
    val rows = runCatching { JSONArray(rawJson) }.getOrElse { JSONArray() }
    var portionEnabled = false
    var specialPrice = 10.0
    val toppings = mutableListOf<MerchantToppingOption>()

    for (i in 0 until rows.length()) {
        when (val row = rows.opt(i)) {
            is JSONObject -> {
                val key = row.optString("key")
                val options = row.optJSONArray("options")
                if (key == "portion" && options != null) {
                    portionEnabled = true
                    for (j in 0 until options.length()) {
                        val option = options.optJSONObject(j) ?: continue
                        if (option.optString("key") == "special" || option.optString("name") == "พิเศษ") {
                            val value = option.optDouble("price_delta", option.optDouble("price", 10.0))
                            if (value.isFinite() && value >= 0.0) specialPrice = value
                        }
                    }
                } else if (key == "toppings" && options != null) {
                    for (j in 0 until options.length()) {
                        val option = options.optJSONObject(j) ?: continue
                        val name = option.optString("name").trim()
                        val price = option.optDouble("price_delta", option.optDouble("price", 0.0))
                        if (name.isNotBlank() && price.isFinite() && price >= 0.0) {
                            toppings += MerchantToppingOption(name, price, true)
                        }
                    }
                } else if (options == null) {
                    val name = row.optString("name").trim()
                    val price = row.optDouble("price_delta", row.optDouble("price", 0.0))
                    if (name.isNotBlank() && price.isFinite() && price >= 0.0) {
                        toppings += MerchantToppingOption(name, price, true)
                    }
                }
            }
            is String -> {
                val name = row.trim()
                if (name.isNotBlank()) toppings += MerchantToppingOption(name, 0.0, true)
            }
        }
    }

    val resolved = if (toppings.isNotEmpty()) toppings else listOf(
        MerchantToppingOption("ไข่ดาว", 10.0, false),
        MerchantToppingOption("ไข่เจียว", 15.0, false),
        MerchantToppingOption("เพิ่มเนื้อ", 20.0, false),
        MerchantToppingOption("เพิ่มข้าว", 10.0, false)
    )
    return MerchantRestaurantOptionState(portionEnabled, specialPrice, resolved)
}

internal fun merchantRestaurantVariantsJson(
    portionEnabled: Boolean,
    specialPrice: Double,
    toppings: List<MerchantToppingOption>
): String {
    require(specialPrice.isFinite() && specialPrice in 0.0..999999.0) {
        "ราคาพิเศษไม่ถูกต้อง"
    }
    val groups = JSONArray()
    if (portionEnabled) {
        groups.put(
            JSONObject()
                .put("schema", "queuego.menu-options.v1")
                .put("key", "portion")
                .put("name", "ขนาด / ความพิเศษ")
                .put("type", "single")
                .put("required", true)
                .put(
                    "options",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("key", "normal")
                                .put("name", "ธรรมดา")
                                .put("price_delta", 0)
                        )
                        .put(
                            JSONObject()
                                .put("key", "special")
                                .put("name", "พิเศษ")
                                .put("price_delta", roundMerchantMoney(specialPrice))
                        )
                )
        )
    }

    val selected = toppings.filter { it.enabled }
    val seen = linkedSetOf<String>()
    val options = JSONArray()
    selected.forEachIndexed { index, topping ->
        val name = topping.name.trim()
        require(name.isNotBlank()) { "กรุณาระบุชื่อท็อปปิ้งที่เปิดใช้" }
        require(name.length <= 60) { "ชื่อท็อปปิ้งยาวเกินไป" }
        require(topping.price.isFinite() && topping.price in 0.0..999999.0) {
            "ราคาท็อปปิ้งไม่ถูกต้อง"
        }
        val key = name.lowercase()
        require(seen.add(key)) { "มีชื่อท็อปปิ้งซ้ำ: $name" }
        options.put(
            JSONObject()
                .put("key", "topping-" + (index + 1))
                .put("name", name)
                .put("price_delta", roundMerchantMoney(topping.price))
        )
    }
    if (options.length() > 0) {
        groups.put(
            JSONObject()
                .put("schema", "queuego.menu-options.v1")
                .put("key", "toppings")
                .put("name", "ท็อปปิ้ง")
                .put("type", "multi")
                .put("required", false)
                .put("options", options)
        )
    }
    return groups.toString()
}

internal fun merchantGenericVariantText(rawJson: String): String {
    val rows = runCatching { JSONArray(rawJson) }.getOrElse { JSONArray() }
    val names = buildList {
        for (i in 0 until rows.length()) {
            when (val row = rows.opt(i)) {
                is String -> row.trim().takeIf(String::isNotBlank)?.let(::add)
                is JSONObject -> if (!row.has("options")) {
                    row.optString("name").trim().takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }
    }
    return names.joinToString(", ")
}

internal fun merchantGenericVariantsJson(text: String): String =
    JSONArray(
        text.split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    ).toString()

private fun roundMerchantMoney(value: Double): Double =
    kotlin.math.round(value * 100.0) / 100.0
