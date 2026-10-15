package com.queuego.rider

/** Called only after the existing photo/cash completion RPC acknowledges this exact order. */
internal fun riderAfterDeliveryCommit(previous: RiderSnapshot, completedOrderId: String): RiderSnapshot {
    if (previous.activeJob?.id != completedOrderId &&
        previous.bundle.activeOrders.none { it.job.id == completedOrderId }) return previous
    val bundleAfter = previous.bundle.copy(
        activeOrders = previous.bundle.activeOrders.map { bundled ->
            if (bundled.job.id == completedOrderId) bundled.copy(job = bundled.job.copy(status = "completed")) else bundled
        }
    )
    val queued = previous.nextJob?.takeIf {
        it.id != completedOrderId && it.status !in setOf("completed", "cancelled", "no_rider_available")
    }
    val promoted = bundleAfter.currentOrder()?.job ?: queued
    return previous.copy(
        activeJob = promoted,
        nextJob = queued?.takeUnless { it.id == promoted?.id },
        marketPickups = emptyList(),
        bundle = bundleAfter
    )
}
