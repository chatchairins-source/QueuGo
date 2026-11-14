package com.queuego.customer

/** Resolves the same page-specific parent destinations used by Customer Web back controls. */
internal fun customerSystemBackTarget(
    screen: String,
    shopReturnScreen: String,
    cartReturnScreen: String,
    ordersReturnScreen: String,
    locationReturnScreen: String,
    promotionReturnScreen: String,
    supportHasOrder: Boolean,
    hasSelectedOrder: Boolean,
): String? = when (screen) {
    "home" -> null
    "favorites" -> "profile"
    "market-cart" -> "market"
    "checkout" -> "cart"
    "shop" -> shopReturnScreen
    "cart" -> cartReturnScreen
    "orders" -> ordersReturnScreen
    "location" -> locationReturnScreen
    "promotion" -> promotionReturnScreen
    "order", "market-trip", "laundry-order" -> "orders"
    "chat" -> if (hasSelectedOrder) "order" else "orders"
    "support" -> if (supportHasOrder && hasSelectedOrder) "order" else "profile"
    else -> "home"
}
