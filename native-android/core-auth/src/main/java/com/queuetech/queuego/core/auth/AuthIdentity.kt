package com.queuetech.queuego.core.auth

object AuthIdentity {
    fun toSupabaseEmail(identifier: String): String {
        val clean = identifier.trim()
        require(clean.isNotBlank()) { "กรุณากรอกเบอร์โทรศัพท์หรืออีเมล" }

        if (clean.contains("@")) return clean.lowercase()

        val digits = clean.filter(Char::isDigit)
        require(digits.isNotBlank()) { "กรุณากรอกเบอร์โทรศัพท์หรืออีเมล" }
        return "$digits@auth.queuetech.local"
    }
}
