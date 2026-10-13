package com.queuego.rider

/** Both automatic Maps handoff and the rendered action must use exactly the same stage key. */
internal fun riderNavigationKey(job: RiderJob): String =
    job.id + if (job.isDelivering) ":customer" else ":shop"

internal fun riderCollectionReady(amount: Double?, photoReady: Boolean, previewReady: Boolean): Boolean =
    photoReady && previewReady && amount != null && amount.isFinite() && amount >= 0.0
