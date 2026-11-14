package com.queuego.customer

import java.util.Locale

internal val marketCategories = linkedMapOf(
    "all" to "ทั้งหมด",
    "meat" to "เนื้อสัตว์",
    "seafood" to "ปลา / อาหารทะเล",
    "vegetable" to "ผักสด",
    "fruit" to "ผลไม้",
    "egg_tofu_fresh" to "ไข่ / เต้าหู้ / ของสด",
    "seasoning_herb" to "เครื่องปรุง / สมุนไพร",
    "rice_dry" to "ข้าว / ของแห้ง / ธัญพืช",
    "processed_ready_to_cook" to "อาหารแปรรูป / พร้อมปรุง",
    "prepared_food" to "อาหารทำสำเร็จจากตลาด",
    "dessert" to "ขนม / ของหวาน",
    "local" to "ของพื้นบ้าน / สินค้าชุมชน",
    "other_market" to "อื่นๆ ในตลาดสด"
)

internal fun marketCategoryKey(raw: String, name: String): String {
    val value = (raw + " " + name).lowercase(Locale.forLanguageTag("th"))
    if (Regex("seafood|fish|ปลา|กุ้ง|ปู|หมึก|หอย").containsMatchIn(value)) return "seafood"
    if (Regex("meat|pork|chicken|beef|หมู|ไก่|เป็ด|เนื้อ|เครื่องใน").containsMatchIn(value)) return "meat"
    if (Regex("vegetable|veg|ผัก|เห็ด|หน่อไม้").containsMatchIn(value)) return "vegetable"
    if (Regex("fruit|ผลไม้").containsMatchIn(value)) return "fruit"
    if (Regex("egg|tofu|ไข่|เต้าหู้").containsMatchIn(value)) return "egg_tofu_fresh"
    if (Regex("season|herb|spice|เครื่องปรุง|สมุนไพร|กระเทียม|พริก|หอม|ข่า|ตะไคร้|น้ำปลา|เครื่องแกง").containsMatchIn(value)) return "seasoning_herb"
    if (Regex("rice|dry|grain|ข้าว|ของแห้ง|ธัญพืช|ถั่ว|เส้น").containsMatchIn(value)) return "rice_dry"
    if (Regex("processed|ready.?to.?cook|ลูกชิ้น|ไส้กรอก|หมัก|แช่แข็ง|พร้อมปรุง").containsMatchIn(value)) return "processed_ready_to_cook"
    if (Regex("prepared|ready.?to.?eat|กับข้าว|น้ำพริก|ของทอด|พร้อมทาน").containsMatchIn(value)) return "prepared_food"
    if (Regex("dessert|bakery|ขนม|ของหวาน").containsMatchIn(value)) return "dessert"
    if (Regex("local|community|พื้นบ้าน|ชุมชน").containsMatchIn(value)) return "local"
    return "other_market"
}
