package com.queuego.rider

import android.content.Context

internal class RiderStopAfterWorkStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("rider_stop_after_work_v1", Context.MODE_PRIVATE)

    private fun key(userId: String) = "pending:" + userId

    fun pending(userId: String): Boolean =
        prefs.getBoolean(key(userId), false)

    fun setPending(userId: String, pending: Boolean) {
        prefs.edit().putBoolean(key(userId), pending).apply()
    }
}

internal fun riderStopAfterWorkReady(
    pending: Boolean,
    networkAvailable: Boolean,
    online: Boolean,
    hasActiveJob: Boolean,
    hasLaundryJob: Boolean,
    hasDeliveryReturn: Boolean,
    hasOffer: Boolean,
    actionBusy: Boolean
): Boolean =
    pending &&
        networkAvailable &&
        online &&
        !hasActiveJob &&
        !hasLaundryJob &&
        !hasDeliveryReturn &&
        !hasOffer &&
        !actionBusy

internal fun riderAutoAcceptAllowedWhileStopping(
    autoAcceptEnabled: Boolean,
    stopAfterWorkPending: Boolean
): Boolean = autoAcceptEnabled && !stopAfterWorkPending
