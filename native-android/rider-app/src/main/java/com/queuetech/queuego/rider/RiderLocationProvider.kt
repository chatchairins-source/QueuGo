package com.queuetech.queuego.rider

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class RiderLocationProvider(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPrecisePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(): RiderCoordinate = suspendCancellableCoroutine { continuation ->
        if (!hasPrecisePermission()) {
            continuation.resumeWithException(
                SecurityException("QueueGo Rider ต้องใช้ตำแหน่งแบบแม่นยำเพื่อเปิดรับงาน"),
            )
            return@suspendCancellableCoroutine
        }

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (provider == null) {
            continuation.resumeWithException(
                IllegalStateException("กรุณาเปิดบริการตำแหน่งของโทรศัพท์"),
            )
            return@suspendCancellableCoroutine
        }

        fun deliver(location: Location?) {
            if (!continuation.isActive) return
            if (location == null) {
                continuation.resumeWithException(
                    IllegalStateException("ยังหาตำแหน่งปัจจุบันไม่ได้ กรุณาลองอีกครั้ง"),
                )
            } else {
                continuation.resume(
                    RiderCoordinate(
                        latitude = location.latitude,
                        longitude = location.longitude,
                    ),
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            val executor = java.util.concurrent.Executor { command ->
                Handler(Looper.getMainLooper()).post(command)
            }
            locationManager.getCurrentLocation(provider, signal, executor, ::deliver)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    deliver(location)
                }

                @Deprecated("Deprecated in Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
            @Suppress("DEPRECATION")
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }
    }
}
