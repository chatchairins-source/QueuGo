package com.queuego.rider

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Locale

internal data class RiderSafetyLocation(
    val latitude: Double,
    val longitude: Double
)

internal fun riderSafetyLocationOrNull(
    latitude: Double,
    longitude: Double
): RiderSafetyLocation? =
    if (
        latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0
    ) {
        RiderSafetyLocation(latitude, longitude)
    } else {
        null
    }

internal fun riderSafetyLocationUrl(location: RiderSafetyLocation): String =
    String.format(
        Locale.US,
        "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f",
        location.latitude,
        location.longitude
    )

internal fun riderSafetyLocationShareText(location: RiderSafetyLocation): String =
    "QueueGo Rider — ตำแหน่งปัจจุบันของฉัน\n" +
        riderSafetyLocationUrl(location) +
        "\nตำแหน่งนี้เป็นจุดที่แชร์ในขณะนี้ ไม่ใช่การติดตามแบบเรียลไทม์"

internal fun riderSafetyLastKnownLocation(context: Context): RiderSafetyLocation? {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) return null

    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val now = System.currentTimeMillis()
    return runCatching {
        manager.getProviders(true)
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter { location ->
                location.latitude.isFinite() &&
                    location.longitude.isFinite() &&
                    location.latitude in -90.0..90.0 &&
                    location.longitude in -180.0..180.0 &&
                    (!location.hasAccuracy() || location.accuracy <= 200f) &&
                    (now - location.time) in 0L..300_000L
            }
            .maxByOrNull { it.time }
            ?.let { riderSafetyLocationOrNull(it.latitude, it.longitude) }
    }.getOrNull()
}
