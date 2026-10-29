package com.queuego.customer

fun customerProfileContact(phone: String?, email: String?): String? =
    phone?.trim()?.takeIf { it.isNotBlank() }
        ?: email?.trim()?.takeIf {
            it.isNotBlank() && !it.endsWith("@auth.queuetech.local", ignoreCase = true)
        }
