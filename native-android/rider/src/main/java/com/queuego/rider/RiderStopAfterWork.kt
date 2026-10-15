package com.queuego.rider

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class RiderStopAfterWorkStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("rider_stop_after_work_v1", Context.MODE_PRIVATE)

    private fun key(userId: String) = "pending:" + userId

    suspend fun pending(userId: String): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(key(userId), false)
    }

    suspend fun setPending(userId: String, pending: Boolean) = withContext(Dispatchers.IO) {
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
