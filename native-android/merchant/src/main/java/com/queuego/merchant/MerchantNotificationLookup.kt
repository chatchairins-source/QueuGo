package com.queuego.merchant

// A transport failure is not evidence that the referenced order is missing.
// The refresh supplied by the caller must stay scoped to the current authenticated shop.
internal data class MerchantNotificationLookup<T : Any>(
    val order: T?,
    val snapshot: List<T>
)

internal suspend fun <T : Any> lookupMerchantNotificationOrder(
    referenceId: String,
    current: List<T>,
    idOf: (T) -> String,
    refresh: suspend () -> List<T>
): MerchantNotificationLookup<T> {
    val cleanReference = referenceId.trim()
    if (cleanReference.isBlank()) return MerchantNotificationLookup(null, current)

    current.firstOrNull { idOf(it) == cleanReference }?.let {
        return MerchantNotificationLookup(it, current)
    }

    // Intentionally let transport/auth/cancellation failures escape so callers can retry.
    val fresh = refresh()
    return MerchantNotificationLookup(
        order = fresh.firstOrNull { idOf(it) == cleanReference },
        snapshot = fresh
    )
}
