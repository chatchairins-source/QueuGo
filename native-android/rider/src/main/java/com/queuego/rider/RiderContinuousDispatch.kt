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

/** A navigation foreground service may renew a lease only with a live bound job/session and fresh GPS. */
internal fun riderNavigationDispatchLeaseEligible(
    ready: Boolean, autoAccept: Boolean, stopAfterWork: Boolean, hasBoundJob: Boolean,
    tokenRemainingMs: Long, fixAgeMs: Long?
): Boolean = ready && autoAccept && !stopAfterWork && hasBoundJob &&
    tokenRemainingMs > 60_000L && fixAgeMs != null && fixAgeMs in 0L..15_000L
