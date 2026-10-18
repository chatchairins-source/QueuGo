package com.queuego.ridernavigationmap

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.queuego.shared.queueGoMapStyle
import com.queuego.shared.queueGoMapVectorSourceUri
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import kotlin.math.hypot

private const val RIDER_MARKER_DEFAULT_ANIMATION_MS = 1_050L
private const val RIDER_MARKER_MIN_ANIMATION_MS = 420L
private const val RIDER_MARKER_MAX_ANIMATION_MS = 1_200L
private const val RIDER_MARKER_ANIMATION_CADENCE_RATIO = 1.05
private const val RIDER_MARKER_TELEPORT_METERS = 120.0
private const val CAMERA_BEARING_MIN_DELTA_DEGREES = 0.75f
private const val CAMERA_ANIMATION_MS = 480
private const val CAMERA_OVERVIEW_ANIMATION_MS = 380
private const val DEFAULT_LONGITUDE = 101.0
private const val DEFAULT_LATITUDE = 15.0

internal fun riderMarkerMoveThresholdMeters(speedMetersPerSecond: Float?): Double = when {
    speedMetersPerSecond == null -> 1.0
    speedMetersPerSecond < 0.8f -> 1.8
    speedMetersPerSecond < 3f -> 1.1
    else -> 0.6
}

internal fun shouldMoveRiderMarker(
    previousRenderedFix: NavigationMapFix?,
    nextFix: NavigationMapFix
): Boolean {
    val previous = previousRenderedFix ?: return true
    return navigationHaversineMeters(previous.point, nextFix.point) >=
        riderMarkerMoveThresholdMeters(nextFix.speedMetersPerSecond)
}

internal fun riderMarkerAnimationSeconds(
    previousFix: NavigationMapFix?,
    nextFix: NavigationMapFix
): Double = riderMarkerAnimationMillis(previousFix, nextFix) / 1000.0

internal fun shouldAnimateRiderMarker(
    previous: NavigationMapPoint,
    next: NavigationMapPoint
): Boolean = navigationHaversineMeters(previous, next) < RIDER_MARKER_TELEPORT_METERS

internal fun riderMarkerAnimationMillis(
    previousRenderedFix: NavigationMapFix?,
    nextFix: NavigationMapFix
): Long {
    val previousNanos = previousRenderedFix?.elapsedRealtimeNanos ?: 0L
    val nextNanos = nextFix.elapsedRealtimeNanos
    if (previousNanos <= 0L || nextNanos <= previousNanos) {
        return RIDER_MARKER_DEFAULT_ANIMATION_MS
    }
    val cadenceMillis = (nextNanos - previousNanos) / 1_000_000.0
    return (cadenceMillis * RIDER_MARKER_ANIMATION_CADENCE_RATIO)
        .toLong()
        .coerceIn(RIDER_MARKER_MIN_ANIMATION_MS, RIDER_MARKER_MAX_ANIMATION_MS)
        .coerceAtMost(
            (cadenceMillis * RIDER_MARKER_ANIMATION_CADENCE_RATIO)
                .toLong()
                .coerceAtLeast(0L)
        )
}

private class RiderNavigationMapHolder {
    var view: MapView? = null
    var map: MapLibreMap? = null
    var disposed = false

    private var started = false
    private var resumed = false
    private var sourceUri: String? = null
    private var styleReady = false
    private var lastState = RiderNavigationMapState()
    private var lastRouteSignature: Int? = null
    private var lastStopSignature: Int? = null
    private var lastDemandSignature: Int? = null
    private var lastRenderedRiderFix: NavigationMapFix? = null
    private var riderMarker: Marker? = null
    private var riderAnimator: ValueAnimator? = null
    private val routeLines = mutableListOf<Polyline>()
    private val stopMarkers = mutableListOf<Marker>()
    private val demandMarkers = mutableListOf<Marker>()
    private var renderPassCount = 0
    private var routeLayerRebuildCount = 0
    private var routeGeometryUpdateCount = 0
    private var stopMutationCount = 0
    private var demandMutationCount = 0
    private var viewCreatedCount = 0
    private var mapCreatedCount = 0
    private var lastCameraPoint: NavigationMapPoint? = null
    private var lastCameraBearing: Float? = null
    private var lastCameraTilt: Float? = null
    private var lastOverviewSignature: Int? = null
    private var onCameraChanged: (NavigationMapCamera) -> Unit = {}
    private var onUserGesture: () -> Unit = {}
    private var onDiagnostics: ((NavigationMapDiagnostics) -> Unit)? = null
    private var onReady: (Boolean) -> Unit = {}
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var gestureReported = false
    private var touchSlop = 8f

    fun bindCallbacks(
        ready: (Boolean) -> Unit,
        cameraChanged: (NavigationMapCamera) -> Unit,
        userGesture: () -> Unit,
        diagnostics: ((NavigationMapDiagnostics) -> Unit)?
    ) {
        onReady = ready
        onCameraChanged = cameraChanged
        onUserGesture = userGesture
        onDiagnostics = diagnostics
    }

    fun unbindCallbacks() {
        onReady = {}
        onCameraChanged = {}
        onUserGesture = {}
        onDiagnostics = null
    }

    fun ensureView(
        context: Context,
        source: String,
        initialCamera: NavigationMapCamera?
    ): MapView {
        val existing = view
        if (
            existing != null &&
            existing.context === context &&
            sourceUri == source &&
            !disposed
        ) {
            return existing
        }

        resetSurface()
        disposed = false
        sourceUri = source
        MapLibre.getInstance(context.applicationContext)
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        return MapView(context).also { mapView ->
            view = mapView
            viewCreatedCount += 1
            mapView.onCreate(null)
            mapView.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownX = event.x
                        touchDownY = event.y
                        gestureReported = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!gestureReported &&
                            hypot(
                                (event.x - touchDownX).toDouble(),
                                (event.y - touchDownY).toDouble()
                            ) > touchSlop
                        ) {
                            gestureReported = true
                            onUserGesture()
                        }
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> gestureReported = false
                }
                false
            }
            mapView.getMapAsync { readyMap ->
                if (disposed || view !== mapView) return@getMapAsync
                map = readyMap
                mapCreatedCount += 1
                initialCamera?.takeIf { it.point.valid }?.let { camera ->
                    readyMap.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(camera.point.latitude, camera.point.longitude))
                        .zoom(camera.zoom.toDouble())
                        .bearing((camera.bearingDegrees ?: 0f).toDouble())
                        .tilt((camera.tiltDegrees ?: 0f).toDouble())
                        .build()
                } ?: run {
                    readyMap.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(DEFAULT_LATITUDE, DEFAULT_LONGITUDE))
                        .zoom(6.0)
                        .build()
                }
                readyMap.addOnCameraIdleListener {
                    if (!disposed) publishCamera()
                }
                readyMap.setStyle(queueGoMapStyle(source)) {
                    if (disposed) return@setStyle
                    styleReady = true
                    render(lastState, force = true)
                    onReady(true)
                }
            }
        }
    }

    fun start() {
        if (disposed || started) return
        view?.onStart()
        started = true
    }

    fun resume() {
        if (disposed || resumed) return
        if (!started) start()
        view?.onResume()
        resumed = true
    }

    fun pause() {
        if (disposed || !resumed) return
        view?.onPause()
        resumed = false
    }

    fun stop() {
        if (disposed || !started) return
        if (resumed) pause()
        view?.onStop()
        started = false
    }

    fun retainAcrossVisibilityLoss() {
        stop()
    }

    fun render(state: RiderNavigationMapState, force: Boolean = false) {
        lastState = state
        val activeMap = map ?: return
        if (!styleReady) return
        renderPassCount += 1
        renderRoutes(activeMap, state, force)
        renderStops(activeMap, state, force)
        renderDemand(activeMap, state, force)
        renderRider(activeMap, state.rider)
        renderCamera(activeMap, state, force)
        publishDiagnostics()
    }

    private fun renderRoutes(
        activeMap: MapLibreMap,
        state: RiderNavigationMapState,
        force: Boolean
    ) {
        val routes = state.styledRemainingRoutes()
        val signature = routes.hashCode()
        if (!force && signature == lastRouteSignature) return
        routeLines.toList().forEach { line -> runCatching { activeMap.removePolyline(line) } }
        routeLines.clear()
        routes.sortedBy { if (it.style == NavigationRouteStyle.FUTURE) 0 else 1 }
            .forEach { segment ->
                if (!segment.renderable) return@forEach
                val color = if (segment.style == NavigationRouteStyle.ACTIVE) {
                    android.graphics.Color.rgb(230, 0, 45)
                } else {
                    android.graphics.Color.argb(72, 230, 0, 45)
                }
                val width = if (segment.style == NavigationRouteStyle.ACTIVE) 8f else 6f
                routeLines += activeMap.addPolyline(
                    PolylineOptions()
                        .addAll(
                            segment.points.map { point ->
                                LatLng(point.latitude, point.longitude)
                            }
                        )
                        .color(color)
                        .width(width)
                )
            }
        lastRouteSignature = signature
        routeLayerRebuildCount += 1
        routeGeometryUpdateCount += 1
    }

    private fun renderStops(
        activeMap: MapLibreMap,
        state: RiderNavigationMapState,
        force: Boolean
    ) {
        val activeId = state.resolvedActiveStopId()
        val stops = state.remainingStops()
        val signature = 31 * stops.hashCode() + (activeId?.hashCode() ?: 0)
        if (!force && signature == lastStopSignature) return
        stopMarkers.toList().forEach { marker -> runCatching { activeMap.removeMarker(marker) } }
        stopMarkers.clear()
        stops.forEach { stop ->
            stopMarkers += activeMap.addMarker(
                MarkerOptions()
                    .position(LatLng(stop.point.latitude, stop.point.longitude))
                    .title(
                        buildString {
                            append(stop.sequence)
                            append(" · ")
                            append(if (stop.kind == NavigationStopKind.PICKUP) "รับ" else "ส่ง")
                            if (stop.label.isNotBlank()) {
                                append(" · ")
                                append(stop.label)
                            }
                        }
                    )
            )
        }
        stopMutationCount += 1
        lastStopSignature = signature
    }

    private fun renderDemand(
        activeMap: MapLibreMap,
        state: RiderNavigationMapState,
        force: Boolean
    ) {
        val zones = state.renderableDemandZones()
        val signature = zones.hashCode()
        if (!force && signature == lastDemandSignature) return
        demandMarkers.toList().forEach { marker -> runCatching { activeMap.removeMarker(marker) } }
        demandMarkers.clear()
        zones.forEach { zone ->
            demandMarkers += activeMap.addMarker(
                MarkerOptions()
                    .position(LatLng(zone.point.latitude, zone.point.longitude))
                    .title("พื้นที่งานระดับ " + zone.level)
            )
        }
        demandMutationCount += 1
        lastDemandSignature = signature
    }

    private fun renderRider(activeMap: MapLibreMap, fix: NavigationMapFix?) {
        if (fix?.point?.valid != true) return
        val target = LatLng(fix.point.latitude, fix.point.longitude)
        val marker = riderMarker
        if (marker == null) {
            riderMarker = activeMap.addMarker(
                MarkerOptions()
                    .position(target)
                    .title("Rider")
            )
            lastRenderedRiderFix = fix
            return
        }
        if (!shouldMoveRiderMarker(lastRenderedRiderFix, fix)) return
        val previous = lastRenderedRiderFix
        val from = previous?.point
        riderAnimator?.cancel()
        if (from != null && shouldAnimateRiderMarker(from, fix.point)) {
            riderAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = riderMarkerAnimationMillis(previous, fix)
                addUpdateListener { animation ->
                    val t = animation.animatedFraction.toDouble()
                    marker.position = LatLng(
                        from.latitude + (fix.point.latitude - from.latitude) * t,
                        from.longitude + (fix.point.longitude - from.longitude) * t
                    )
                }
                start()
            }
        } else {
            marker.position = target
        }
        lastRenderedRiderFix = fix
    }

    private fun renderCamera(
        activeMap: MapLibreMap,
        state: RiderNavigationMapState,
        force: Boolean
    ) {
        if (state.viewMode == NavigationViewMode.OVERVIEW) {
            val bounds = navigationOverviewBounds(state) ?: return
            val viewWidth = view?.width?.coerceAtLeast(1) ?: 1
            val viewHeight = view?.height?.coerceAtLeast(1) ?: 1
            val decision = navigationOverviewViewportDecision(
                bounds,
                state.viewportInsets,
                viewWidth,
                viewHeight
            )
            val signature = 31 * decision.hashCode() + state.remainingStops().hashCode()
            if (!force && signature == lastOverviewSignature) return
            activeMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(decision.center.latitude, decision.center.longitude))
                        .zoom(decision.zoom.toDouble())
                        .bearing(0.0)
                        .tilt(0.0)
                        .build()
                ),
                CAMERA_OVERVIEW_ANIMATION_MS
            )
            lastOverviewSignature = signature
            lastCameraPoint = decision.center
            lastCameraBearing = 0f
            lastCameraTilt = 0f
            return
        }

        lastOverviewSignature = null
        if (!state.followRider) return
        val renderedBearing = activeMap.cameraPosition.bearing.toFloat()
        val decision = navigationCameraDecision(state, renderedBearing) ?: return
        val pointChanged = lastCameraPoint == null ||
            navigationHaversineMeters(lastCameraPoint!!, decision.center) >=
            navigationCameraMovementThresholdMeters(state.rider?.speedMetersPerSecond)
        val bearingChanged = navigationBearingChangedEnough(
            lastCameraBearing ?: renderedBearing,
            decision.bearingDegrees,
            CAMERA_BEARING_MIN_DELTA_DEGREES
        )
        val tiltChanged = navigationTiltChangedEnough(
            lastCameraTilt,
            decision.tiltDegrees
        )
        if (!force && !pointChanged && !bearingChanged && !tiltChanged) return

        val targetBearing = decision.bearingDegrees ?: renderedBearing
        activeMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(decision.center.latitude, decision.center.longitude))
                    .zoom(decision.zoom.toDouble())
                    .bearing(targetBearing.toDouble())
                    .tilt(decision.tiltDegrees.toDouble())
                    .build()
            ),
            CAMERA_ANIMATION_MS
        )
        lastCameraPoint = decision.center
        lastCameraBearing = targetBearing
        lastCameraTilt = decision.tiltDegrees
    }

    private fun publishCamera() {
        val activeMap = map ?: return
        val camera = activeMap.cameraPosition
        val target = camera.target ?: return
        onCameraChanged(
            NavigationMapCamera(
                point = NavigationMapPoint(target.latitude, target.longitude),
                zoom = camera.zoom.toInt().coerceIn(0, 24),
                bearingDegrees = camera.bearing.toFloat(),
                tiltDegrees = camera.tilt.toFloat()
            )
        )
    }

    private fun publishDiagnostics() {
        onDiagnostics?.invoke(
            NavigationMapDiagnostics(
                viewCreatedCount = viewCreatedCount,
                mapCreatedCount = mapCreatedCount,
                routeLayerRebuildCount = routeLayerRebuildCount,
                stopMutationCount = stopMutationCount,
                renderPassCount = renderPassCount,
                routeGeometryUpdateCount = routeGeometryUpdateCount,
                demandMutationCount = demandMutationCount,
                riderPuckPresent = riderMarker != null
            )
        )
    }

    private fun resetSurface() {
        riderAnimator?.cancel()
        riderAnimator = null
        stop()
        routeLines.clear()
        stopMarkers.clear()
        demandMarkers.clear()
        riderMarker = null
        map = null
        styleReady = false
        view?.onDestroy()
        view = null
        started = false
        resumed = false
        lastRouteSignature = null
        lastStopSignature = null
        lastDemandSignature = null
        lastRenderedRiderFix = null
    }

    fun dispose() {
        if (disposed) return
        resetSurface()
        disposed = true
        unbindCallbacks()
    }
}

private object RiderNavigationMapRegistry {
    private var activeKey: String? = null
    private var activeHolder: RiderNavigationMapHolder? = null
    private var activeLifecycle: Lifecycle? = null
    private var activeDestroyObserver: LifecycleEventObserver? = null

    private fun detachLifecycleObserver() {
        val lifecycle = activeLifecycle
        val observer = activeDestroyObserver
        if (lifecycle != null && observer != null) lifecycle.removeObserver(observer)
        activeLifecycle = null
        activeDestroyObserver = null
    }

    @Synchronized
    fun acquire(stateKey: String, lifecycle: Lifecycle): RiderNavigationMapHolder {
        val current = activeHolder
        if (
            activeKey == stateKey &&
            activeLifecycle === lifecycle &&
            current != null &&
            !current.disposed
        ) {
            return current
        }

        detachLifecycleObserver()
        current?.dispose()
        val holder = RiderNavigationMapHolder()
        val destroyObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) destroy(stateKey, holder)
        }
        activeKey = stateKey
        activeHolder = holder
        activeLifecycle = lifecycle
        activeDestroyObserver = destroyObserver
        lifecycle.addObserver(destroyObserver)
        return holder
    }

    @Synchronized
    fun release(stateKey: String, holder: RiderNavigationMapHolder) {
        if (activeKey == stateKey && activeHolder === holder) {
            holder.retainAcrossVisibilityLoss()
            holder.unbindCallbacks()
        }
    }

    @Synchronized
    fun destroy(stateKey: String, holder: RiderNavigationMapHolder) {
        if (activeKey == stateKey && activeHolder === holder) {
            detachLifecycleObserver()
            holder.dispose()
            activeHolder = null
            activeKey = null
        }
    }
}

/**
 * QueueGo navigation surface. Routing/matching/camera policy stays QueueGo-owned while
 * MapLibre renders QueueGo's installed vector package. No public-routing fallback.
 */
@Composable
fun RiderNavigationMap(
    stateKey: String,
    state: RiderNavigationMapState,
    modifier: Modifier = Modifier,
    initialCamera: NavigationMapCamera? = null,
    mapSourceUri: String? = null,
    onMapReady: (Boolean) -> Unit = {},
    onCameraChanged: (NavigationMapCamera) -> Unit = {},
    onUserGesture: () -> Unit = {},
    onDiagnostics: ((NavigationMapDiagnostics) -> Unit)? = null
) {
    require(stateKey.isNotBlank()) {
        "Navigation map stateKey must be stable and non-blank"
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentState = rememberUpdatedState(state)
    val currentReady = rememberUpdatedState(onMapReady)
    val currentCameraChanged = rememberUpdatedState(onCameraChanged)
    val currentUserGesture = rememberUpdatedState(onUserGesture)
    val currentDiagnostics = rememberUpdatedState(onDiagnostics)
    val holder = remember(stateKey, lifecycle) {
        RiderNavigationMapRegistry.acquire(stateKey, lifecycle)
    }
    holder.bindCallbacks(
        ready = { currentReady.value(it) },
        cameraChanged = { currentCameraChanged.value(it) },
        userGesture = { currentUserGesture.value() },
        diagnostics = { currentDiagnostics.value?.invoke(it) }
    )

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val sourceUri = mapSourceUri ?: queueGoMapVectorSourceUri(context)
            if (sourceUri == null) {
                // QueueGo vector hosting is release-gated. A missing runtime URL must
                // fail closed without constructing MapView before MapLibre is configured.
                currentReady.value(false)
                View(context).apply {
                    setBackgroundColor(android.graphics.Color.rgb(241, 242, 243))
                }
            } else {
                val view = holder.ensureView(context, sourceUri, initialCamera)
                (view.parent as? ViewGroup)?.removeView(view)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    holder.start()
                }
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    holder.resume()
                }
                holder.render(currentState.value)
                view
            }
        },
        update = {
            holder.render(state)
        }
    )

    DisposableEffect(lifecycle, stateKey) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> holder.start()
                Lifecycle.Event.ON_RESUME -> holder.resume()
                Lifecycle.Event.ON_PAUSE -> holder.pause()
                Lifecycle.Event.ON_STOP -> holder.stop()
                Lifecycle.Event.ON_DESTROY ->
                    RiderNavigationMapRegistry.destroy(stateKey, holder)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            RiderNavigationMapRegistry.release(stateKey, holder)
        }
    }
}
