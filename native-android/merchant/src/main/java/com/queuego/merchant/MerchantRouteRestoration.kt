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

/**
 * One back-navigation policy for every Merchant destination.
 * Child/detail screens return to their owning section; primary sections return home.
 * Keep this policy in sync with explicit onBack callbacks and cover it with unit tests.
 */
internal fun merchantBackRoute(route: String, productReturnRoute: String = "products"): String = when (route) {
    "home" -> "home"
    "shop-setup", "hours", "support", "order-report" -> "profile"
    "notifications", "media", "modules", "market-stock", "pos", "laundry",
    "revenue", "promotions", "orders", "products", "profile" -> "home"
    "catalog", "grocery-catalog", "market-catalog" -> "products"
    "product" -> productReturnRoute.takeIf { it in setOf("products", "catalog", "market-catalog", "grocery-catalog") } ?: "products"
    "order" -> "orders"
    else -> "home"
}
