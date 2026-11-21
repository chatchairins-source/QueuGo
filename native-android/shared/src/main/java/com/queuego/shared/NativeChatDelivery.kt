package com.queuego.shared

import kotlinx.coroutines.CancellationException
import java.util.UUID
import java.security.MessageDigest

/** Retain the same ID after an ambiguous response; never insert a second message on retry. */
class NativeChatOutbox {
    private val requests = mutableMapOf<String, String>()
    private fun key(payload: String): String = MessageDigest.getInstance("SHA-256")
        .digest(payload.trim().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun requestId(payload: String): String = requests.getOrPut(key(payload)) { UUID.randomUUID().toString() }
    fun confirmed(payload: String) { requests.remove(key(payload)) }
}

suspend fun sendNativeChatOnce(exists: suspend () -> Boolean, insert: suspend () -> Boolean) {
    if (exists()) return
    try {
        if (insert()) return
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        if (exists()) return
        throw failure
    }
    check(exists()) { "ยังไม่ได้รับผลยืนยันข้อความ" }
}

fun validateNativeChatPayload(raw: String): String {
    val clean = raw.trim()
    require(clean.isNotBlank()) { "กรุณาพิมพ์ข้อความ" }
    if (clean.startsWith("__IMG__")) {
        require(clean.startsWith("__IMG__data:image/jpeg;base64,") && clean.length <= 7 * 1024 * 1024) {
            "รูปภาพไม่ถูกต้องหรือใหญ่เกินไป"
        }
        require(clean.substringAfter("base64,").isNotBlank()) { "รูปภาพไม่ถูกต้อง" }
    } else require(clean.length <= 500) { "ข้อความยาวเกิน 500 ตัวอักษร" }
    return clean
}

