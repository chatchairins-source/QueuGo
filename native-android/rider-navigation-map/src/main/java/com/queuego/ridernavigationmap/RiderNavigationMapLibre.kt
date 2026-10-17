package com.queuego.ridernavigationmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.queuego.ridermaplibreosm.QueueGoMapLibreStyle
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.cos
import kotlin.math.sin

private class RiderMapLibreNavigationHolder(
    context: android.content.Context,
    private val onReady: (Boolean) -> Unit,
    private val onCameraChanged: (NavigationMapCamera) -> Unit,
    private val onUserGesture: () -> Unit,
    private val onDiagnostics: ((NavigationMapDiagnostics) -> Unit)?
) {
    val view = MapView(context)
    private var map: MapLibreMap? = null
    private var created = false
    private var started = false
    private var resumed = false
    private var destroyed = false
    private var styleReady = false
    private var pendingState = RiderNavigationMapState()
    private var initialCamera: NavigationMapCamera? = null
    private var renderPassCount = 0
    private var mapCreatedCount = 0
    private var routeLayerRebuildCount = 0
    private var stopMutationCount = 0
    private var demandMutationCount = 0
    private var routeGeometryUpdateCount = 0
    private var routeSignature = ""
    private var stopSignature = ""
    private var demandSignature = ""
    private val iconBitmaps = mutableListOf<Bitmap>()

    fun create(initial: NavigationMapCamera?) {
        if (created || destroyed) return
        created = true
        initialCamera = initial
        MapLibre.getInstance(view.context.applicationContext)
        view.onCreate(null)
        view.getMapAsync { ready ->
            if (destroyed) return@getMapAsync
            map = ready
            mapCreatedCount += 1
            ready.uiSettings.isAttributionEnabled = true
            ready.uiSettings.isLogoEnabled = false
            ready.uiSettings.isCompassEnabled = false
            ready.uiSettings.isTiltGesturesEnabled = false
            ready.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    onUserGesture()
                }
            }
            ready.addOnCameraIdleListener {
                val camera = ready.cameraPosition
                val target = camera.target ?: return@addOnCameraIdleListener
                onCameraChanged(
                    NavigationMapCamera(
                        point = NavigationMapPoint(target.latitude, target.longitude),
                        zoom = camera.zoom.toInt().coerceIn(4, 20),
                        bearingDegrees = camera.bearing.toFloat().takeIf { it.isFinite() },
                        tiltDegrees = camera.tilt.toFloat().takeIf { it.isFinite() }
                    )
                )
            }
            ready.setStyle(Style.Builder().fromJson(QueueGoMapLibreStyle.rasterFallbackJson())) {
                if (destroyed) return@setStyle
                styleReady = true
                initialCamera?.let(::applyCameraImmediately)
                render(pendingState)
                onReady(true)
                publishDiagnostics()
            }
        }
    }

    fun render(state: RiderNavigationMapState) {
        pendingState = state
        val ready = map ?: return
        if (!styleReady) return

        renderPassCount += 1
        val routes = state.styledRemainingRoutes()
        val stops = state.remainingStops()
        val demand = state.renderableDemandZones()

        val nextRouteSignature = routes.joinToString("|") {
            it.id + ":" + it.style.name + ":" + it.points.size + ":" +
                it.points.firstOrNull()?.latitude + ":" + it.points.lastOrNull()?.longitude
        }
        val nextStopSignature = stops.joinToString("|") {
            it.id + ":" + it.sequence + ":" + it.completed + ":" + it.point.latitude + ":" + it.point.longitude
        }
        val nextDemandSignature = demand.joinToString("|") {
            it.id + ":" + it.level + ":" + it.point.latitude + ":" + it.point.longitude
        }
        if (nextRouteSignature != routeSignature) {
            if (routeSignature.isNotEmpty()) routeGeometryUpdateCount += 1
            routeLayerRebuildCount += 1
            routeSignature = nextRouteSignature
        }
        if (nextStopSignature != stopSignature) {
            stopMutationCount += 1
            stopSignature = nextStopSignature
        }
        if (nextDemandSignature != demandSignature) {
            demandMutationCount += 1
            demandSignature = nextDemandSignature
        }

        ready.clear()
        recycleIcons()

        routes.forEach { segment ->
            val points = segment.points.filter { it.valid }
            if (points.size < 2) return@forEach
            val color = if (segment.style == NavigationRouteStyle.ACTIVE) {
                Color.rgb(230, 0, 45)
            } else {
                Color.argb(82, 230, 0, 45)
            }
            val width = if (segment.style == NavigationRouteStyle.ACTIVE) 7f else 5f
            ready.addPolyline(
                PolylineOptions()
                    .addAll(points.map { LatLng(it.latitude, it.longitude) })
                    .color(color)
                    .width(width)
            )
        }

        val iconFactory = IconFactory.getInstance(view.context)
        demand.forEach { zone ->
            val bitmap = demandBitmap(zone.level)
            iconBitmaps += bitmap
            ready.addMarker(
                MarkerOptions()
                    .position(LatLng(zone.point.latitude, zone.point.longitude))
                    .title("พื้นที่มีโอกาสรับงาน")
                    .icon(iconFactory.fromBitmap(bitmap))
            )
        }

        stops.forEach { stop ->
            val bitmap = stopBitmap(stop.sequence, stop.kind)
            iconBitmaps += bitmap
            ready.addMarker(
                MarkerOptions()
                    .position(LatLng(stop.point.latitude, stop.point.longitude))
                    .title(stop.label.ifBlank { if (stop.kind == NavigationStopKind.PICKUP) "จุดรับ" else "จุดส่ง" })
                    .icon(iconFactory.fromBitmap(bitmap))
            )
        }

        state.rider?.takeIf { it.point.valid }?.let { fix ->
            val bitmap = riderBitmap(fix.bearingDegrees)
            iconBitmaps += bitmap
            ready.addMarker(
                MarkerOptions()
                    .position(LatLng(fix.point.latitude, fix.point.longitude))
                    .title("ตำแหน่ง Rider")
                    .icon(iconFactory.fromBitmap(bitmap))
            )
        }

        applyCameraForState(state)
        publishDiagnostics()
    }

    private fun applyCameraForState(state: RiderNavigationMapState) {
        val ready = map ?: return
        when (state.viewMode) {
            NavigationViewMode.FOLLOW -> {
                val fix = state.rider ?: return
                if (!fix.point.valid) return
                val camera = CameraPosition.Builder()
                    .target(LatLng(fix.point.latitude, fix.point.longitude))
                    .zoom(state.followZoom.toDouble().coerceIn(4.0, 20.0))
                    .bearing((fix.bearingDegrees ?: 0f).toDouble())
                    .tilt(if ((fix.speedMetersPerSecond ?: 0f) > 1.5f) 42.0 else 28.0)
                    .build()
                ready.animateCamera(CameraUpdateFactory.newCameraPosition(camera), 380)
            }
            NavigationViewMode.OVERVIEW -> {
                val points = buildList {
                    state.rider?.point?.takeIf { it.valid }?.let(::add)
                    state.remainingStops().mapTo(this) { it.point }
                    state.styledRemainingRoutes().flatMapTo(this) { it.points.filter(NavigationMapPoint::valid) }
                }
                if (points.isEmpty()) return
                if (points.size == 1) {
                    ready.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(points.first().latitude, points.first().longitude),
                            16.0
                        ),
                        320
                    )
                } else {
                    val bounds = LatLngBounds.fromLatLngs(points.map { LatLng(it.latitude, it.longitude) })
                    val camera = ready.getCameraForLatLngBounds(bounds) ?: return
                    ready.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(camera.target)
                                .zoom((camera.zoom - 0.55).coerceAtMost(16.0))
                                .bearing(0.0)
                                .tilt(0.0)
                                .build()
                        ),
                        320
                    )
                }
            }
            NavigationViewMode.FREE_PAN -> Unit
        }
    }

    private fun applyCameraImmediately(camera: NavigationMapCamera) {
        if (!camera.point.valid) return
        val ready = map ?: return
        ready.cameraPosition = CameraPosition.Builder()
            .target(LatLng(camera.point.latitude, camera.point.longitude))
            .zoom(camera.zoom.toDouble().coerceIn(4.0, 20.0))
            .bearing((camera.bearingDegrees ?: 0f).toDouble())
            .tilt((camera.tiltDegrees ?: 0f).toDouble().coerceIn(0.0, 60.0))
            .build()
    }

    private fun stopBitmap(sequence: Int, kind: NavigationStopKind): Bitmap {
        val size = 58
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, 27f, paint)
        paint.color = if (kind == NavigationStopKind.PICKUP) Color.rgb(22, 163, 74) else Color.rgb(230, 0, 45)
        canvas.drawCircle(size / 2f, size / 2f, 21f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 20f
        paint.isFakeBoldText = true
        val y = size / 2f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(sequence.coerceAtLeast(1).toString(), size / 2f, y, paint)
        return bitmap
    }

    private fun riderBitmap(bearing: Float?): Bitmap {
        val size = 60
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, 28f, paint)
        paint.color = Color.rgb(22, 119, 255)
        canvas.drawCircle(size / 2f, size / 2f, 21f, paint)
        if (bearing != null && bearing.isFinite()) {
            canvas.save()
            canvas.rotate(bearing, size / 2f, size / 2f)
            paint.color = Color.WHITE
            val path = android.graphics.Path().apply {
                moveTo(size / 2f, 10f)
                lineTo(size / 2f - 6f, 25f)
                lineTo(size / 2f + 6f, 25f)
                close()
            }
            canvas.drawPath(path, paint)
            canvas.restore()
        }
        return bitmap
    }

    private fun demandBitmap(level: Int): Bitmap {
        val size = when (level.coerceIn(1, 3)) { 1 -> 52; 2 -> 68; else -> 84 }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = when (level.coerceIn(1, 3)) {
            1 -> Color.argb(48, 230, 0, 45)
            2 -> Color.argb(70, 230, 0, 45)
            else -> Color.argb(92, 230, 0, 45)
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, paint)
        paint.color = Color.argb(210, 230, 0, 45)
        canvas.drawCircle(size / 2f, size / 2f, 6f, paint)
        return bitmap
    }

    private fun recycleIcons() {
        iconBitmaps.forEach { if (!it.isRecycled) runCatching { it.recycle() } }
        iconBitmaps.clear()
    }

    private fun publishDiagnostics() {
        onDiagnostics?.invoke(
            NavigationMapDiagnostics(
                viewCreatedCount = if (created) 1 else 0,
                mapCreatedCount = mapCreatedCount,
                routeLayerRebuildCount = routeLayerRebuildCount,
                stopMutationCount = stopMutationCount,
                renderPassCount = renderPassCount,
                routeGeometryUpdateCount = routeGeometryUpdateCount,
                demandMutationCount = demandMutationCount
            )
        )
    }

    fun onStart() {
        if (!created || started || destroyed) return
        started = true
        view.onStart()
    }

    fun onResume() {
        if (!created || resumed || destroyed) return
        resumed = true
        view.onResume()
    }

    fun onPause() {
        if (!resumed || destroyed) return
        resumed = false
        view.onPause()
    }

    fun onStop() {
        if (!started || destroyed) return
        started = false
        view.onStop()
    }

    fun destroy() {
        if (destroyed) return
        if (resumed) onPause()
        if (started) onStop()
        destroyed = true
        runCatching { map?.clear() }
        recycleIcons()
        onReady(false)
        if (created) view.onDestroy()
        map = null
    }
}

@Composable
fun RiderNavigationMap(
    stateKey: String,
    state: RiderNavigationMapState,
    modifier: Modifier = Modifier,
    initialCamera: NavigationMapCamera? = null,
    onMapReady: (Boolean) -> Unit = {},
    onCameraChanged: (NavigationMapCamera) -> Unit = {},
    onUserGesture: () -> Unit = {},
    onDiagnostics: ((NavigationMapDiagnostics) -> Unit)? = null
) {
    require(stateKey.isNotBlank()) { "Navigation map stateKey must be stable and non-blank" }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentReady = rememberUpdatedState(onMapReady)
    val currentCamera = rememberUpdatedState(onCameraChanged)
    val currentGesture = rememberUpdatedState(onUserGesture)
    val currentDiagnostics = rememberUpdatedState(onDiagnostics)

    val holder = remember(stateKey, context) {
        RiderMapLibreNavigationHolder(
            context = context,
            onReady = { currentReady.value(it) },
            onCameraChanged = { currentCamera.value(it) },
            onUserGesture = { currentGesture.value() },
            onDiagnostics = { currentDiagnostics.value?.invoke(it) }
        )
    }

    DisposableEffect(holder, lifecycle) {
        holder.create(initialCamera)
        fun sync(current: Lifecycle.State) {
            if (current.isAtLeast(Lifecycle.State.STARTED)) holder.onStart() else holder.onStop()
            if (current.isAtLeast(Lifecycle.State.RESUMED)) holder.onResume() else holder.onPause()
        }
        sync(lifecycle.currentState)
        val observer = LifecycleEventObserver { _, _ -> sync(lifecycle.currentState) }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder.destroy()
        }
    }

    LaunchedEffect(state) {
        holder.render(state)
    }

    AndroidView(factory = { holder.view }, modifier = modifier)
}
