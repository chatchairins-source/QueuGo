package com.queuego.rider

/** Exact Production report RPC format; never include a chat-message image marker. */
internal fun riderReportEvidence(payload: String): String {
    val data = payload.removePrefix("__IMG__")
    require(data.length <= 900_000) { "ภาพแคปหน้าจอมีขนาดใหญ่เกินไป กรุณาครอปภาพแล้วลองอีกครั้ง" }
    require(Regex("^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$").matches(data)) {
        "หลักฐานต้องเป็นรูป JPG, PNG หรือ WebP"
    }
    return data
}
