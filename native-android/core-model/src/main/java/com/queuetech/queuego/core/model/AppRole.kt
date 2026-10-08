package com.queuetech.queuego.core.model

enum class AppRole(val backendValue: String, val displayName: String) {
    CUSTOMER("customer", "ลูกค้า"),
    MERCHANT("shop", "ร้านค้า"),
    RIDER("rider", "ไรเดอร์");

    companion object {
        fun fromBackend(value: String): AppRole? = entries.firstOrNull { it.backendValue == value }
    }
}
