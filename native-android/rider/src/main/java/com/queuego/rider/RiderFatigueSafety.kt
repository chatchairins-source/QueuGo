package com.queuego.rider

internal fun riderFatigueDurationLabel(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    val hours = safe / 60
    val remaining = safe % 60
    return when {
        hours > 0 && remaining > 0 -> "$hours ชม. $remaining นาที"
        hours > 0 -> "$hours ชม."
        else -> "$remaining นาที"
    }
}

internal fun riderFatigueTitle(level: String): String = when (level) {
    "strong_rest_recommended" -> "ควรพักก่อนรับงานถัดไป"
    "rest_recommended" -> "แนะนำให้พักเมื่อสะดวก"
    else -> "เวลาทำงานอยู่ในช่วงปกติ"
}

internal fun riderFatigueDetail(level: String): String = when (level) {
    "strong_rest_recommended" ->
        "คุณออนไลน์ต่อเนื่องมานานแล้ว หลังจบงานปัจจุบันควรปิดรับงานและพักก่อนเริ่มรอบใหม่"
    "rest_recommended" ->
        "คุณออนไลน์ต่อเนื่องเกิน 2 ชั่วโมง หากจบงานหรืออยู่ในช่วงว่างควรพักก่อนรับงานถัดไป"
    else ->
        "QueueGo จะเตือนเมื่อออนไลน์ต่อเนื่องนานขึ้น โดยไม่ตัดงานที่กำลังส่งอยู่"
}
