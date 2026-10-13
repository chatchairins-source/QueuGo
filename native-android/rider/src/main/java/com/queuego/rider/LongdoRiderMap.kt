package com.queuego.rider

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.Location
import android.location.LocationListener
import android.graphics.PointF
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Typeface
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
import com.longdo.api.MapGLSurfaceView
import com.longdo.api.Pin
import com.longdo.api.type.MapLocation

private const val RIDER_MAP_LOCATION_REFRESH_MS = 2_500L
private const val RIDER_MAP_LOCATION_REFRESH_METERS = 2f
private const val RIDER_MAP_LAST_KNOWN_MAX_AGE_MS = 120_000L
private const val RIDER_MAP_LOCATION_ANIMATION_STEPS = 18

private enum class RiderStopVisual { PICKUP, DROPOFF }

private fun renderRiderMarkerBitmap(context: Context, resId: Int): Bitmap {
    val drawable = requireNotNull(context.resources.getDrawable(resId, context.theme)) {
        "Rider marker resource unavailable: $resId"
    }
    val width = drawable.intrinsicWidth.coerceAtLeast(1)
    val height = drawable.intrinsicHeight.coerceAtLeast(1)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
    }
}

private class RiderLongdoHolder(
    private val cameraState: MutableState<DoubleArray?>,
    private val persistCamera: (DoubleArray) -> Unit
) {
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
    private var latestDeviceLocation: Pair<Double, Double>? = null
    private var currentLocationPin: Pin? = null
    private val stopPins = mutableMapOf<String, Pin>()
    private val stopBitmaps = mutableMapOf<Pair<RiderStopVisual, Int>, Bitmap>()
    private var cameraJobId: String? = null
    private val markerBitmaps = mutableMapOf<Int, Bitmap>()
    private var recenterOnNextFix = false
    private var currentJob: RiderJob? = null
    private var currentPickups: List<MarketPickup> = emptyList()
    private var currentBundleOrders: List<RiderBundleOrder> = emptyList()

    private fun markerBitmap(context: Context, resId: Int): Bitmap =
        markerBitmaps[resId] ?: renderRiderMarkerBitmap(context, resId).also {
            markerBitmaps[resId] = it
        }

    private fun stopBitmap(context: Context, visual: RiderStopVisual, number: Int): Bitmap =
        stopBitmaps.getOrPut(visual to number) {
            val asset = if (visual == RiderStopVisual.PICKUP) R.drawable.qg_marker_pickup
                else R.drawable.qg_marker_dropoff
            val bitmap = renderRiderMarkerBitmap(context, asset)
            val canvas = Canvas(bitmap)
            val scale = bitmap.width / 48f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.WHITE
            canvas.drawRoundRect(28f * scale, 0f, 48f * scale, 18f * scale, 6f * scale, 6f * scale, paint)
            paint.color = Color.rgb(14, 36, 56)
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = (if (number < 100) 12f else 9f) * scale
            val baseline = 9f * scale - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(number.toString(), 38f * scale, baseline, paint)
            bitmap
        }

    fun attachMap(nextMap: com.longdo.api.Map) {
        if (map !== nextMap) {
            lastOverlayKey = null
            currentLocationPin = null
            stopPins.clear()
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
                if (!disposed && resumed &&
                    location.latitude.isFinite() && location.longitude.isFinite() &&
                    location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0
                ) {
                    latestDeviceLocation = location.latitude to location.longitude
                    if (recenterOnNextFix) {
                        map?.let { activeMap ->
                            runCatching {
                                activeMap.setZoom(16)
                                activeMap.setLocation(MapLocation(location.longitude, location.latitude))
                            }.onSuccess {
                                centeredOnDevice = true
                                recenterOnNextFix = false
                            }
                        }
                    }
                    refreshOverlays(context, currentJob, currentPickups, currentBundleOrders)
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
                runCatching { manager.requestLocationUpdates(
                            provider,
                            RIDER_MAP_LOCATION_REFRESH_MS,
                            RIDER_MAP_LOCATION_REFRESH_METERS,
                            listener,
                            Looper.getMainLooper()
                        ) }
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
        if (resumed) {
            // Never present an in-memory fix from before Google Maps/background as "current".
            latestDeviceLocation = null
            startLocationUpdates(context)
            refreshOverlays(context, currentJob, currentPickups, currentBundleOrders)
        }
    }

    fun pause() {
        map?.let { activeMap ->
            runCatching {
                val location = activeMap.getLocation()
                val camera = doubleArrayOf(location.lon, location.lat, activeMap.getZoom().toDouble(), if (centeredOnDevice) 1.0 else 0.0)
                cameraState.value = camera
                persistCamera(camera)
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
        val location = latestDeviceLocation ?: deviceLocation(context)?.also { latestDeviceLocation = it }

        fun updateCurrentLocationPin(next: Pair<Double, Double>?) {
            if (next == null) {
                currentLocationPin?.let { pin -> runCatching { ldmap.removePin(pin) } }
                currentLocationPin = null
                return
            }
            val (lat, lon) = next
            val mapLocation = MapLocation(lon, lat)
            val existing = currentLocationPin
            if (existing == null) {
                val pin = Pin(mapLocation, markerBitmap(context, R.drawable.qg_rider_current_location))
                runCatching { ldmap.pushPin(pin) }
                    .onSuccess { currentLocationPin = pin }
            } else {
                runCatching {
                    existing.setLocation(mapLocation, true, RIDER_MAP_LOCATION_ANIMATION_STEPS)
                }.onFailure {
                    runCatching { ldmap.removePin(existing) }
                    val replacementPin = Pin(mapLocation, markerBitmap(context, R.drawable.qg_rider_current_location))
                    runCatching { ldmap.pushPin(replacementPin) }
                        .onSuccess { currentLocationPin = replacementPin }
                }
            }
        }

        val key = buildString {
            append(job?.id ?: "none")
            append("|")
            append(job?.status ?: "")
            append("|arrived:")
            append(job?.shopArrived ?: false)
            append("|")
            append(job?.pickupLat).append(",").append(job?.pickupLng)
            append("|")
            append(job?.deliveryLat).append(",").append(job?.deliveryLng)
            append("|")
            pickups.forEach { append(it.pickupId).append(":").append(it.status).append(";") }
            append("|bundle:")
            bundleOrders.forEach {
                append(it.job.id).append(":").append(it.job.status).append(":")
                    .append(it.job.shopArrived).append(":")
                    .append(it.bundleSequence).append(":").append(it.dropRank).append(";")
            }
        }

        if (key == lastOverlayKey) {
            updateCurrentLocationPin(location)
            if (job == null) centerOnDevice(context)
            return
        }
        lastOverlayKey = key

        updateCurrentLocationPin(location)
        val desiredStops = mutableSetOf<String>()

        fun addStopPin(lat: Double?, lon: Double?, number: Int, visual: RiderStopVisual) {
            if (lat == null || lon == null) return
            val stopKey = "$visual:$number:$lat:$lon"
            desiredStops.add(stopKey)
            if (stopKey in stopPins) return
            runCatching {
                val pin = Pin(
                    MapLocation(lon, lat),
                    stopBitmap(context, visual, number)
                )
                pin.setOffset(PointF(0f, -0.4f))
                ldmap.pushPin(pin)
                stopPins[stopKey] = pin
            }
        }

        if (job == null) {
            stopPins.values.forEach { runCatching { ldmap.removePin(it) } }
            stopPins.clear()
            cameraJobId = null
            centerOnDevice(context)
            return
        }

        if (bundleOrders.size > 1 && job.marketOrderId == null) {
            val numberedOrders = bundleOrders.filter { it.job.status != "cancelled" }
            val pickupOrder = numberedOrders.sortedWith(
                compareBy<RiderBundleOrder> { it.bundleSequence }.thenBy { it.job.id }
            )
            val dropOrder = numberedOrders.sortedWith(
                compareBy<RiderBundleOrder> { it.dropRank }.thenBy { it.job.id }
            )
            pickupOrder.forEachIndexed { index, bundled ->
                if (!bundled.job.shopArrived && !bundled.job.isDelivering &&
                    bundled.job.status != "completed"
                ) {
                    addStopPin(bundled.job.pickupLat, bundled.job.pickupLng, index + 1, RiderStopVisual.PICKUP)
                }
            }
            dropOrder.forEachIndexed { index, bundled ->
                if (bundled.job.status != "completed") {
                    addStopPin(
                        bundled.job.deliveryLat,
                        bundled.job.deliveryLng,
                        pickupOrder.size + index + 1, RiderStopVisual.DROPOFF
                    )
                }
            }
        } else if (job.marketOrderId != null) {
            pickups.forEachIndexed { index, pickup ->
                if (!pickup.done) addStopPin(pickup.latitude, pickup.longitude, index + 1, RiderStopVisual.PICKUP)
            }
            addStopPin(job.deliveryLat, job.deliveryLng, pickups.size + 1, RiderStopVisual.DROPOFF)
        } else {
            if (!job.shopArrived && !job.isDelivering) {
                addStopPin(job.pickupLat, job.pickupLng, 1, RiderStopVisual.PICKUP)
            }
            addStopPin(job.deliveryLat, job.deliveryLng, 2, RiderStopVisual.DROPOFF)
        }

        stopPins.keys.filter { it !in desiredStops }.forEach { key ->
            stopPins.remove(key)?.let { pin -> runCatching { ldmap.removePin(pin) } }
        }
        if (cameraJobId == job.id) return
        cameraJobId = job.id
        val target = job.navigationTarget
        if (target != null) {
            centeredOnDevice = false
            runCatching { ldmap.setLocation(MapLocation(target.second, target.first)) }
        } else {
            centerOnDevice(context)
        }
    }

    fun centerOnDevice(context: Context, force: Boolean = false) {
        val activeMap = map ?: return
        if (centeredOnDevice && !force) return
        val location = latestDeviceLocation ?: deviceLocation(context)?.also { latestDeviceLocation = it } ?: return
        runCatching {
            activeMap.setZoom(15)
            activeMap.setLocation(MapLocation(location.second, location.first))
        }.onSuccess { centeredOnDevice = true }
    }

    fun recenter(context: Context) {
        recenterOnNextFix = true
        startLocationUpdates(context)
        val activeMap = map ?: return
        val location = latestDeviceLocation ?: deviceLocation(context)?.also { latestDeviceLocation = it } ?: return
        runCatching {
            activeMap.setZoom(16)
            activeMap.setLocation(MapLocation(location.second, location.first))
        }.onSuccess { centeredOnDevice = true }
    }

    fun dispose() {
        disposed = true
        runCatching { pause() }
        map = null
        mapView = null
    }
}

@Composable
internal fun RiderLongdoMap(
    modifier: Modifier = Modifier,
    stateKey: String,
    job: RiderJob?,
    marketPickups: List<MarketPickup>,
    bundleOrders: List<RiderBundleOrder> = emptyList(),
    recenterSignal: Int,
    onMapReady: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val stateStore = remember(context) { SessionStore(context) }
    val cameraState = rememberSaveable(stateKey) { mutableStateOf(stateStore.loadMapCamera(stateKey)) }
    val holder = remember(stateKey) {
        RiderLongdoHolder(cameraState) { camera -> stateStore.saveMapCamera(stateKey, camera) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentReady = rememberUpdatedState(onMapReady)
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
            holder.refreshOverlays(context, job, marketPickups, bundleOrders)
        }
    )

    LaunchedEffect(job?.id, job?.status, job?.arrivedShopAt, job?.pickupLat, job?.pickupLng, job?.deliveryLat, job?.deliveryLng, marketPickups, bundleOrders) {
        holder.refreshOverlays(context, job, marketPickups, bundleOrders)
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
            ?.takeIf { fix ->
                val ageMs = System.currentTimeMillis() - fix.time
                ageMs in 0L..RIDER_MAP_LAST_KNOWN_MAX_AGE_MS &&
                    fix.latitude.isFinite() && fix.longitude.isFinite() &&
                    fix.latitude in -90.0..90.0 && fix.longitude in -180.0..180.0
            }
            ?.let { it.latitude to it.longitude }
    }.getOrNull()
}
