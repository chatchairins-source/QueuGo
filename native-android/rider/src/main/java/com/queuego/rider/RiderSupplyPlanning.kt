package com.queuego.rider

internal fun riderSupplyDayLabel(day: Int): String = when (day) {
    1 -> "จันทร์"
    2 -> "อังคาร"
    3 -> "พุธ"
    4 -> "พฤหัสบดี"
    5 -> "ศุกร์"
    6 -> "เสาร์"
    7 -> "อาทิตย์"
    else -> "ไม่ทราบวัน"
}

internal fun riderSupplyWindowLabel(window: RiderSupplyWindow): String =
    riderSupplyDayLabel(window.dayOfWeek) + " · " +
        window.startHour.toString().padStart(2, '0') + ":00–" +
        window.endHour.toString().padStart(2, '0') + ":00"

internal fun riderSupplyLevelLabel(level: Int): String = when (level) {
    3 -> "คึกคักมาก"
    2 -> "คึกคัก"
    else -> "มีแนวโน้มงาน"
}

internal fun riderSupplyConfidenceLabel(confidence: String): String = when (confidence) {
    "high" -> "ข้อมูลย้อนหลังมาก"
    "medium" -> "ข้อมูลย้อนหลังปานกลาง"
    else -> "ข้อมูลย้อนหลังยังน้อย"
}
