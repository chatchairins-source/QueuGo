package com.queuetech.queuego.rider

import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object RiderServerTime {
    private val longFraction = Regex("""(\.\d{3})\d+(?=(?:Z|[+-]\d{2}:\d{2})$)""")

    fun parseMillis(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        val normalized = trimmed.replace(longFraction, "$1")

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
        )
        for (pattern in patterns) {
            val parser = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                if (pattern.endsWith("'Z'")) timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsed = runCatching { parser.parse(normalized) }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }
}

object RiderActionIds {
    fun stable(userId: String, kind: String, key: String): String {
        val input = "queuego-rider|$userId|$kind|$key"
        return UUID.nameUUIDFromBytes(input.toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
