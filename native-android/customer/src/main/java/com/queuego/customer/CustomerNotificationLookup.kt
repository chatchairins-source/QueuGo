package com.queuego.customer

import kotlinx.coroutines.CancellationException

// All readers must use the current authenticated owner and an exact reference ID.
// A failed read is not evidence that an order does not exist.
internal suspend fun <T : Any> lookupCustomerNotification(
    readers: List<suspend () -> T?>
): T? {
    var failure: Exception? = null
    for (read in readers) {
        try {
            read()?.let { return it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (failure == null) failure = e
        }
    }
    failure?.let { throw it }
    return null
}

internal sealed interface CustomerNotificationDestination {
    data class Order(val order: CustomerOrder) : CustomerNotificationDestination
    data class Market(val trip: MarketTripSummary) : CustomerNotificationDestination
    data class Laundry(val order: LaundryOrderSummary) : CustomerNotificationDestination
}
