package com.queuego.rider

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
            append("|")
            pickups.forEach { append(it.pickupId).append(":").append(it.status).append(";") }
        }
        if (key == lastOverlayKey) return
        lastOverlayKey = key

        runCatching { ldmap.clearPin() }

        if (job == null) {
            runCatching { ldmap.updateAndShowCurrentLocation() }
            return
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
            runCatching { ldmap.updateAndShowCurrentLocation() }
        }
    }

    fun recenter() {
        runCatching { map?.updateAndShowCurrentLocation() }
    }

    fun dispose() {
        runCatching { map?.cancelUpdateAndShowCurrentLocation() }
        runCatching { mapView?.onPause() }
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
                    holder.map = map
                    runCatching {
                        map.setBase(LongdoLayer(ctx, "gray", 0, 1, 20))
                    }
                    runCatching { map.updateAndShowCurrentLocation() }
                    holder.refreshOverlays(ctx, latestJob.value, latestPickups.value)
                    onMapReady(true)
                }
            })
            runCatching { view.onResume() }
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
        if (recenterSignal > 0) holder.recenter()
    }

    DisposableEffect(Unit) {
        onDispose {
            holder.dispose()
            onMapReady(false)
        }
    }
}
