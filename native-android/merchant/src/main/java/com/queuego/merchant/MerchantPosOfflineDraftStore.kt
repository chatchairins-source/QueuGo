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
    val requestIds: List<String> = emptyList(),
    val basePriceSnapshot: Double = priceSnapshot,
    val optionsJson: String = "{}",
    val optionLabel: String = ""
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
        if (file.exists()) {
            check(file.isFile && file.length() in 1..36) { "รหัสอุปกรณ์ POS ไม่ถูกต้อง" }
            return file.readText(Charsets.UTF_8).also(::requireUuid)
        }
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
            check(target.length() <= MAX_BYTES) { "ข้อมูลร่าง POS ไม่ถูกต้อง" }
            val existing = decode(JSONObject(target.readText()))
            // Never replace a persisted draft with invalid replay identities or another shop's data.
            validate(existing)
            check(existing.actorId == draft.actorId && existing.shopId == draft.shopId &&
                existing.deviceId == draft.deviceId) { "ร่าง POS ไม่ตรงกับบัญชี ร้านค้า หรืออุปกรณ์นี้" }
            check(existing.id == draft.id && existing.requestId == draft.requestId) {
                "มีร่าง POS เดิมที่ยังไม่ส่ง กรุณาตรวจร่างนั้นก่อน"
            }
            // An out-of-order offline update must not roll a newer local draft back.
            check(existing.savedAtMs <= draft.savedAtMs) {
                "ร่าง POS ใหม่กว่าข้อมูลที่กำลังบันทึก"
            }
            // The same timestamp cannot represent two different cart revisions.
            check(existing.savedAtMs != draft.savedAtMs || encode(existing).similar(encode(draft))) {
                "ร่าง POS มีข้อมูลขัดแย้งในเวลาเดียวกัน"
            }
        }
        val json = encode(draft).toString().toByteArray(Charsets.UTF_8)
        check(json.size <= MAX_BYTES) { "ร่าง POS มีรายการมากเกินไป" }
        atomicWrite(target, json)
        return draft
    }

    @Synchronized fun saveCatalog(catalog: PosOfflineCatalog) {
        requireUuid(catalog.actorId); requireUuid(catalog.shopId); requireUuid(catalog.deviceId)
        require(catalog.savedAtMs >= 0) { "เวลาแคตตาล็อก POS ไม่ถูกต้อง" }
        require(catalog.shopName.isNotBlank() && catalog.shopName.length <= 200 &&
            catalog.products.size <= 20_000 && catalog.tables.size <= 300)
        require(catalog.products.all { it.id.isNotBlank() && it.name.isNotBlank() && it.name.length <= 200 &&
            it.price.isFinite() && it.price >= 0 &&
            (it.deliveryPrice == null || (it.deliveryPrice.isFinite() && it.deliveryPrice >= 0)) &&
            it.stock >= 0 }) {
            "ราคาสินค้า POS ออฟไลน์ไม่ถูกต้อง"
        }
        require(catalog.products.map { it.id }.distinct().size == catalog.products.size) {
            "รหัสสินค้า POS ออฟไลน์ซ้ำกัน"
        }
        require(catalog.tables.map { it.id }.distinct().size == catalog.tables.size) {
            "รหัสโต๊ะ POS ออฟไลน์ซ้ำกัน"
        }
        require(catalog.tables.all { it.id.isNotBlank() && it.label.isNotBlank() && it.label.length <= 100 }) {
            "ข้อมูลโต๊ะ POS ออฟไลน์ไม่ถูกต้อง"
        }
        val json = JSONObject().put("actor_id", catalog.actorId).put("shop_id", catalog.shopId)
            .put("device_id", catalog.deviceId).put("shop_name", catalog.shopName.take(200))
            .put("saved_at_ms", catalog.savedAtMs)
            .put("products", JSONArray().apply { catalog.products.forEach { p ->
                put(JSONObject().put("id", p.id).put("name", p.name.take(200)).put("price", p.price)
                    .put("image", p.image ?: JSONObject.NULL).put("available", p.available)
                    .put("delivery_available", p.deliveryAvailable)
                    .put("delivery_price", p.deliveryPrice ?: JSONObject.NULL)
                    .put("stock", p.stock)
                    .put("inventory_stock_tracking", p.inventoryStockTracking)
                    .put("grocery_master_id", p.groceryMasterId ?: JSONObject.NULL)
                    .put("grocery_stock_tracking", p.groceryStockTracking)
                    .put("option_groups", posProductOptionGroupsJson(p.optionGroups))
                    .put("category", p.category ?: JSONObject.NULL))
            } })
            .put("tables", JSONArray().apply { catalog.tables.forEach { t ->
                put(JSONObject().put("id", t.id).put("label", t.label.take(100)).put("active", t.active)
                    .put("qr_token", t.qrToken ?: JSONObject.NULL))
            } })
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_CATALOG_BYTES) { "แคตตาล็อก POS มีขนาดมากเกินไปสำหรับออฟไลน์" }
        check(root.isDirectory || root.mkdirs()) { "เตรียมพื้นที่เก็บ POS ออฟไลน์ไม่สำเร็จ" }
        val target = catalogFile(catalog.actorId, catalog.deviceId)
        if (target.exists()) {
            check(target.length() <= MAX_CATALOG_BYTES) { "แคตตาล็อก POS ออฟไลน์ไม่ถูกต้อง" }
            val existing = JSONObject(target.readText())
            check(existing.getString("actor_id") == catalog.actorId &&
                existing.getString("device_id") == catalog.deviceId &&
                existing.getString("shop_id") == catalog.shopId) {
                "แคตตาล็อก POS ไม่ตรงกับบัญชี ร้านค้า หรืออุปกรณ์นี้"
            }
            val storedShopName = existing.optString("shop_name", "")
            check(storedShopName.isNotBlank() && storedShopName.length <= 200) {
                "ชื่อร้านค้า POS ออฟไลน์ไม่ถูกต้อง"
            }
            // A delayed offline refresh must never replace a newer catalog snapshot.
            val existingSavedAtMs = strictCatalogTimestamp(existing)
            check(existingSavedAtMs <= catalog.savedAtMs) {
                "แคตตาล็อก POS ใหม่กว่าข้อมูลที่กำลังบันทึก"
            }
            // Equal timestamps may only be replayed with identical data.
            check(existingSavedAtMs != catalog.savedAtMs || existing.similar(json)) {
                "แคตตาล็อก POS มีข้อมูลขัดแย้งในเวลาเดียวกัน"
            }
            // Validate the complete persisted snapshot before replacing it. Bounds and metadata
            // checks alone miss corrupted prices, stock, table data and product option groups.
            loadCatalog(catalog.actorId, catalog.deviceId)
            // Do not silently replace a malformed persisted catalog before it can be inspected.
            check(existing.getJSONArray("products").length() <= 20_000 &&
                existing.getJSONArray("tables").length() <= 300) {
                "แคตตาล็อก POS ออฟไลน์ไม่ถูกต้อง"
            }
        }
        atomicWrite(target, bytes)
    }

    @Synchronized fun loadCatalog(actorId: String, deviceId: String): PosOfflineCatalog? {
        requireUuid(actorId); requireUuid(deviceId)
        val file = catalogFile(actorId, deviceId)
        if (!file.exists()) return null
        check(file.length() <= MAX_CATALOG_BYTES) { "แคตตาล็อก POS ออฟไลน์ไม่ถูกต้อง" }
        val json = JSONObject(file.readText())
        val savedAtMs = strictCatalogTimestamp(json)
        val storedActor = json.getString("actor_id")
        val storedDevice = json.getString("device_id")
        check(storedActor == actorId && storedDevice == deviceId) { "แคตตาล็อก POS ไม่ตรงกับบัญชีหรืออุปกรณ์นี้" }
        // Fail closed on corrupted local catalog metadata before allocating product/table lists.
        requireUuid(json.getString("shop_id"))
        val productArray = json.getJSONArray("products")
        val tableArray = json.getJSONArray("tables")
        check(productArray.length() <= 20_000 && tableArray.length() <= 300) {
            "แคตตาล็อก POS ออฟไลน์ไม่ถูกต้อง"
        }
        val products = productArray.let { array -> (0 until array.length()).map { i ->
            val p = array.getJSONObject(i)
            val price = strictCatalogPrice(p, "price")
            val deliveryPrice = if (!p.has("delivery_price") || p.isNull("delivery_price")) null
                else strictCatalogPrice(p, "delivery_price")
            // optInt silently converts malformed strings/fractions to zero or truncates stock.
            // Preserve zero for older snapshots without this field, but reject corrupt values.
            val stock = when (val raw = p.opt("stock")) {
                null -> 0
                is Int -> raw
                is Long -> {
                    check(raw in 0..Int.MAX_VALUE.toLong()) { "จำนวนสินค้า POS ออฟไลน์ไม่ถูกต้อง" }
                    raw.toInt()
                }
                else -> error("จำนวนสินค้า POS ออฟไลน์ไม่ถูกต้อง")
            }
            check(p.getString("id").isNotBlank() && p.getString("name").isNotBlank() &&
                p.getString("name").length <= 200 && price.isFinite() && price >= 0 &&
                (deliveryPrice == null || (deliveryPrice.isFinite() && deliveryPrice >= 0)) &&
                stock >= 0) {
                "ราคาสินค้า POS ออฟไลน์ไม่ถูกต้อง"
            }
            PosProduct(p.getString("id"), p.getString("name"), price,
                if (p.isNull("image")) null else p.getString("image"), strictCatalogBoolean(p, "available"),
                strictOptionalCatalogBoolean(p, "delivery_available", true), deliveryPrice,
                stock = stock,
                inventoryStockTracking = strictOptionalCatalogBoolean(p, "inventory_stock_tracking", false),
                groceryMasterId = if (p.isNull("grocery_master_id")) null else p.optString("grocery_master_id"),
                groceryStockTracking = strictOptionalCatalogBoolean(p, "grocery_stock_tracking", false),
                optionGroups = parsePosProductOptionGroups(when (val groups = p.opt("option_groups")) {
                    null -> JSONArray() // Backward compatibility for catalogs saved before option groups existed.
                    is JSONArray -> groups
                    else -> error("กลุ่มตัวเลือกสินค้า POS ออฟไลน์ไม่ถูกต้อง")
                }),
                category = if (!p.has("category") || p.isNull("category")) null else p.optString("category"))
        } }
        val tables = tableArray.let { array -> (0 until array.length()).map { i ->
            val t = array.getJSONObject(i)
            check(t.getString("id").isNotBlank() && t.getString("label").isNotBlank() &&
                t.getString("label").length <= 100) { "ข้อมูลโต๊ะ POS ออฟไลน์ไม่ถูกต้อง" }
            PosTable(t.getString("id"), t.getString("label"), strictCatalogBoolean(t, "active"),
                if (t.isNull("qr_token")) null else t.getString("qr_token"))
        } }
        check(products.map { it.id }.distinct().size == products.size &&
            tables.map { it.id }.distinct().size == tables.size) {
            "แคตตาล็อก POS ออฟไลน์มีรหัสสินค้าหรือโต๊ะซ้ำกัน"
        }
        // Persisted catalog metadata may have been tampered with after the atomic write.
        // Do not expose oversized or blank shop names as trusted offline UI data.
        val shopName = json.optString("shop_name", "ร้านค้า")
        check(shopName.isNotBlank() && shopName.length <= 200) { "ชื่อร้านค้า POS ออฟไลน์ไม่ถูกต้อง" }
        return PosOfflineCatalog(actorId, json.getString("shop_id"), deviceId,
            shopName, savedAtMs, products, tables)
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

    private fun strictCatalogBoolean(json: JSONObject, key: String): Boolean =
        (json.get(key) as? Boolean) ?: error("ข้อมูลสถานะ POS ออฟไลน์ไม่ถูกต้อง: $key")

    private fun strictOptionalCatalogBoolean(json: JSONObject, key: String, default: Boolean): Boolean =
        if (json.has(key)) strictCatalogBoolean(json, key) else default

    private fun strictCatalogPrice(json: JSONObject, key: String): Double {
        // JSONObject.getDouble accepts numeric strings; persisted catalog prices must be JSON numbers.
        val raw = json.opt(key)
        check(raw is Number) { "ราคาสินค้า POS ออฟไลน์ไม่ถูกต้อง: $key" }
        return raw.toDouble().also {
            check(it.isFinite() && it >= 0) { "ราคาสินค้า POS ออฟไลน์ไม่ถูกต้อง: $key" }
        }
    }

    private fun strictDraftPrice(json: JSONObject, key: String): Double {
        // Reject strings, booleans and null rather than allowing JSONObject.getDouble coercion.
        val raw = json.opt(key)
        check(raw is Number) { "ราคาสินค้าร่าง POS ไม่ถูกต้อง: $key" }
        return raw.toDouble().also {
            check(it.isFinite() && it >= 0) { "ราคาสินค้าร่าง POS ไม่ถูกต้อง: $key" }
        }
    }

    private fun strictCatalogTimestamp(json: JSONObject): Long {
        // JSONObject.getLong silently truncates fractional values and coerces numeric strings.
        return when (val raw = json.opt("saved_at_ms")) {
            is Int -> raw.toLong().also { check(it >= 0) { "เวลาแคตตาล็อก POS ไม่ถูกต้อง" } }
            is Long -> raw.also { check(it >= 0) { "เวลาแคตตาล็อก POS ไม่ถูกต้อง" } }
            else -> error("เวลาแคตตาล็อก POS ไม่ถูกต้อง")
        }
    }

    private fun encode(d: PosOfflineDraft) = JSONObject()
        .put("id", d.id).put("request_id", d.requestId).put("actor_id", d.actorId)
        .put("shop_id", d.shopId).put("device_id", d.deviceId).put("type", d.type)
        .put("table_id", d.tableId ?: JSONObject.NULL).put("saved_at_ms", d.savedAtMs)
        .put("items", JSONArray().apply { d.items.forEach { i ->
            put(JSONObject().put("product_id", i.productId).put("name", i.nameSnapshot)
                .put("price", i.priceSnapshot).put("quantity", i.quantity)
                .put("base_price", i.basePriceSnapshot).put("options", JSONObject(i.optionsJson))
                .put("option_label", i.optionLabel).put("request_ids", JSONArray(i.requestIds)))
        } })

    private fun decode(j: JSONObject): PosOfflineDraft {
        val items = j.getJSONArray("items")
        check(items.length() in 1..100) { "จำนวนรายการร่าง POS ไม่ถูกต้อง" }
        return PosOfflineDraft(j.getString("id"), j.getString("request_id"), j.getString("actor_id"),
            j.getString("shop_id"), j.getString("device_id"), j.getString("type"),
            if (j.isNull("table_id")) null else j.getString("table_id"), strictCatalogTimestamp(j),
            (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                val requests = item.getJSONArray("request_ids")
                check(requests.length() <= 99) { "จำนวนรหัสคำขอร่าง POS ไม่ถูกต้อง" }
                val rawQuantity = item.get("quantity")
                val quantity = when (rawQuantity) {
                    is Int -> rawQuantity
                    is Long -> {
                        check(rawQuantity in 1..99) { "จำนวนสินค้าร่าง POS ไม่ถูกต้อง" }
                        rawQuantity.toInt()
                    }
                    else -> error("จำนวนสินค้าร่าง POS ไม่ถูกต้อง")
                }
                PosOfflineDraftItem(item.getString("product_id"), item.getString("name"),
                    strictDraftPrice(item, "price"), quantity,
                    (0 until requests.length()).map(requests::getString),
                    basePriceSnapshot = if (item.has("base_price")) strictDraftPrice(item, "base_price") else strictDraftPrice(item, "price"),
                    optionsJson = canonicalPosOptionJson((item.optJSONObject("options") ?: JSONObject()).toString()),
                    optionLabel = item.optString("option_label", ""))
            })
    }

    private fun validate(d: PosOfflineDraft) {
        require(d.savedAtMs >= 0) { "เวลาร่าง POS ไม่ถูกต้อง" }
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
            require(item.basePriceSnapshot.isFinite() && item.basePriceSnapshot >= 0)
            require(item.optionsJson.length <= 5000 && canonicalPosOptionJson(item.optionsJson) == item.optionsJson)
            require(item.optionLabel.length <= 1000)
            require(item.quantity in 1..99)
            require(item.requestIds.size in setOf(0, item.quantity))
            item.requestIds.forEach(::requireUuid)
            require(item.requestIds.toSet().size == item.requestIds.size)
        }
        // Each replay UUID must identify exactly one mutation, including the bill creation.
        val replayIds = d.items.flatMap { it.requestIds }
        require(replayIds.size == replayIds.toSet().size && d.requestId !in replayIds)
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
