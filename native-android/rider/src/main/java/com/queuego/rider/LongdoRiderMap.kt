package com.queuego.rider

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.view.LayoutInflater
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.longdo.api.IMapListener
import com.longdo.api.LongdoLayer
import com.longdo.api.MapGLSurfaceView
import com.longdo.api.Pin
import com.longdo.api.type.MapLocation

private class RiderLongdoHolder {
    var mapView: MapGLSurfaceView? = null
    var map: com.longdo.api.Map? = null
    var lastOverlayKey: String? = null
    var disposed = false
    private var centeredOnDevice = false
    private var resumed = false

    fun resume() {
        val view = mapView ?: return
        if (!disposed && !resumed) { view.onResume(); resumed = true }
    }

    fun pause() {
        if (resumed) { mapView?.onPause(); resumed = false }
    }

    fun refreshOverlays(
        context: android.content.Context,
        job: RiderJob?,
        pickups: List<MarketPickup>
    ) {
        val ldmap = map ?: return
        if (disposed) return
        val location = deviceLocation(context)
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

        if (job.marketOrderId != null && !job.isDelivering) {
            pickups.filter { !it.done }.forEach { addPin(it.latitude, it.longitude) }
        } else {
            addPin(job.pickupLat, job.pickupLng)
        }
        addPin(job.deliveryLat, job.deliveryLng)

        val target = job.navigationTarget
        if (target != null) {
            runCatching { ldmap.setLocation(MapLocation(target.second, target.first)) }
        } else {
            centerOnDevice(context)
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
        map = null
        mapView = null
    }
}

@Composable
fun RiderLongdoMap(
    modifier: Modifier = Modifier,
    job: RiderJob?,
    marketPickups: List<MarketPickup>,
    recenterSignal: Int,
    onMapReady: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val holder = remember { RiderLongdoHolder() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentReady = rememberUpdatedState(onMapReady)
    val latestJob: MutableState<RiderJob?> = remember { mutableStateOf(job) }
    val latestPickups = remember { mutableStateOf(marketPickups) }
    latestJob.value = job
    latestPickups.value = marketPickups

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val view = LayoutInflater.from(ctx)
                .inflate(R.layout.view_longdo_map, null, false) as MapGLSurfaceView
            holder.mapView = view
            view.setListener(object : IMapListener {
                override fun onMapCreated(map: com.longdo.api.Map) {
                    view.post {
                        if (!holder.disposed) {
                            runCatching {
                                holder.map = map
                                map.setBase(LongdoLayer(ctx, "normal", 0, 1, 20).apply {
                                    // The SDK defaults to HTTP; keep Android cleartext disabled.
                                    layerURL = "https://ms2.longdo.com/mmmap/img.php"
                                })
                                // Production Web's initial camera, never a fabricated GPS marker.
                                map.setLocation(MapLocation(100.5018, 13.7563))
                                map.setZoom(13)
                                holder.centerOnDevice(ctx)
                                holder.refreshOverlays(ctx, latestJob.value, latestPickups.value)
                            }.onSuccess { currentReady.value(true) }
                                .onFailure { currentReady.value(false) }
                        }
                    }
                }
            })
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) holder.resume()
            view
        },
        update = {
            holder.refreshOverlays(context, job, marketPickups)
        }
    )

    LaunchedEffect(job?.id, job?.status, job?.pickupLat, job?.pickupLng, job?.deliveryLat, job?.deliveryLng, marketPickups) {
        holder.refreshOverlays(context, job, marketPickups)
    }

    LaunchedEffect(recenterSignal) {
        if (recenterSignal > 0) holder.recenter(context)
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.resume()
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
