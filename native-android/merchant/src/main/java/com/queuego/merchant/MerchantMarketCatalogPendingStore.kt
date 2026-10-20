package com.queuego.merchant

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.json.JSONObject

/** Same atomic no-backup file convention as MerchantPosPendingStore; one request per actor/shop. */
internal class MerchantMarketCatalogPendingStore(private val root: File, private val actor: String, private val shop: String) {
    init { require(marketCatalogUuid(actor) && marketCatalogUuid(shop)) }
    private val file: File get() {
        val key = MessageDigest.getInstance("SHA-256").digest("$actor:$shop".toByteArray()).joinToString("") { "%02x".format(it) }
        return File(root, "$key.json")
    }
    @Synchronized fun load(): MarketCatalogIntent? {
        if (!file.exists()) return null
        check(file.length() <= 16 * 1024) { "ข้อมูลสินค้าเพิ่มค้างไม่ถูกต้อง" }
        val json = JSONObject(file.readText())
        check(json.getString("actor") == actor && json.getString("shop") == shop) { "คำขอสินค้าไม่ตรงบัญชีหรือร้าน" }
        return MarketCatalogIntent.fromBody(json.getJSONObject("body"))
    }
    @Synchronized fun prepare(intent: MarketCatalogIntent): MarketCatalogIntent {
        intent.validate()
        load()?.let {
            check(it.copy(requestId = intent.requestId) == intent) { "ยังมีคำขอเดิมที่ไม่ทราบผล กดลองโหลดรายการค้างก่อน" }
            return it
        }
        check(root.isDirectory || root.mkdirs()) { "บันทึกคำขอสินค้าไม่สำเร็จ" }
        val temporary = File.createTempFile("market-add-", ".tmp", root)
        try {
            val bytes = JSONObject().put("actor", actor).put("shop", shop).put("body", intent.body()).toString().toByteArray(Charsets.UTF_8)
            FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally { temporary.delete() }
        return intent
    }
    @Synchronized fun clear(request: String) {
        val pending = load() ?: return
        check(pending.requestId == request) { "คำขอสินค้าเปลี่ยนแล้ว" }
        check(file.delete()) { "ล้างคำขอสินค้าที่บันทึกแล้วไม่สำเร็จ กดลองโหลดอีกครั้ง" }
    }
}
