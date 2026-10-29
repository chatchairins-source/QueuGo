package com.queuego.merchant

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal val merchantInventoryAdjustmentReasons = listOf(
    "receiving" to "รับสินค้าเข้า",
    "count_correction" to "แก้ยอดจากการตรวจนับ",
    "damage" to "สินค้าเสียหาย",
    "loss" to "สูญหาย",
    "return_restock" to "รับคืนและตรวจสภาพแล้ว",
    "other" to "อื่น ๆ"
)

data class MerchantInventoryAdjustmentIntent(
    val requestKey: String, val productId: String, val delta: Int, val reason: String, val note: String
) {
    fun body(): JSONObject {
        require(runCatching { UUID.fromString(requestKey) }.isSuccess) { "รหัสปรับสต็อกไม่ถูกต้อง" }
        require(productId.isNotBlank()) { "ไม่พบสินค้า" }
        require(delta != 0 && kotlin.math.abs(delta.toLong()) <= 100000L) { "จำนวนปรับสต็อกไม่ถูกต้อง" }
        require(reason in merchantInventoryAdjustmentReasons.map { it.first }) { "เลือกเหตุผลการปรับสต็อก" }
        val cleanNote = note.trim()
        require(cleanNote.length in 3..300) { "ระบุรายละเอียดอย่างน้อย 3 ตัวอักษร" }
        return JSONObject().put("p_product_id", productId).put("p_delta", delta)
            .put("p_reason", reason).put("p_note", cleanNote).put("p_request_key", requestKey)
    }
}

data class MerchantInventoryAdjustmentReceipt(val id: String, val stockBefore: Int, val stockAfter: Int, val replayed: Boolean)
data class MerchantInventoryAdjustment(
    val id: String, val eventType: String, val delta: Int, val stockBefore: Int, val stockAfter: Int,
    val reason: String?, val note: String?, val createdAt: String?, val actorName: String
)
data class MerchantInventoryAdjustmentPage(val entries: List<MerchantInventoryAdjustment>, val hasMore: Boolean)

internal fun merchantInventoryAdjustmentReceipt(raw: JSONObject): MerchantInventoryAdjustmentReceipt {
    val id = raw.optString("id")
    require(id.isNotBlank()) { "Server ยังไม่ยืนยันการปรับสต็อก" }
    return MerchantInventoryAdjustmentReceipt(id, raw.optInt("stock_before"), raw.optInt("stock_after"), raw.optBoolean("replayed"))
}

internal fun merchantInventoryAdjustmentPage(raw: JSONObject): MerchantInventoryAdjustmentPage {
    val rows = raw.optJSONArray("entries") ?: JSONArray()
    val entries = buildList {
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val id = row.optString("id")
            if (id.isBlank()) continue
            add(MerchantInventoryAdjustment(id, row.optString("event_type"), row.optInt("delta"),
                row.optInt("stock_before"), row.optInt("stock_after"),
                row.optString("reason").takeIf { it.isNotBlank() && it != "null" },
                row.optString("note").takeIf { it.isNotBlank() && it != "null" },
                row.optString("created_at").takeIf { it.isNotBlank() && it != "null" },
                row.optString("actor_name").ifBlank { "เจ้าของร้าน/ระบบ" }))
        }
    }
    return MerchantInventoryAdjustmentPage(entries, raw.optBoolean("has_more"))
}

internal fun requireMerchantStockEditAllowed(product: MerchantProduct?, nextStock: Int) {
    if (product?.inventoryStockTracking == true) {
        require(nextStock == product.stock) { "สินค้านี้ติดตามสต็อกอยู่ กรุณาใช้เมนูปรับสต็อกเพื่อบันทึกประวัติ" }
    }
}
