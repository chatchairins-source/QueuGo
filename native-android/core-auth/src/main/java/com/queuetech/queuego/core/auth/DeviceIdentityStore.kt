package com.queuetech.queuego.core.auth

import android.content.Context
import java.util.UUID

class DeviceIdentityStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getOrCreate(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).commit()
        return created
    }

    private companion object {
        const val PREFS_NAME = "queuego_device_identity_v1"
        const val KEY_DEVICE_ID = "device_id"
    }
}
