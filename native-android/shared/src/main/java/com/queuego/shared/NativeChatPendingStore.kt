package com.queuego.shared

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject

data class NativeChatPending(val id: String, val payload: String)

/** App-private, no-backup root supplied by the caller. Never sends automatically. */
class NativeChatPendingStore(private val root: File, private val userId: String, private val orderId: String) {
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private val file = File(File(root, hash(userId)), hash(orderId) + ".json")

    @Synchronized fun load(): NativeChatPending? {
        if (!file.exists()) return null
        check(file.length() <= 8 * 1024 * 1024) { "ข้อความค้างส่งใหญ่เกินไป" }
        val json = JSONObject(file.readText())
        check(json.getString("sender_id") == userId && json.getString("order_id") == orderId) {
            "ข้อมูลข้อความค้างส่งไม่ตรงกับบัญชีหรือออเดอร์"
        }
        val id = json.getString("id")
        require(UUID.fromString(id).toString() == id)
        return NativeChatPending(id, validateNativeChatPayload(json.getString("message")))
    }

    /** Existing pending payload wins over newly typed text, exactly as the Customer web form. */
    @Synchronized fun prepare(payload: String): NativeChatPending {
        load()?.let { return it }
        val pending = NativeChatPending(UUID.randomUUID().toString(), validateNativeChatPayload(payload))
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "บันทึกข้อความค้างส่งไม่สำเร็จ" }
        val temporary = File.createTempFile("pending-", ".tmp", file.parentFile)
        try {
            val bytes = JSONObject().put("id", pending.id).put("sender_id", userId)
                .put("order_id", orderId).put("message", pending.payload).toString().toByteArray(Charsets.UTF_8)
            FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally { temporary.delete() }
        return pending
    }

    @Synchronized fun clear() {
        check(!file.exists() || file.delete()) { "ล้างข้อความค้างส่งไม่สำเร็จ" }
    }
}

fun nativeChatPermanentFailure(statusCode: Int) = statusCode in 400..499 && statusCode !in setOf(408, 409, 429)
