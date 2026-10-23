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
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.queuego.ridernavigationmap.NavigationMapFix
import com.queuego.ridernavigationmap.NavigationMapPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max

private const val NAVIGATION_LOCATION_CHANNEL_ID = "queuego_rider_navigation_location"
private const val NAVIGATION_LOCATION_NOTIFICATION_ID = 4112
private const val NAVIGATION_LOCATION_INTERVAL_MS = 500L
private const val NAVIGATION_LOCATION_MIN_INTERVAL_MS = 250L
private const val NAVIGATION_LOCATION_MIN_DISTANCE_METERS = 0.5f
private const val NAVIGATION_MAX_ACCURACY_METERS = 80f
private const val NAVIGATION_MAX_REASONABLE_SPEED_MPS = 55f
private const val NAVIGATION_STALE_LAST_FIX_MS = 30_000L

internal fun riderShortestBearingDelta(from: Float, to: Float): Float {
    val a = ((from % 360f) + 360f) % 360f
    val b = ((to % 360f) + 360f) % 360f
    return ((b - a + 540f) % 360f) - 180f
}

internal fun riderResolveTravelBearing(
    priorBearing: Float?,
    rawBearing: Float?,
    motionBearing: Float?,
    speedMetersPerSecond: Float?
): Float? {
    val prior = priorBearing?.takeIf { it.isFinite() }?.let { ((it % 360f) + 360f) % 360f }
    val raw = rawBearing?.takeIf { it.isFinite() }?.let { ((it % 360f) + 360f) % 360f }
    val motion = motionBearing?.takeIf { it.isFinite() }?.let { ((it % 360f) + 360f) % 360f }
    val speed = speedMetersPerSecond?.takeIf { it.isFinite() && it >= 0f }

    if (raw == null) return motion ?: prior
    if (speed != null && speed >= 2f) {
        if (motion != null && abs(riderShortestBearingDelta(motion, raw)) > 65f) {
            return motion
        }
        if (prior != null && abs(riderShortestBearingDelta(prior, raw)) > 120f) {
            if (motion == null || abs(riderShortestBearingDelta(prior, motion)) < 70f) {
                return motion ?: prior
            }
        }
    }
    return raw
}

internal object RiderNavigationLocationStore {
    private val mutableFix = MutableStateFlow<NavigationMapFix?>(null)
    private val mutableSessionStartedElapsedRealtimeMs = MutableStateFlow<Long?>(null)
    val fix: StateFlow<NavigationMapFix?> = mutableFix.asStateFlow()
    val sessionStartedElapsedRealtimeMs: StateFlow<Long?> =
        mutableSessionStartedElapsedRealtimeMs.asStateFlow()

    fun publish(next: NavigationMapFix) {
        if (mutableSessionStartedElapsedRealtimeMs.value == null) {
            val elapsedMs = next.elapsedRealtimeNanos / 1_000_000L
            if (elapsedMs > 0L) mutableSessionStartedElapsedRealtimeMs.value = elapsedMs
        }
        mutableFix.value = next
    }

    fun clear() {
        mutableFix.value = null
        mutableSessionStartedElapsedRealtimeMs.value = null
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

    private val dispatchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dispatchHeartbeat: Job? = null
    private var callback: LocationCallback? = null
    private var lastAccepted: Location? = null
    private var lastResolvedBearing: Float? = null
    private var locationUpdatesStarted = false
    private var freshLocationRequest: CancellationTokenSource? = null

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
        requestFreshLocation()
        if (dispatchHeartbeat == null && locationUpdatesStarted) {
            dispatchHeartbeat = dispatchScope.launch {
                val api = QueueGoApi()
                while (isActive) {
                    try {
                        withTimeout(15_000L) {
                            val fix = RiderNavigationLocationStore.fix.value
                            RiderNavigationDispatchReadiness.syncNavigation(applicationContext, api,
                                fix?.elapsedRealtimeNanos, fix?.point?.latitude, fix?.point?.longitude)
                        }
                    } catch (error: TimeoutCancellationException) {
                        // No successful renewal: any previous server lease expires normally.
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.w("QueueGoNavLocation", "dispatch_readiness_unavailable")
                    }
                    delay(20_000L)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        dispatchScope.cancel()
        freshLocationRequest?.cancel()
        stopLocationUpdates()
        RiderNavigationLocationStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        if (locationUpdatesStarted || !hasLocationPermission()) return
        RiderLocationIntegrityStore.startSession()

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
            Log.i(
                "QueueGoNavLocation",
                "foreground_updates_started interval=${NAVIGATION_LOCATION_INTERVAL_MS}ms min=${NAVIGATION_LOCATION_MIN_INTERVAL_MS}ms"
            )
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

    // Repeated starts (recenter/resume) must request a new measurement even when the
    // continuous subscription is already running and the Rider is standing still.
    private fun requestFreshLocation() {
        if (!locationUpdatesStarted || !hasLocationPermission()) return
        freshLocationRequest?.cancel()
        val requestToken = CancellationTokenSource()
        freshLocationRequest = requestToken
        runCatching {
            fusedClient.getCurrentLocation(
                CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(0L)
                    .setDurationMillis(15_000L)
                    .build(),
                requestToken.token
            ).addOnSuccessListener { location ->
                if (freshLocationRequest === requestToken && location != null) accept(location)
            }.addOnFailureListener {
                Log.w("QueueGoNavLocation", "fresh_location_unavailable")
            }
        }.onFailure {
            Log.w("QueueGoNavLocation", "fresh_location_request_failed", it)
        }
    }

    private fun accept(location: Location) {
        if (!locationUpdatesStarted) return
        RiderLocationIntegrityStore.record(location)
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

        val rawBearing = location.bearing.takeIf { location.hasBearing() && it.isFinite() }
        val speed = location.speed.takeIf { location.hasSpeed() && it.isFinite() && it >= 0f }
        val motionBearing = previous?.let { prior ->
            val deltaSeconds =
                (location.elapsedRealtimeNanos - prior.elapsedRealtimeNanos) / 1_000_000_000.0
            val distance = prior.distanceTo(location)
            if (deltaSeconds > 0.0 && deltaSeconds <= 5.0 && distance >= 2.2f) {
                prior.bearingTo(location).takeIf { it.isFinite() }
            } else null
        }
        val resolvedBearing = riderResolveTravelBearing(
            priorBearing = lastResolvedBearing,
            rawBearing = rawBearing,
            motionBearing = motionBearing,
            speedMetersPerSecond = speed
        )
        lastAccepted = Location(location)
        lastResolvedBearing = resolvedBearing
        RiderNavigationLocationStore.publish(
            NavigationMapFix(
                point = NavigationMapPoint(location.latitude, location.longitude),
                bearingDegrees = resolvedBearing,
                accuracyMeters =
                    location.accuracy.takeIf { location.hasAccuracy() && it.isFinite() },
                speedMetersPerSecond = speed,
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
