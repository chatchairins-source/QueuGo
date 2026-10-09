package com.queuego.rider

import com.queuego.shared.NativeChatOutbox
import com.queuego.shared.sendNativeChatOnce

internal typealias RiderChatOutbox = NativeChatOutbox
internal suspend fun sendRiderChatOnce(exists: suspend () -> Boolean, insert: suspend () -> Boolean) =
    sendNativeChatOnce(exists, insert)

internal fun validateRiderChatPayload(raw: String): String {
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

internal val riderChatReportReasons = linkedMapOf(
    "harassment" to "คุกคาม / กลั่นแกล้ง", "inappropriate" to "เนื้อหาไม่เหมาะสม",
    "spam" to "สแปม", "fraud" to "หลอกลวง / ฉ้อโกง", "safety" to "ความปลอดภัย", "other" to "อื่น ๆ"
)
