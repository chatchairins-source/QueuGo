package com.queuego.rider

/** Both automatic Maps handoff and the rendered action must use exactly the same stage key. */
internal fun riderNavigationKey(job: RiderJob): String =
    if (job.isDelivering) job.id + ":customer"
    else if (job.marketOrderId != null) job.id + ":shop:" + job.pickupLat + ":" + job.pickupLng
    else job.id + ":shop"

internal fun riderCollectionReady(amount: Double?, photoReady: Boolean, previewReady: Boolean): Boolean =
    photoReady && previewReady && amount != null && amount.isFinite() && amount >= 0.0
