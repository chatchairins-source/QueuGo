package com.queuego.rider

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.longdo.api.IMapListener
import com.longdo.api.LongdoLayer
import com.longdo.api.Line
import com.longdo.api.MapGLSurfaceView
import com.longdo.api.Pin
import com.longdo.api.type.MapLocation

private class RiderLongdoHolder(private val cameraState: MutableState<DoubleArray?>) {
    var mapView: MapGLSurfaceView? = null
    var map: com.longdo.api.Map? = null
    var lastOverlayKey: String? = null
    var disposed = false
    private var centeredOnDevice = cameraState.value?.getOrNull(3) == 1.0
    private var resumed = false
    private var pausePending = false
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var locationPermissionMask = 0
    private var currentJob: RiderJob? = null
    private var currentPickups: List<MarketPickup> = emptyList()
    private var currentBundleOrders: List<RiderBundleOrder> = emptyList()
    private var navigationState: RiderNavigationState = RiderNavigationState()
    private var onLocationFix: (RiderNavigationFix) -> Unit = {}
    private val routeLines = mutableListOf<Line>()
    private var renderedRouteKey: String? = null

    fun attachMap(nextMap: com.longdo.api.Map) {
        if (map !== nextMap) {
            map?.let { oldMap -> routeLines.forEach { line -> runCatching { oldMap.removeLine(line) } } }
            routeLines.clear()
            renderedRouteKey = null
            lastOverlayKey = null
            map = nextMap
        }
    }

    private fun stopLocationUpdates() {
        locationListener?.let { listener ->
            runCatching { locationManager?.removeUpdates(listener) }
            Log.i("QueueGoMapLocation", "foreground_updates_stopped")
        }
        locationListener = null
        locationManager = null
        locationPermissionMask = 0
    }

    private fun startLocationUpdates(context: Context) {
        if (disposed || !resumed) return
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val permissionMask = (if (fine) 2 else 0) + (if (coarse) 1 else 0)
        if (permissionMask == locationPermissionMask && locationListener != null) return
        stopLocationUpdates()
        if (permissionMask == 0) return
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!disposed && resumed) {
                    onLocationFix(
                        RiderNavigationFix(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                            bearingDegrees = if (location.hasBearing()) location.bearing else null,
                            speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
                            elapsedRealtimeNanos = location.elapsedRealtimeNanos
                        )
                    )
                    refreshOverlays(context, currentJob, currentPickups, currentBundleOrders)
                    renderNavigation()
                }
            }
            override fun onProviderEnabled(provider: String) {
                if (!disposed && resumed) refreshOverlays(context, currentJob, currentPickups, currentBundleOrders)
            }
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Required for Android 8/9 compatibility")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        var registered = false
        // Register available providers even when GPS is toggled off, so enabling it resumes updates.
        runCatching { manager.getProviders(false) }.getOrDefault(emptyList())
            .filter { it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER }
            .forEach { provider ->
                runCatching { manager.requestLocationUpdates(provider, 5_000L, 5f, listener, Looper.getMainLooper()) }
                    .onSuccess { registered = true }
            }
        if (registered) {
            locationManager = manager
            locationListener = listener
            locationPermissionMask = permissionMask
            Log.i("QueueGoMapLocation", "foreground_updates_started")
        }
    }

    fun resume(context: Context) {
        val view = mapView ?: return
        pausePending = false
        if (!disposed && !resumed) {
            resumed = runCatching { view.onResume() }
                .onFailure { Log.w("QueueGoLongdo", "map_on_resume_failed", it) }
                .isSuccess
        }
        if (resumed) startLocationUpdates(context)
    }

    fun pause() {
        map?.let { activeMap ->
            runCatching {
                val location = activeMap.getLocation()
                cameraState.value = doubleArrayOf(location.lon, location.lat, activeMap.getZoom().toDouble(), if (centeredOnDevice) 1.0 else 0.0)
            }
        }
        stopLocationUpdates()
        if (resumed) {
            // A permission dialog can pause before SDK0.1.8 creates renderer.map.
            // Renderer access itself is SDK-owned and can also fail during GL teardown
            // on physical Android devices, so treat "unknown" as not-ready.
            val rendererReady = runCatching { mapView?.renderer?.map != null }
                .onFailure { Log.w("QueueGoLongdo", "map_renderer_state_failed", it) }
                .getOrDefault(false)
            if (!rendererReady) {
                pausePending = true
                return
            }
            runCatching { mapView?.onPause() }
                .onFailure { Log.w("QueueGoLongdo", "map_on_pause_failed", it) }
            resumed = false
            pausePending = false
        }
    }

    fun mapCreated(view: MapGLSurfaceView) {
        if (pausePending && resumed) {
            runCatching { view.onPause() }
                .onFailure { Log.w("QueueGoLongdo", "map_created_pause_failed", it) }
            resumed = false
            pausePending = false
        }
    }

    fun refreshOverlays(
        context: android.content.Context,
        job: RiderJob?,
        pickups: List<MarketPickup>,
        bundleOrders: List<RiderBundleOrder> = emptyList()
    ) {
        currentJob = job
        currentPickups = pickups
        currentBundleOrders = bundleOrders
        startLocationUpdates(context)
        val ldmap = map ?: return
        if (disposed) return
        val matchedLocation = navigationState.snappedPoint
            ?.takeIf { navigationState.active && !navigationState.offRoute && !navigationState.weakGps }
        val location = matchedLocation?.let { it.latitude to it.longitude } ?: deviceLocation(context)
        val key = buildString {
            append(location?.first).append(",").append(location?.second).append("|")
            append(job?.id ?: "none")
            append("|")
            append(job?.status ?: "")
            append("|")
            append(job?.pickupLat).append(",").append(job?.pickupLng)
            append("|")
            append(job?.deliveryLat).append(",").append(job?.deliveryLng)
            append("|")
            pickups.forEach { append(it.pickupId).append(":").append(it.status).append(";") }
            append("|bundle:")
            bundleOrders.forEach {
                append(it.job.id).append(":").append(it.job.status).append(":")
                    .append(it.bundleSequence).append(":").append(it.dropRank).append(";")
            }
        }
        if (key == lastOverlayKey) return
        lastOverlayKey = key

        runCatching { ldmap.clearPin() }

        location?.let { (lat, lon) ->
            runCatching {
                ldmap.pushPin(
                    Pin(
                        MapLocation(lon, lat),
                        context,
                        android.R.drawable.ic_menu_compass
                    )
                )
            }
        }

        fun addPin(lat: Double?, lon: Double?) {
            if (lat == null || lon == null) return
            runCatching {
                ldmap.pushPin(
                    Pin(
                        MapLocation(lon, lat),
                        context,
                        android.R.drawable.ic_menu_mylocation
                    )
                )
            }
        }

        if (job == null) {
            centerOnDevice(context)
            return
        }

        if (bundleOrders.size > 1 && job.marketOrderId == null) {
            bundleOrders.filter { it.job.status != "cancelled" }.forEach { bundled ->
                addPin(bundled.job.pickupLat, bundled.job.pickupLng)
                addPin(bundled.job.deliveryLat, bundled.job.deliveryLng)
            }
        } else {
            if (job.marketOrderId != null && !job.isDelivering) {
                pickups.filter { !it.done }.forEach { addPin(it.latitude, it.longitude) }
            } else {
                addPin(job.pickupLat, job.pickupLng)
            }
            addPin(job.deliveryLat, job.deliveryLng)
        }

        if (navigationState.active) {
            renderNavigation()
            return
        }
        val target = job.navigationTarget
        if (target != null) {
            runCatching { ldmap.setLocation(MapLocation(target.second, target.first)) }
        } else {
            centerOnDevice(context)
        }
    }

    fun updateNavigation(
        state: RiderNavigationState,
        locationCallback: (RiderNavigationFix) -> Unit
    ) {
        navigationState = state
        onLocationFix = locationCallback
        renderNavigation()
    }

    private fun renderNavigation() {
        val activeMap = map ?: return
        val plan = navigationState.plan
        val routeKey = plan?.let { it.targetKey + ":" + navigationState.rerouteVersion }
        if (routeKey != renderedRouteKey) {
            routeLines.forEach { line -> runCatching { activeMap.removeLine(line) } }
            routeLines.clear()
            renderedRouteKey = routeKey
            plan?.parts.orEmpty().filter { it.size > 1 }.forEach { part ->
                runCatching {
                    val line = Line(part.map { MapLocation(it.longitude, it.latitude) }.toTypedArray()).apply {
                        setWidth(8)
                        setColor(floatArrayOf(230f / 255f, 0f, 45f / 255f, 235f / 255f))
                    }
                    activeMap.addLine(line)
                    routeLines += line
                }
            }
        }
        if (!navigationState.active || !navigationState.routeReady) return
        val fix = navigationState.fix ?: return
        if (navigationState.weakGps) return
        runCatching {
            activeMap.setCanRotateCamera(true)
            activeMap.setZoom(17)
            val followedPoint = navigationState.snappedPoint
                ?.takeIf { !navigationState.offRoute } ?: fix.point
            activeMap.setLocation(MapLocation(followedPoint.longitude, followedPoint.latitude))
            navigationState.headingDegrees?.let { activeMap.rotate(it, true) }
        }
    }

    fun centerOnDevice(context: Context, force: Boolean = false) {
        val activeMap = map ?: return
        if (centeredOnDevice && !force) return
        val location = deviceLocation(context) ?: return
        runCatching {
            activeMap.setZoom(15)
            activeMap.setLocation(MapLocation(location.second, location.first))
        }.onSuccess { centeredOnDevice = true }
    }

    fun recenter(context: Context) {
        centerOnDevice(context, force = true)
    }

    fun dispose() {
        disposed = true
        runCatching { pause() }
        map?.let { activeMap ->
            routeLines.forEach { line -> runCatching { activeMap.removeLine(line) } }
        }
        routeLines.clear()
        renderedRouteKey = null
        map = null
        mapView = null
    }
}

@Composable
internal fun RiderLongdoMap(
    modifier: Modifier = Modifier,
    job: RiderJob?,
    marketPickups: List<MarketPickup>,
    bundleOrders: List<RiderBundleOrder> = emptyList(),
    navigationState: RiderNavigationState,
    recenterSignal: Int,
    onLocationFix: (RiderNavigationFix) -> Unit,
    onMapReady: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val cameraState = rememberSaveable { mutableStateOf<DoubleArray?>(null) }
    val holder = remember { RiderLongdoHolder(cameraState) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentReady = rememberUpdatedState(onMapReady)
    val currentLocationCallback = rememberUpdatedState(onLocationFix)
    val currentNavigationState = rememberUpdatedState(navigationState)
    val latestJob: MutableState<RiderJob?> = remember { mutableStateOf(job) }
    val latestPickups = remember { mutableStateOf(marketPickups) }
    val latestBundleOrders = remember { mutableStateOf(bundleOrders) }
    latestJob.value = job
    latestPickups.value = marketPickups
    latestBundleOrders.value = bundleOrders

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val root = FrameLayout(ctx)
            val view = runCatching {
                LayoutInflater.from(ctx)
                    .inflate(R.layout.view_longdo_map, root, false) as MapGLSurfaceView
            }.onFailure {
                Log.e("QueueGoLongdo", "map_inflate_failed", it)
            }.getOrNull()

            if (view == null) {
                root.addView(
                    TextView(ctx).apply {
                        text = "แผนที่กำลังเชื่อมต่อใหม่"
                        gravity = Gravity.CENTER
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                currentReady.value(false)
                return@AndroidView root
            }

            root.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            holder.mapView = view
            runCatching {
                view.setListener(object : IMapListener {
                    override fun onMapCreated(map: com.longdo.api.Map) {
                        view.post {
                            holder.mapCreated(view)
                            if (!holder.disposed) {
                                runCatching {
                                    holder.attachMap(map)
                                    map.setBase(LongdoLayer(ctx, "normal", 0, 1, 20).apply {
                                        // The SDK defaults to HTTP; keep Android cleartext disabled.
                                        // Start on the endpoint selected by the live SDK key checker.
                                        // Keep key validation/host selection enabled; avoid mixed in-flight hosts.
                                        layerURL = "https://ms.longdo.com/map/msn-server/tile.php"
                                    })
                                    map.setDrawCrossSign(false)
                                    // Keep the real viewport across GL-surface or Activity recreation.
                                    // Web's fallback is only the initial camera, never a GPS marker.
                                    val camera = cameraState.value
                                    map.setLocation(MapLocation(camera?.get(0) ?: 100.5018, camera?.get(1) ?: 13.7563))
                                    map.setZoom(camera?.get(2)?.toInt() ?: 13)
                                    holder.lastOverlayKey = null
                                    holder.centerOnDevice(ctx)
                                    holder.updateNavigation(currentNavigationState.value) { currentLocationCallback.value(it) }
                                    holder.refreshOverlays(ctx, latestJob.value, latestPickups.value, latestBundleOrders.value)
                                }.onSuccess { currentReady.value(true) }
                                    .onFailure {
                                        Log.w("QueueGoLongdo", "map_created_setup_failed", it)
                                        currentReady.value(false)
                                    }
                            }
                        }
                    }
                })
            }.onFailure {
                Log.e("QueueGoLongdo", "map_listener_failed", it)
                currentReady.value(false)
            }
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) holder.resume(context)
            root
        },
        update = {
            holder.updateNavigation(currentNavigationState.value) { currentLocationCallback.value(it) }
            holder.refreshOverlays(context, job, marketPickups, bundleOrders)
        }
    )

    LaunchedEffect(job?.id, job?.status, job?.pickupLat, job?.pickupLng, job?.deliveryLat, job?.deliveryLng, marketPickups, bundleOrders) {
        holder.refreshOverlays(context, job, marketPickups, bundleOrders)
    }

    LaunchedEffect(navigationState) {
        holder.updateNavigation(currentNavigationState.value) { currentLocationCallback.value(it) }
    }

    LaunchedEffect(recenterSignal) {
        if (recenterSignal > 0) holder.recenter(context)
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.resume(context)
                Lifecycle.Event.ON_PAUSE -> holder.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder.dispose()
            currentReady.value(false)
        }
    }
}


private fun deviceLocation(context: Context): Pair<Double, Double>? {
    val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return runCatching {
        manager.getProviders(true)
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
            ?.takeIf { it.latitude.isFinite() && it.longitude.isFinite() &&
                it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
            ?.let { it.latitude to it.longitude }
    }.getOrNull()
}
