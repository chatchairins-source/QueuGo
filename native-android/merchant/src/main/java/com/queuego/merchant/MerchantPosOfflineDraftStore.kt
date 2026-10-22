package com.queuego.merchant

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class PosOfflineDraftItem(
    val productId: String,
    val nameSnapshot: String,
    val priceSnapshot: Double,
    val quantity: Int,
    val requestIds: List<String> = emptyList()
)

data class PosOfflineDraft(
    val id: String,
    val requestId: String,
    val actorId: String,
    val shopId: String,
    val deviceId: String,
    val type: String,
    val tableId: String?,
    val savedAtMs: Long,
    val items: List<PosOfflineDraftItem>
)

data class PosOfflineDraftScope(val actorId: String, val shopId: String, val deviceId: String)

data class PosOfflineCatalog(
    val actorId: String,
    val shopId: String,
    val deviceId: String,
    val shopName: String,
    val savedAtMs: Long,
    val products: List<PosProduct>,
    val tables: List<PosTable>
)

/** Device-local, atomic and identity-scoped drafts. Drafts are never bill or payment records. */
class MerchantPosOfflineDraftStore(private val root: File) {
    @Synchronized fun deviceId(): String {
        check(root.isDirectory || root.mkdirs()) { "เตรียมพื้นที่เก็บร่าง POS ไม่สำเร็จ" }
        val file = File(root, "device-id")
        if (file.exists()) return file.readText().also(::requireUuid)
        val id = UUID.randomUUID().toString()
        atomicWrite(file, id.toByteArray(Charsets.UTF_8))
        return id
    }

    @Synchronized fun load(scope: PosOfflineDraftScope): PosOfflineDraft? {
        val file = file(scope)
        if (!file.exists()) return null
        check(file.length() <= MAX_BYTES) { "ข้อมูลร่าง POS ไม่ถูกต้อง" }
        val draft = decode(JSONObject(file.readText()))
        check(draft.actorId == scope.actorId && draft.deviceId == scope.deviceId) {
            "ร่าง POS ไม่ตรงกับบัญชีหรืออุปกรณ์นี้"
        }
        if (draft.shopId != scope.shopId) return null
        validate(draft)
        return draft
    }

    @Synchronized fun loadForActor(actorId: String, deviceId: String): PosOfflineDraft? {
        requireUuid(actorId); requireUuid(deviceId)
        val file = file(actorId, deviceId)
        if (!file.exists()) return null
        check(file.length() <= MAX_BYTES) { "ข้อมูลร่าง POS ไม่ถูกต้อง" }
        val draft = decode(JSONObject(file.readText()))
        check(draft.actorId == actorId && draft.deviceId == deviceId) { "ร่าง POS ไม่ตรงกับบัญชีหรืออุปกรณ์นี้" }
        validate(draft)
        return draft
    }

    @Synchronized fun save(draft: PosOfflineDraft): PosOfflineDraft {
        validate(draft)
        check(root.isDirectory || root.mkdirs()) { "บันทึกร่าง POS ไม่สำเร็จ" }
        val target = file(PosOfflineDraftScope(draft.actorId, draft.shopId, draft.deviceId))
        if (target.exists()) {
            val existing = decode(JSONObject(target.readText()))
            check(existing.id == draft.id && existing.requestId == draft.requestId) {
                "มีร่าง POS เดิมที่ยังไม่ส่ง กรุณาตรวจร่างนั้นก่อน"
            }
        }
        val json = encode(draft).toString().toByteArray(Charsets.UTF_8)
        check(json.size <= MAX_BYTES) { "ร่าง POS มีรายการมากเกินไป" }
        atomicWrite(target, json)
        return draft
    }

    @Synchronized fun saveCatalog(catalog: PosOfflineCatalog) {
        requireUuid(catalog.actorId); requireUuid(catalog.shopId); requireUuid(catalog.deviceId)
        require(catalog.shopName.length <= 200 && catalog.products.size <= 20_000 && catalog.tables.size <= 300)
        val json = JSONObject().put("actor_id", catalog.actorId).put("shop_id", catalog.shopId)
            .put("device_id", catalog.deviceId).put("shop_name", catalog.shopName.take(200))
            .put("saved_at_ms", catalog.savedAtMs)
            .put("products", JSONArray().apply { catalog.products.forEach { p ->
                put(JSONObject().put("id", p.id).put("name", p.name.take(200)).put("price", p.price)
                    .put("image", p.image ?: JSONObject.NULL).put("available", p.available)
                    .put("delivery_available", p.deliveryAvailable)
                    .put("delivery_price", p.deliveryPrice ?: JSONObject.NULL))
            } })
            .put("tables", JSONArray().apply { catalog.tables.forEach { t ->
                put(JSONObject().put("id", t.id).put("label", t.label.take(100)).put("active", t.active)
                    .put("qr_token", t.qrToken ?: JSONObject.NULL))
            } })
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_CATALOG_BYTES) { "แคตตาล็อก POS มีขนาดมากเกินไปสำหรับออฟไลน์" }
        check(root.isDirectory || root.mkdirs()) { "เตรียมพื้นที่เก็บ POS ออฟไลน์ไม่สำเร็จ" }
        atomicWrite(catalogFile(catalog.actorId, catalog.deviceId), bytes)
    }

    @Synchronized fun loadCatalog(actorId: String, deviceId: String): PosOfflineCatalog? {
        requireUuid(actorId); requireUuid(deviceId)
        val file = catalogFile(actorId, deviceId)
        if (!file.exists()) return null
        check(file.length() <= MAX_CATALOG_BYTES) { "แคตตาล็อก POS ออฟไลน์ไม่ถูกต้อง" }
        val json = JSONObject(file.readText())
        val storedActor = json.getString("actor_id")
        val storedDevice = json.getString("device_id")
        check(storedActor == actorId && storedDevice == deviceId) { "แคตตาล็อก POS ไม่ตรงกับบัญชีหรืออุปกรณ์นี้" }
        val products = json.getJSONArray("products").let { array -> (0 until array.length()).map { i ->
            val p = array.getJSONObject(i)
            PosProduct(p.getString("id"), p.getString("name"), p.getDouble("price"),
                if (p.isNull("image")) null else p.getString("image"), p.getBoolean("available"),
                p.optBoolean("delivery_available", true), if (p.isNull("delivery_price")) null else p.getDouble("delivery_price"))
        } }
        val tables = json.getJSONArray("tables").let { array -> (0 until array.length()).map { i ->
            val t = array.getJSONObject(i)
            PosTable(t.getString("id"), t.getString("label"), t.getBoolean("active"),
                if (t.isNull("qr_token")) null else t.getString("qr_token"))
        } }
        return PosOfflineCatalog(actorId, json.getString("shop_id"), deviceId,
            json.optString("shop_name", "ร้านค้า"), json.getLong("saved_at_ms"), products, tables)
    }

    @Synchronized fun remove(scope: PosOfflineDraftScope, id: String) {
        requireUuid(id)
        val draft = load(scope) ?: return
        check(draft.id == id) { "ร่าง POS เปลี่ยนแล้ว" }
        check(file(scope).delete()) { "ลบร่าง POS ไม่สำเร็จ กรุณาตรวจรายการเดิมอีกครั้ง" }
    }

    private fun file(scope: PosOfflineDraftScope): File {
        requireUuid(scope.actorId); requireUuid(scope.shopId); requireUuid(scope.deviceId)
        return file(scope.actorId, scope.deviceId)
    }

    private fun file(actorId: String, deviceId: String): File {
        requireUuid(actorId); requireUuid(deviceId)
        val key = "$actorId|$deviceId"
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(root, "$hash.json")
    }

    private fun catalogFile(actorId: String, deviceId: String): File {
        val key = "catalog|$actorId|$deviceId"
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(root, "$hash.catalog.json")
    }

    private fun encode(d: PosOfflineDraft) = JSONObject()
        .put("id", d.id).put("request_id", d.requestId).put("actor_id", d.actorId)
        .put("shop_id", d.shopId).put("device_id", d.deviceId).put("type", d.type)
        .put("table_id", d.tableId ?: JSONObject.NULL).put("saved_at_ms", d.savedAtMs)
        .put("items", JSONArray().apply { d.items.forEach { i ->
            put(JSONObject().put("product_id", i.productId).put("name", i.nameSnapshot)
                .put("price", i.priceSnapshot).put("quantity", i.quantity)
                .put("request_ids", JSONArray(i.requestIds)))
        } })

    private fun decode(j: JSONObject): PosOfflineDraft {
        val items = j.getJSONArray("items")
        return PosOfflineDraft(j.getString("id"), j.getString("request_id"), j.getString("actor_id"),
            j.getString("shop_id"), j.getString("device_id"), j.getString("type"),
            if (j.isNull("table_id")) null else j.getString("table_id"), j.getLong("saved_at_ms"),
            (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                val requests = item.getJSONArray("request_ids")
                PosOfflineDraftItem(item.getString("product_id"), item.getString("name"),
                    item.getDouble("price"), item.getInt("quantity"),
                    (0 until requests.length()).map(requests::getString))
            })
    }

    private fun validate(d: PosOfflineDraft) {
        requireUuid(d.id); requireUuid(d.requestId); requireUuid(d.actorId)
        requireUuid(d.shopId); requireUuid(d.deviceId); d.tableId?.let(::requireUuid)
        require(d.type in setOf("DINE_IN", "TAKEAWAY"))
        require(d.type != "TAKEAWAY" || d.tableId == null)
        require(d.items.isNotEmpty() && d.items.size <= 100)
        require(d.items.sumOf { it.quantity } <= 500)
        d.items.forEach { item ->
            requireUuid(item.productId)
            require(item.nameSnapshot.isNotBlank() && item.nameSnapshot.length <= 200)
            require(item.priceSnapshot.isFinite() && item.priceSnapshot >= 0)
            require(item.quantity in 1..99)
            require(item.requestIds.size in setOf(0, item.quantity))
            item.requestIds.forEach(::requireUuid)
            require(item.requestIds.toSet().size == item.requestIds.size)
        }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val temporary = File.createTempFile("pos-draft-", ".tmp", root)
        try {
            FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }

    private fun requireUuid(value: String) { require(UUID.fromString(value).toString() == value) }

    companion object {
        private const val MAX_BYTES = 256 * 1024
        private const val MAX_CATALOG_BYTES = 4 * 1024 * 1024
    }
}
