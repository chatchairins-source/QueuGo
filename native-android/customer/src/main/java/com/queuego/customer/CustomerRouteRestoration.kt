package com.queuego.customer

/** Only restore pages whose data can be reloaded without an in-memory selection. */
internal fun customerRestorableRoute(route: String): String = when (route) {
    "order", "chat", "market-trip", "laundry-order" -> "orders"
    "support" -> "profile"
    "home", "search", "category", "shopping", "cart", "checkout", "orders",
    "location", "promotion", "market", "market-cart", "laundry", "ride", "express",
    "profile", "favorites", "notifications" -> route
    else -> "home"
}
