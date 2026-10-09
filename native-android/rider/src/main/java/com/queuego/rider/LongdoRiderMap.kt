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

    fun refreshOverlays(
        context: android.content.Context,
        job: RiderJob?,
        laundryJob: RiderLaundryJob?,
        pickups: List<MarketPickup>
    ) {
        val ldmap = map ?: return
        val key = buildString {
            append(job?.id ?: "none")
            append("|")
            append(job?.status ?: "")
            append("|")
            append(job?.pickupLat).append(",").append(job?.pickupLng)
            append("|")
            append(job?.deliveryLat).append(",").append(job?.deliveryLng)
            append("|laundry:")
            append(laundryJob?.jobId ?: "none").append(":").append(laundryJob?.jobStatus ?: "")
            append(":").append(laundryJob?.fromLatitude).append(",").append(laundryJob?.fromLongitude)
            append(":").append(laundryJob?.toLatitude).append(",").append(laundryJob?.toLongitude)
            append("|")
            pickups.forEach { append(it.pickupId).append(":").append(it.status).append(";") }
        }
        if (key == lastOverlayKey) return
        lastOverlayKey = key

        runCatching { ldmap.clearPin() }

        deviceLocation(context)?.let { (lat, lon) ->
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

        if (job == null && laundryJob == null) {
            centerOnDevice(context)
            return
        }

        if (job == null && laundryJob != null) {
            addPin(laundryJob.fromLatitude, laundryJob.fromLongitude)
            addPin(laundryJob.toLatitude, laundryJob.toLongitude)
            val target = laundryJob.navigationTarget
            if (target != null) {
                runCatching { ldmap.setLocation(MapLocation(target.second, target.first)) }
            } else {
                centerOnDevice(context)
            }
            return
        }

        if (job!!.marketOrderId != null && !job.isDelivering) {
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

    fun centerOnDevice(context: Context) {
        val location = deviceLocation(context) ?: return
        runCatching {
            map?.setLocation(MapLocation(location.second, location.first))
        }
    }

    fun recenter(context: Context) {
        centerOnDevice(context)
    }

    fun dispose() {
        runCatching { mapView?.onPause() }
        map = null
        mapView = null
    }
}

@Composable
fun RiderLongdoMap(
    modifier: Modifier = Modifier,
    job: RiderJob?,
    laundryJob: RiderLaundryJob? = null,
    marketPickups: List<MarketPickup>,
    recenterSignal: Int,
    onMapReady: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val holder = remember { RiderLongdoHolder() }
    val latestJob: MutableState<RiderJob?> = remember { mutableStateOf(job) }
    val latestLaundryJob: MutableState<RiderLaundryJob?> = remember { mutableStateOf(laundryJob) }
    val latestPickups = remember { mutableStateOf(marketPickups) }
    latestJob.value = job
    latestLaundryJob.value = laundryJob
    latestPickups.value = marketPickups

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val view = LayoutInflater.from(ctx)
                .inflate(R.layout.view_longdo_map, null, false) as MapGLSurfaceView
            holder.mapView = view
            view.setListener(object : IMapListener {
                override fun onMapCreated(map: com.longdo.api.Map) {
                    holder.map = map
                    runCatching {
                        map.setBase(LongdoLayer(ctx, "gray", 0, 1, 20))
                    }
                    holder.centerOnDevice(ctx)
                    holder.refreshOverlays(ctx, latestJob.value, latestLaundryJob.value, latestPickups.value)
                    onMapReady(true)
                }
            })
            runCatching { view.onResume() }
            view
        },
        update = {
            holder.refreshOverlays(context, job, laundryJob, marketPickups)
        }
    )

    LaunchedEffect(job?.id, job?.status, job?.pickupLat, job?.pickupLng, job?.deliveryLat, job?.deliveryLng, laundryJob?.jobId, laundryJob?.jobStatus, marketPickups) {
        holder.refreshOverlays(context, job, marketPickups)
    }

    LaunchedEffect(recenterSignal) {
        if (recenterSignal > 0) holder.recenter(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            holder.dispose()
            onMapReady(false)
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
            ?.let { it.latitude to it.longitude }
    }.getOrNull()
}
