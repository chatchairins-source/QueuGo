package com.queuego.rider

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.queuego.ridernavigationmap.LongdoNavigationRouteClient
import com.queuego.ridernavigationmap.NavigationMapCamera
import com.queuego.ridernavigationmap.NavigationMapFix
import com.queuego.ridernavigationmap.NavigationMapPoint
import com.queuego.ridernavigationmap.NavigationMapStop
import com.queuego.ridernavigationmap.NavigationRouteCoordinator
import com.queuego.ridernavigationmap.NavigationRouteProvider
import com.queuego.ridernavigationmap.NavigationRouteSegment
import com.queuego.ridernavigationmap.NavigationRouteStyle
import com.queuego.ridernavigationmap.NavigationStopKind
import com.queuego.ridernavigationmap.RiderNavigationMap
import com.queuego.ridernavigationmap.RiderNavigationMapState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlin.math.max

private const val NAVIGATION_LOCATION_INTERVAL_MS = 1_000L
private const val NAVIGATION_LOCATION_MIN_INTERVAL_MS = 500L
private const val NAVIGATION_LOCATION_MIN_DISTANCE_METERS = 1f
private const val NAVIGATION_MAX_ACCURACY_METERS = 80f
private const val NAVIGATION_MAX_REASONABLE_SPEED_MPS = 55f
private const val NAVIGATION_STALE_LAST_FIX_MS = 30_000L

private class RiderNavigationLocationEngine(
    context: Context,
    private val onFix: (NavigationMapFix) -> Unit
) {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)
    private val request = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        NAVIGATION_LOCATION_INTERVAL_MS
    )
        .setMinUpdateIntervalMillis(NAVIGATION_LOCATION_MIN_INTERVAL_MS)
        .setMinUpdateDistanceMeters(NAVIGATION_LOCATION_MIN_DISTANCE_METERS)
        .setWaitForAccurateLocation(false)
        .build()

    private var callback: LocationCallback? = null
    private var lastAccepted: Location? = null
    private var running = false

    fun start() {
        if (running) return
        val fine = appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return

        running = true
        val nextCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(::accept)
            }
        }
        callback = nextCallback
        runCatching {
            client.requestLocationUpdates(request, nextCallback, Looper.getMainLooper())
            client.lastLocation.addOnSuccessListener { location ->
                if (!running || location == null) return@addOnSuccessListener
                val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L
                if (ageMs in 0L..NAVIGATION_STALE_LAST_FIX_MS) accept(location)
            }
        }.onFailure {
            callback = null
            running = false
        }
    }

    fun stop() {
        running = false
        callback?.let { runCatching { client.removeLocationUpdates(it) } }
        callback = null
    }

    private fun accept(location: Location) {
        if (!running || !location.latitude.isFinite() || !location.longitude.isFinite()) return
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return
        if (location.hasAccuracy() && location.accuracy > NAVIGATION_MAX_ACCURACY_METERS) return

        val previous = lastAccepted
        if (previous != null) {
            val deltaSeconds =
                (location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000.0
            if (deltaSeconds > 0.0) {
                val distance = previous.distanceTo(location)
                val maximumJump = max(120f, (deltaSeconds * NAVIGATION_MAX_REASONABLE_SPEED_MPS).toFloat())
                if (distance > maximumJump) return
            }
        }

        lastAccepted = Location(location)
        onFix(
            NavigationMapFix(
                point = NavigationMapPoint(location.latitude, location.longitude),
                bearingDegrees = location.bearing.takeIf { location.hasBearing() && it.isFinite() },
                accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() && it.isFinite() },
                speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() && it.isFinite() && it >= 0f },
                elapsedRealtimeNanos = location.elapsedRealtimeNanos
            )
        )
    }
}

private fun riderLongdoNavigationKey(context: Context): String? = runCatching {
    context.packageManager
        .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        .metaData
        ?.getString("longdo.map.key")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}.getOrNull()

private fun validNavigationPoint(lat: Double?, lon: Double?): NavigationMapPoint? {
    if (lat == null || lon == null) return null
    return NavigationMapPoint(lat, lon).takeIf { it.valid }
}

internal fun riderNavigationStops(
    job: RiderJob?,
    marketPickups: List<MarketPickup>,
    bundleOrders: List<RiderBundleOrder>
): List<NavigationMapStop> {
    if (job == null || job.status.lowercase() in setOf("completed", "cancelled", "no_rider_available")) {
        return emptyList()
    }

    val stops = mutableListOf<NavigationMapStop>()
    var blockedByUnknownPendingStop = false

    fun add(
        id: String,
        sequence: Int,
        kind: NavigationStopKind,
        lat: Double?,
        lon: Double?,
        completed: Boolean,
        label: String
    ) {
        if (blockedByUnknownPendingStop) return
        val point = validNavigationPoint(lat, lon)
        if (point == null) {
            if (!completed) blockedByUnknownPendingStop = true
            return
        }
        stops += NavigationMapStop(
            id = id,
            sequence = sequence,
            kind = kind,
            point = point,
            completed = completed,
            label = label
        )
    }

    if (bundleOrders.size > 1 && job.marketOrderId == null) {
        val usable = bundleOrders.filter {
            it.job.status.lowercase() !in setOf("cancelled", "no_rider_available")
        }
        val pickups = usable.sortedWith(
            compareBy<RiderBundleOrder> { it.bundleSequence }.thenBy { it.job.id }
        )
        val drops = usable.sortedWith(
            compareBy<RiderBundleOrder> { it.dropRank }.thenBy { it.job.id }
        )
        pickups.forEachIndexed { index, order ->
            val completed = order.job.isDelivering || order.job.status.lowercase() == "completed"
            add(
                id = "pickup:" + order.job.id,
                sequence = index + 1,
                kind = NavigationStopKind.PICKUP,
                lat = order.job.pickupLat,
                lon = order.job.pickupLng,
                completed = completed,
                label = order.shopName
            )
        }
        val offset = pickups.size
        drops.forEachIndexed { index, order ->
            add(
                id = "drop:" + order.job.id,
                sequence = offset + index + 1,
                kind = NavigationStopKind.DROPOFF,
                lat = order.job.deliveryLat,
                lon = order.job.deliveryLng,
                completed = order.job.status.lowercase() == "completed",
                label = "ลูกค้า " + order.job.numberLabel
            )
        }
    } else if (job.marketOrderId != null) {
        marketPickups.forEachIndexed { index, pickup ->
            add(
                id = "pickup:" + pickup.pickupId,
                sequence = index + 1,
                kind = NavigationStopKind.PICKUP,
                lat = pickup.latitude,
                lon = pickup.longitude,
                completed = pickup.done,
                label = pickup.shopName
            )
        }
        add(
            id = "drop:" + job.id,
            sequence = marketPickups.size + 1,
            kind = NavigationStopKind.DROPOFF,
            lat = job.deliveryLat,
            lon = job.deliveryLng,
            completed = job.status.lowercase() == "completed",
            label = "ลูกค้า " + job.numberLabel
        )
    } else {
        add(
            id = "pickup:" + job.id,
            sequence = 1,
            kind = NavigationStopKind.PICKUP,
            lat = job.pickupLat,
            lon = job.pickupLng,
            completed = job.isDelivering,
            label = job.pickupAddress.orEmpty()
        )
        add(
            id = "drop:" + job.id,
            sequence = 2,
            kind = NavigationStopKind.DROPOFF,
            lat = job.deliveryLat,
            lon = job.deliveryLng,
            completed = job.status.lowercase() == "completed",
            label = job.deliveryAddress.orEmpty()
        )
    }
    return stops
}

private suspend fun buildFutureRoutes(
    routeProvider: NavigationRouteProvider,
    stops: List<NavigationMapStop>
): List<NavigationRouteSegment> {
    val remaining = stops
        .filter { !it.completed && it.point.valid }
        .sortedBy { it.sequence }
    if (remaining.size < 2) return emptyList()

    val routes = mutableListOf<NavigationRouteSegment>()
    for ((from, to) in remaining.zipWithNext()) {
        try {
            val leg = routeProvider.route(
                from = from.point,
                stop = to,
                segmentId = "future:" + from.id + ">" + to.id
            )
            routes += leg.segment.copy(style = NavigationRouteStyle.FUTURE)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Never fabricate a straight-line shortcut past a route service failure.
            break
        }
    }
    return routes
}

@Composable
internal fun QueueGoRiderNavigationMap(
    modifier: Modifier = Modifier,
    stateKey: String,
    job: RiderJob?,
    marketPickups: List<MarketPickup>,
    bundleOrders: List<RiderBundleOrder> = emptyList(),
    recenterSignal: Int,
    onMapReady: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sessionStore = remember(context) { SessionStore(context) }
    val initialCamera = remember(stateKey) {
        sessionStore.loadMapCamera(stateKey)?.let { saved ->
            NavigationMapCamera(
                point = NavigationMapPoint(saved[1], saved[0]),
                zoom = saved[2].toInt().coerceIn(4, 20)
            )
        }
    }

    var riderFix by remember(stateKey) { mutableStateOf<NavigationMapFix?>(null) }
    val locationEngine = remember(stateKey) {
        RiderNavigationLocationEngine(context) { riderFix = it }
    }

    DisposableEffect(lifecycle, locationEngine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> locationEngine.start()
                Lifecycle.Event.ON_PAUSE -> locationEngine.stop()
                Lifecycle.Event.ON_DESTROY -> locationEngine.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) locationEngine.start()
        onDispose {
            lifecycle.removeObserver(observer)
            locationEngine.stop()
        }
    }

    val stops = remember(job, marketPickups, bundleOrders) {
        riderNavigationStops(job, marketPickups, bundleOrders)
    }
    val activeStopId = stops
        .filter { !it.completed }
        .minByOrNull { it.sequence }
        ?.id

    val routeKey = remember(context.packageName) { riderLongdoNavigationKey(context) }
    val routeProvider = remember(routeKey) {
        routeKey?.let { LongdoNavigationRouteClient(it) }
    }
    val coordinator = remember(stateKey, routeProvider) {
        routeProvider?.let { NavigationRouteCoordinator(it) }
    }

    var futureRoutes by remember(stateKey) { mutableStateOf<List<NavigationRouteSegment>>(emptyList()) }
    val stopTopology = remember(stops) {
        stops.joinToString("|") {
            listOf(
                it.id,
                it.sequence.toString(),
                it.completed.toString(),
                it.point.latitude.toString(),
                it.point.longitude.toString()
            ).joinToString(":")
        }
    }

    LaunchedEffect(routeProvider, stopTopology) {
        futureRoutes = if (routeProvider == null) emptyList()
        else buildFutureRoutes(routeProvider, stops)
    }

    val baseState = RiderNavigationMapState(
        rider = riderFix,
        stops = stops,
        routes = futureRoutes,
        activeStopId = activeStopId,
        followRider = true,
        followZoom = 17
    )
    var renderedState by remember(stateKey) { mutableStateOf(baseState) }
    val stateChannel = remember(stateKey) { Channel<RiderNavigationMapState>(Channel.CONFLATED) }

    LaunchedEffect(baseState) {
        stateChannel.trySend(baseState)
    }
    LaunchedEffect(coordinator, stateChannel) {
        if (coordinator == null) {
            for (next in stateChannel) renderedState = next
        } else {
            for (next in stateChannel) {
                val result = coordinator.synchronize(next)
                renderedState = result.mapState
            }
        }
    }

    LaunchedEffect(recenterSignal) {
        if (recenterSignal > 0) {
            riderFix?.let { fix ->
                stateChannel.trySend(
                    baseState.copy(rider = fix, followRider = true)
                )
            }
        }
    }

    RiderNavigationMap(
        stateKey = stateKey,
        state = renderedState,
        modifier = modifier,
        initialCamera = initialCamera,
        onMapReady = onMapReady,
        onCameraChanged = { camera ->
            sessionStore.saveMapCamera(
                stateKey,
                doubleArrayOf(
                    camera.point.longitude,
                    camera.point.latitude,
                    camera.zoom.toDouble(),
                    1.0
                )
            )
        }
    )
}
