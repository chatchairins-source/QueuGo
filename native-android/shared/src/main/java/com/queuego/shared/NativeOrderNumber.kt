package com.queuego.shared

import java.util.Locale

/** Production queuego-order-number.js formatting; never derives a visible code from an order UUID. */
fun nativeOrderNumber(raw: String?): String {
    val clean = raw.orEmpty().trim().uppercase(Locale.ROOT)
    val legacy = Regex("^(?:POS|QR)-([0-9A-F]{4,})$").matchEntire(clean)
    if (legacy != null) return "QT-" + (legacy.groupValues[1].takeLast(4).toInt(16) % 10000).toString().padStart(4, '0')
    val match = listOf(
        Regex("^(?:QT|QO)-[0-9]{8}-([A-Z0-9]{1,4})$"),
        Regex("^(?:QT|QO)-([A-Z0-9]{1,4})$"),
        Regex("^([0-9]{1,4})$"),
        Regex("^LW-[0-9]{8}-[0-9]{6}-([A-Z0-9]{4})$")
    ).firstNotNullOfOrNull { it.matchEntire(clean) } ?: return "QT-----"
    val code = match.groupValues[1]
    val numeric = when {
        clean.startsWith("LW-") && Regex("^[0-9A-F]{4}$").matches(code) -> code.toInt(16) % 10000
        Regex("^[0-9]+$").matches(code) -> code.toInt()
        else -> code.toInt(36) % 10000
    }
    return "QT-" + numeric.toString().padStart(4, '0')
}
