package com.queuego.shared

/** Mirrors Production pos_allowed: explicit false overrides the role default. */
fun nativePosPermissionAllowed(
    active: Boolean,
    role: String,
    permission: String,
    grants: Set<String>,
    denials: Set<String>
): Boolean {
    if (!active || permission in denials) return false
    if (permission in grants) return true
    return when (permission) {
        "receive_order", "send_kitchen", "serve_order" -> role == "WAITER"
        "cook_order", "ready_order" -> role == "KITCHEN"
        "close_bill" -> role == "CASHIER"
        else -> false
    }
}
