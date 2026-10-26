package com.queuego.merchant

/** Restore reloadable destinations without reopening editors backed by lost selections. */
internal fun merchantRestorableRoute(route: String): String = when (route) {
    "order" -> "orders"
    "order-report" -> "profile"
    "product", "catalog", "grocery-catalog", "market-catalog" -> "products"
    "hours", "shop-setup" -> "profile"
    "home", "orders", "products", "pos", "laundry", "revenue", "promotions",
    "notifications", "market-stock", "media", "modules", "support", "profile" -> route
    else -> "home"
}
