package com.queuego.rider

import com.queuego.shared.NativeChatOutbox
import com.queuego.shared.sendNativeChatOnce

internal typealias RiderChatOutbox = NativeChatOutbox
internal suspend fun sendRiderChatOnce(exists: suspend () -> Boolean, insert: suspend () -> Boolean) =
    sendNativeChatOnce(exists, insert)

internal fun validateRiderChatPayload(raw: String): String = com.queuego.shared.validateNativeChatPayload(raw)

internal val riderChatReportReasons = linkedMapOf(
    "harassment" to "คุกคาม / กลั่นแกล้ง", "inappropriate" to "เนื้อหาไม่เหมาะสม",
    "spam" to "สแปม", "fraud" to "หลอกลวง / ฉ้อโกง", "safety" to "ความปลอดภัย", "other" to "อื่น ๆ"
)
