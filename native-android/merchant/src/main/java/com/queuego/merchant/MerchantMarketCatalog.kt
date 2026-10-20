package com.queuego.merchant

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal val merchantMarketCatalogUnits = listOf("ชิ้น", "กิโลกรัม", "ขีด", "กรัม", "ถุง", "แพ็ก", "มัด", "ลูก", "ขวด", "กล่อง")
internal val merchantMarketCatalogCategories = setOf("ผักสด", "เห็ด", "ผลไม้", "เนื้อหมู", "เนื้อวัว", "ไก่ / เป็ด", "ปลา", "กุ้ง", "ปู", "หอย", "ปลาหมึก", "ไข่", "เต้าหู้ / เส้นสด / ลูกชิ้น", "ของแห้ง")
internal fun marketCatalogUuid(value: String): Boolean = runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)

internal data class MerchantMarketCatalogItem(
    val id: String, val name: String, val category: String, val sourceUnit: String,
    val image: String?, val suggestedUnit: String, val suggestedPack: Double, val productId: String?
)
internal data class MerchantMarketCatalogPage(val items: List<MerchantMarketCatalogItem>, val hasMore: Boolean)
internal fun merchantMarketCatalogPage(rows: JSONArray): MerchantMarketCatalogPage {
    val items = (0 until minOf(rows.length(), 50)).map { index ->
        val row = rows.getJSONObject(index)
        val id = row.getString("id")
        val category = row.getString("category")
        require(marketCatalogUuid(id) && category in merchantMarketCatalogCategories) { "ข้อมูลแคตตาล็อกตลาดไม่ครบ" }
        val image = row.optString("catalog_preview_image").takeIf { it.startsWith("https://") }
        val unit = row.optString("suggested_unit").takeIf { it in merchantMarketCatalogUnits } ?: "กิโลกรัม"
        val pack = row.optDouble("suggested_pack_size", 1.0).takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        MerchantMarketCatalogItem(id, row.getString("name_th"), category, row.optString("source_unit"), image, unit, pack,
            row.optString("product_id").takeIf(::marketCatalogUuid))
    }
    return MerchantMarketCatalogPage(items, rows.length() > 50)
}

internal data class MarketCatalogIntent(
    val requestId: String, val masterId: String, val price: Double, val unit: String,
    val pack: Double, val weight: Double, val cost: Double, val stock: Double, val minStock: Double, val available: Boolean
) {
    fun validate() {
        require(marketCatalogUuid(requestId) && marketCatalogUuid(masterId)) { "รหัสคำขอสินค้าไม่ถูกต้อง" }
        require(unit in merchantMarketCatalogUnits) { "หน่วยสินค้าไม่ถูกต้อง" }
        require(listOf(price, pack, weight).all { it.isFinite() && it > 0.0 && it <= 999999.0 }) { "กรอกราคา ปริมาณ และน้ำหนักจริงให้ครบ" }
        require(listOf(cost, stock, minStock).all { it.isFinite() && it >= 0.0 && it <= 999999.0 }) { "ต้นทุนหรือสต๊อกไม่ถูกต้อง" }
    }
    fun body(): JSONObject {
        validate()
        return JSONObject().put("p_request_id", requestId).put("p_master_product_id", masterId).put("p_price", price)
            .put("p_unit", unit).put("p_pack_size", pack).put("p_weight_kg", weight).put("p_cost", cost)
            .put("p_initial_stock", stock).put("p_min_stock", minStock).put("p_available", available)
    }
    companion object {
        fun fromBody(body: JSONObject) = MarketCatalogIntent(body.getString("p_request_id"), body.getString("p_master_product_id"),
            body.getDouble("p_price"), body.getString("p_unit"), body.getDouble("p_pack_size"), body.getDouble("p_weight_kg"),
            body.getDouble("p_cost"), body.getDouble("p_initial_stock"), body.getDouble("p_min_stock"), body.getBoolean("p_available")).also { it.validate() }
    }
}

/** Reject a stale archived/replaced shop before replaying its durable request. Server still owns eligibility. */
internal fun requireMerchantMarketCatalogShop(actual: Any?, expected: String) {
    check(actual is String && marketCatalogUuid(expected) && actual == expected) { "ร้านที่ระบบเลือกเปลี่ยนแล้ว กรุณากลับไปโหลดข้อมูลร้านใหม่ก่อนเพิ่มสินค้า" }
}
