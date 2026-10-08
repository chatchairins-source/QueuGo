package com.queuetech.queuego.core.model

data class QueueGoShop(
    val id: String,
    val name: String,
    val category: String,
    val subcategories: List<String>,
    val description: String,
    val address: String,
    val logoUrl: String,
    val coverUrl: String,
    val latitude: Double?,
    val longitude: Double?,
    val deliveryEnabled: Boolean,
    val isOpen: Boolean,
)

data class QueueGoProduct(
    val id: String,
    val shopId: String,
    val name: String,
    val description: String,
    val price: Double,
    val deliveryPrice: Double?,
    val imageUrl: String,
    val available: Boolean,
    val deliveryAvailable: Boolean,
) {
    val effectivePrice: Double
        get() = (deliveryPrice ?: price).coerceAtLeast(0.0)
}

data class QueueGoCartItem(
    val productId: String,
    val shopId: String,
    val name: String,
    val price: Double,
    val imageUrl: String,
    val quantity: Int,
)

data class QueueGoCustomerCart(
    val shopId: String? = null,
    val items: List<QueueGoCartItem> = emptyList(),
) {
    val count: Int get() = items.sumOf { it.quantity }
    val total: Double get() = items.sumOf { it.price * it.quantity }
}
