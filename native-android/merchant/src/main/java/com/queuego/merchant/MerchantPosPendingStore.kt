package com.queuego.merchant

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject
import org.json.JSONArray

data class PosEditIntent(
    val requestId: String,
    val billId: String?,
    val type: String,
    val tableId: String?,
    val productId: String,
    val quantity: Int,
    val note: String
) {
    fun body(): JSONObject = JSONObject().put("p_request", requestId).put("p_order", billId ?: JSONObject.NULL)
        .put("p_type", type).put("p_table", tableId ?: JSONObject.NULL)
        .put("p_product", productId).put("p_quantity", quantity).put("p_note", note)
}

/** One unresolved edit per authenticated actor; caller supplies an app-private no-backup root. */
class MerchantPosPendingStore(private val root: File) {
    private fun file(actor: String): File {
        require(actor.isNotBlank())
        val hash = MessageDigest.getInstance("SHA-256").digest(actor.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(root, "$hash.json")
    }

    @Synchronized fun load(actor: String): PosEditIntent? {
        val file = file(actor)
        if (!file.exists()) return null
        check(file.length() <= 16 * 1024) { "ข้อมูลรายการ POS ค้างไม่ถูกต้อง" }
        val json = JSONObject(file.readText())
        check(json.getString("actor") == actor) { "รายการ POS ค้างไม่ตรงกับบัญชี" }
        val body = json.getJSONObject("body")
        val request = body.getString("p_request")
        require(UUID.fromString(request).toString() == request)
        return PosEditIntent(request, if (body.isNull("p_order")) null else body.getString("p_order"), body.getString("p_type"),
            if (body.isNull("p_table")) null else body.getString("p_table"),
            body.getString("p_product"), body.getInt("p_quantity"), body.getString("p_note"))
            .also(::validate)
    }

    @Synchronized fun prepare(actor: String, intent: PosEditIntent): PosEditIntent {
        validate(intent)
        load(actor)?.let { old ->
            check(old.copy(requestId = intent.requestId) == intent) {
                "มีรายการ POS ก่อนหน้าที่ยังไม่ทราบผล กรุณากดตรวจรายการค้างก่อนทำรายการอื่น"
            }
            return old
        }
        check(root.isDirectory || root.mkdirs()) { "บันทึกรายการ POS ค้างไม่สำเร็จ" }
        val temporary = File.createTempFile("pos-edit-", ".tmp", root)
        try {
            val bytes = JSONObject().put("actor", actor).put("body", intent.body())
                .toString().toByteArray(Charsets.UTF_8)
            FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
            Files.move(temporary.toPath(), file(actor).toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally { temporary.delete() }
        return intent
    }

    @Synchronized fun clear(actor: String, requestId: String) {
        val pending = load(actor) ?: return
        check(pending.requestId == requestId) { "รายการ POS ค้างเปลี่ยนแล้ว" }
        check(file(actor).delete()) { "ล้างรายการ POS ค้างไม่สำเร็จ กรุณาตรวจรายการเดิมอีกครั้ง" }
    }

    private fun validate(intent: PosEditIntent) {
        require(UUID.fromString(intent.requestId).toString() == intent.requestId)
        intent.billId?.let { require(UUID.fromString(it).toString() == it) }
        require(UUID.fromString(intent.productId).toString() == intent.productId)
        intent.tableId?.let { require(UUID.fromString(it).toString() == it) }
        require(intent.type in setOf("DINE_IN", "TAKEAWAY"))
        require(intent.quantity in setOf(-1, 1))
        require(intent.billId != null || intent.quantity == 1)
        require(intent.note.length <= 500 && intent.note == intent.note.trim())
        require(intent.type != "TAKEAWAY" || intent.tableId == null)
    }
}

/** Only a canonical server order UUID is an acknowledgement of a pending mutation. */
internal fun merchantPosReplayOrderId(raw: Any, expectedBillId: String?): String {
    fun value(item: Any?): String? = when (item) {
        is String -> item.trim().trim('"')
        is JSONObject -> listOf("id", "pos_create_bill_once", "pos_edit_bill_once")
            .firstNotNullOfOrNull { key -> (item.opt(key) as? String)?.takeIf { it.isNotBlank() } }
        is JSONArray -> value(item.opt(0))
        else -> null
    }
    val id = value(raw)
    check(id != null && runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
        && (expectedBillId == null || id == expectedBillId)) { "Server ยังไม่ยืนยันรายการ POS เดิม" }
    return id
}
