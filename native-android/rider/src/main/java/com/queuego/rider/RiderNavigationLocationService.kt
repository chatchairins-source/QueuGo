package com.queuego.rider

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.queuego.ridernavigationmap.NavigationMapFix
import com.queuego.ridernavigationmap.NavigationMapPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

private const val NAVIGATION_LOCATION_CHANNEL_ID = "queuego_rider_navigation_location"
private const val NAVIGATION_LOCATION_NOTIFICATION_ID = 4112
private const val NAVIGATION_LOCATION_INTERVAL_MS = 1_000L
private const val NAVIGATION_LOCATION_MIN_INTERVAL_MS = 500L
private const val NAVIGATION_LOCATION_MIN_DISTANCE_METERS = 1f
private const val NAVIGATION_MAX_ACCURACY_METERS = 80f
private const val NAVIGATION_MAX_REASONABLE_SPEED_MPS = 55f
private const val NAVIGATION_STALE_LAST_FIX_MS = 30_000L

internal object RiderNavigationLocationStore {
    private val mutableFix = MutableStateFlow<NavigationMapFix?>(null)
    val fix: StateFlow<NavigationMapFix?> = mutableFix.asStateFlow()

    fun publish(next: NavigationMapFix) {
        mutableFix.value = next
    }

    fun clear() {
        mutableFix.value = null
    }
}

class RiderNavigationLocationService : Service() {
    companion object {
        fun start(context: Context) {
            val appContext = context.applicationContext
            val fine = appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            val coarse = appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) return

            runCatching {
                ContextCompat.startForegroundService(
                    appContext,
                    Intent(appContext, RiderNavigationLocationService::class.java)
                )
            }.onFailure {
                Log.w("QueueGoNavLocation", "foreground_start_rejected", it)
            }
        }

        fun stop(context: Context) {
            RiderNavigationLocationStore.clear()
            context.applicationContext.stopService(
                Intent(context.applicationContext, RiderNavigationLocationService::class.java)
            )
        }
    }

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(applicationContext)
    }
    private val request by lazy {
        LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            NAVIGATION_LOCATION_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(NAVIGATION_LOCATION_MIN_INTERVAL_MS)
            .setMinUpdateDistanceMeters(NAVIGATION_LOCATION_MIN_DISTANCE_METERS)
            .setWaitForAccurateLocation(false)
            .build()
    }

    private var callback: LocationCallback? = null
    private var lastAccepted: Location? = null
    private var locationUpdatesStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val foregroundStarted = runCatching {
            val notification = navigationNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NAVIGATION_LOCATION_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NAVIGATION_LOCATION_NOTIFICATION_ID, notification)
            }
        }.onFailure {
            Log.w("QueueGoNavLocation", "start_foreground_failed", it)
        }.isSuccess

        if (!foregroundStarted) {
            stopSelf()
            return START_NOT_STICKY
        }

        startLocationUpdates()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopLocationUpdates()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        if (locationUpdatesStarted || !hasLocationPermission()) return

        val nextCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(::accept)
            }
        }
        callback = nextCallback

        val started = runCatching {
            fusedClient.requestLocationUpdates(request, nextCallback, Looper.getMainLooper())
            fusedClient.lastLocation.addOnSuccessListener { location ->
                if (!locationUpdatesStarted || location == null) return@addOnSuccessListener
                val ageMs =
                    (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L
                if (ageMs in 0L..NAVIGATION_STALE_LAST_FIX_MS) accept(location)
            }
        }.onFailure {
            Log.w("QueueGoNavLocation", "location_updates_failed", it)
        }.isSuccess

        if (started) {
            locationUpdatesStarted = true
            Log.i("QueueGoNavLocation", "foreground_updates_started")
        } else {
            callback = null
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        locationUpdatesStarted = false
        callback?.let { runCatching { fusedClient.removeLocationUpdates(it) } }
        callback = null
        Log.i("QueueGoNavLocation", "foreground_updates_stopped")
    }

    private fun accept(location: Location) {
        if (!locationUpdatesStarted) return
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return
        if (location.hasAccuracy() && location.accuracy > NAVIGATION_MAX_ACCURACY_METERS) return

        val previous = lastAccepted
        if (previous != null) {
            val deltaSeconds =
                (location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000.0
            if (deltaSeconds <= 0.0) return

            val distance = previous.distanceTo(location)
            val maximumJump =
                max(120f, (deltaSeconds * NAVIGATION_MAX_REASONABLE_SPEED_MPS).toFloat())
            if (distance > maximumJump) return
        }

        lastAccepted = Location(location)
        RiderNavigationLocationStore.publish(
            NavigationMapFix(
                point = NavigationMapPoint(location.latitude, location.longitude),
                bearingDegrees =
                    location.bearing.takeIf { location.hasBearing() && it.isFinite() },
                accuracyMeters =
                    location.accuracy.takeIf { location.hasAccuracy() && it.isFinite() },
                speedMetersPerSecond =
                    location.speed.takeIf { location.hasSpeed() && it.isFinite() && it >= 0f },
                elapsedRealtimeNanos = location.elapsedRealtimeNanos,
                bearingAccuracyDegrees = location.bearingAccuracyDegrees.takeIf {
                    location.hasBearingAccuracy() && it.isFinite() && it >= 0f
                },
                speedAccuracyMetersPerSecond = location.speedAccuracyMetersPerSecond.takeIf {
                    location.hasSpeedAccuracy() && it.isFinite() && it >= 0f
                }
            )
        )
    }

    private fun hasLocationPermission(): Boolean {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun navigationNotification() =
        NotificationCompat.Builder(this, NAVIGATION_LOCATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.qg_notification)
            .setContentTitle("QueueGo Rider")
            .setContentText("กำลังนำทางและอัปเดตตำแหน่ง")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(returnPendingIntent())
            .build()

    private fun returnPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                NAVIGATION_LOCATION_CHANNEL_ID,
                "การนำทาง QueueGo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "รักษาตำแหน่ง Rider ต่อเนื่องระหว่างงานนำทาง"
                setSound(null, null)
                enableVibration(false)
            }
        )
    }
}
