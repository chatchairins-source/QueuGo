package com.queuego.ridernavigationmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.longdo.api.IMapListener
import com.longdo.api.Line
import com.longdo.api.LongdoLayer
import com.longdo.api.MapGLSurfaceView
import com.longdo.api.Pin
import com.longdo.api.type.MapLocation
import com.queuego.shared.R as SharedR

private const val RIDER_MARKER_ANIMATION_STEPS = 18
private const val DEFAULT_LONGITUDE = 100.5018
private const val DEFAULT_LATITUDE = 13.7563

private val ACTIVE_ROUTE_COLOR = floatArrayOf(230f / 255f, 0f, 45f / 255f, 0.96f)
private val FUTURE_ROUTE_COLOR = floatArrayOf(230f / 255f, 0f, 45f / 255f, 0.28f)

private data class RenderedStop(val signature: String, val pin: Pin)

private class RiderNavigationMapHolder(
    private val onCameraChanged: (NavigationMapCamera) -> Unit,
    private val onDiagnostics: ((NavigationMapDiagnostics) -> Unit)?
) {
    var view: MapGLSurfaceView? = null
    var map: com.longdo.api.Map? = null
    var disposed = false

    private var resumed = false
    private var pausePending = false
    private var lastState = RiderNavigationMapState()
    private var riderPin: Pin? = null
    private var lastRiderFix: NavigationMapFix? = null
    private var lastCameraFix: NavigationMapPoint? = null
    private var lastCameraBearing: Float? = null
    private val stopPins = linkedMapOf<String, RenderedStop>()
    private val routeLines = mutableListOf<Line>()
    private val markerBitmaps = mutableMapOf<Int, Bitmap>()
    private val stopBitmaps = mutableMapOf<Triple<NavigationStopKind, Int, Boolean>, Bitmap>()
    private var stopSource: List<NavigationMapStop>? = null
    private var stopActiveId: String? = null
    private var routeSource: List<NavigationRouteSegment>? = null
    private var routeActiveId: String? = null
    private var routeRemainingStopIds: Set<String> = emptySet()

    private var viewCreatedCount = 0
    private var mapCreatedCount = 0
    private var routeLayerRebuildCount = 0
    private var stopMutationCount = 0
    private var renderPassCount = 0

    fun markViewCreated() {
        viewCreatedCount += 1
        publishDiagnostics()
    }

    fun attachMap(nextMap: com.longdo.api.Map) {
        if (map === nextMap) return
        map = nextMap
        riderPin = null
        stopPins.clear()
        routeLines.clear()
        stopSource = null
        stopActiveId = null
        routeSource = null
        routeActiveId = null
        routeRemainingStopIds = emptySet()
        lastCameraFix = null
        lastCameraBearing = null
        mapCreatedCount += 1
        publishDiagnostics()
    }

    fun resume() {
        val activeView = view ?: return
        pausePending = false
        if (!disposed && !resumed) {
            resumed = runCatching { activeView.onResume() }
                .onFailure { Log.w("QueueGoNavMap", "map_on_resume_failed", it) }
                .isSuccess
        }
        // Deliberately keep lastRiderFix, map, pins and route layers. A refresh/resume must
        // never reconstruct the map just because Rider/order state changed.
        if (resumed) render(lastState)
    }

    fun pause() {
        map?.let { activeMap ->
            runCatching {
                val location = activeMap.getLocation()
                onCameraChanged(
                    NavigationMapCamera(
                        point = NavigationMapPoint(location.lat, location.lon),
                        zoom = activeMap.getZoom()
                    )
                )
            }
        }
        if (!resumed) return
        val rendererReady = runCatching { view?.renderer?.map != null }
            .onFailure { Log.w("QueueGoNavMap", "map_renderer_state_failed", it) }
            .getOrDefault(false)
        if (!rendererReady) {
            pausePending = true
            return
        }
        runCatching { view?.onPause() }
            .onFailure { Log.w("QueueGoNavMap", "map_on_pause_failed", it) }
        resumed = false
        pausePending = false
    }

    fun mapCreated(createdView: MapGLSurfaceView) {
        if (pausePending && resumed) {
            runCatching { createdView.onPause() }
                .onFailure { Log.w("QueueGoNavMap", "map_created_pause_failed", it) }
            resumed = false
            pausePending = false
        }
    }

    fun render(state: RiderNavigationMapState) {
        lastState = state
        val activeMap = map ?: return
        if (disposed) return
        renderPassCount += 1

        state.rider?.takeIf { it.point.valid }?.let { nextFix ->
            if (riderPin == null || lastRiderFix?.point != nextFix.point) {
                renderRider(activeMap, nextFix)
            }
            lastRiderFix = nextFix
        }
        renderStops(activeMap, state)
        renderRoutes(activeMap, state)

        val followedFix = state.rider?.takeIf { it.point.valid } ?: lastRiderFix
        if (state.followRider && followedFix != null) {
            followRider(activeMap, followedFix, state.followZoom)
        }
        // Publish once per applied state even when overlays are structurally unchanged.
        // This distinguishes a stable frame from a state update that never reached the map.
        publishDiagnostics()
    }

    private fun renderRider(activeMap: com.longdo.api.Map, fix: NavigationMapFix) {
        val location = MapLocation(fix.point.longitude, fix.point.latitude)
        val existing = riderPin
        if (existing == null) {
            val pin = Pin(location, markerBitmap(SharedR.drawable.qg_map_rider))
            runCatching { activeMap.pushPin(pin) }.onSuccess { riderPin = pin }
        } else {
            runCatching { existing.setLocation(location, true, RIDER_MARKER_ANIMATION_STEPS) }
                .onFailure {
                    runCatching { activeMap.removePin(existing) }
                    val replacement = Pin(location, markerBitmap(SharedR.drawable.qg_map_rider))
                    runCatching { activeMap.pushPin(replacement) }.onSuccess { riderPin = replacement }
                }
        }
    }

    private fun renderStops(activeMap: com.longdo.api.Map, state: RiderNavigationMapState) {
        val activeStopId = state.resolvedActiveStopId()
        if (state.stops == stopSource && activeStopId == stopActiveId) return
        val desired = state.remainingStops().associateBy { it.id }
        var renderSucceeded = true

        stopPins.keys.filter { it !in desired.keys }.toList().forEach { id ->
            val rendered = stopPins[id] ?: return@forEach
            val removed = runCatching { activeMap.removePin(rendered.pin) }.isSuccess
            if (removed) {
                stopPins.remove(id)
                stopMutationCount += 1
            } else {
                renderSucceeded = false
            }
        }

        desired.values.forEach { stop ->
            val signature = listOf(
                stop.sequence.toString(),
                stop.kind.name,
                stop.point.latitude.toString(),
                stop.point.longitude.toString(),
                (stop.id == activeStopId).toString()
            ).joinToString("|")
            val existing = stopPins[stop.id]
            if (existing?.signature == signature) return@forEach

            if (existing != null) {
                val removed = runCatching { activeMap.removePin(existing.pin) }.isSuccess
                if (!removed) {
                    renderSucceeded = false
                    return@forEach
                }
                stopPins.remove(stop.id)
                stopMutationCount += 1
            }

            val pin = Pin(
                MapLocation(stop.point.longitude, stop.point.latitude),
                stopBitmap(stop.kind, stop.sequence, stop.id == activeStopId)
            )
            pin.setOffset(PointF(0f, -0.4f))
            val pushed = runCatching { activeMap.pushPin(pin) }
                .onSuccess {
                    stopPins[stop.id] = RenderedStop(signature, pin)
                    stopMutationCount += 1
                }
                .isSuccess
            if (!pushed) renderSucceeded = false
        }

        if (renderSucceeded && stopPins.keys == desired.keys) {
            stopSource = state.stops
            stopActiveId = activeStopId
        } else {
            // Do not cache a partial frame: the next refresh/resume must retry missing pins.
            stopSource = null
            stopActiveId = null
        }
        publishDiagnostics()
    }

    private fun renderRoutes(activeMap: com.longdo.api.Map, state: RiderNavigationMapState) {
        val activeStopId = state.resolvedActiveStopId()
        val remainingStopIds = state.remainingStops().mapTo(linkedSetOf()) { it.id }
        if (state.routes == routeSource &&
            activeStopId == routeActiveId &&
            remainingStopIds == routeRemainingStopIds
        ) return

        val routes = state.styledRemainingRoutes()
        val failedToRemove = routeLines.filter { line ->
            runCatching { activeMap.removeLine(line) }.isFailure
        }
        routeLines.clear()
        routeLines.addAll(failedToRemove)
        if (failedToRemove.isNotEmpty()) {
            routeSource = null
            routeActiveId = null
            routeRemainingStopIds = emptySet()
            publishDiagnostics()
            return
        }

        var renderSucceeded = true
        // Future legs first, active leg last so the dark-red current path stays visually dominant.
        routes.sortedBy { if (it.style == NavigationRouteStyle.FUTURE) 0 else 1 }
            .forEach { segment ->
                val line = Line(
                    segment.points.map { MapLocation(it.longitude, it.latitude) }.toTypedArray()
                ).apply {
                    setWidth(if (segment.style == NavigationRouteStyle.ACTIVE) 8 else 6)
                    setColor(
                        if (segment.style == NavigationRouteStyle.ACTIVE) ACTIVE_ROUTE_COLOR
                        else FUTURE_ROUTE_COLOR
                    )
                }
                val added = runCatching { activeMap.addLine(line) }
                    .onSuccess { routeLines += line }
                    .isSuccess
                if (!added) renderSucceeded = false
            }

        if (renderSucceeded && routeLines.size == routes.size) {
            routeSource = state.routes
            routeActiveId = activeStopId
            routeRemainingStopIds = remainingStopIds
            routeLayerRebuildCount += 1
        } else {
            // Keep partial route layers visible only until the next render can clean/rebuild them.
            routeSource = null
            routeActiveId = null
            routeRemainingStopIds = emptySet()
        }
        publishDiagnostics()
    }

    private fun followRider(activeMap: com.longdo.api.Map, fix: NavigationMapFix, zoom: Int) {
        val target = fix.point
        val previous = lastCameraFix
        val movedEnough = previous == null || navigationHaversineMeters(previous, target) >= 1.5
        val nextBearing = fix.bearingDegrees
            ?.takeIf { it.isFinite() }
            ?.let { ((it % 360f) + 360f) % 360f }
        val bearingChanged = when {
            nextBearing == null -> false
            lastCameraBearing == null -> true
            else -> kotlin.math.abs(((nextBearing - lastCameraBearing!! + 540f) % 360f) - 180f) >= 2f
        }
        if (!movedEnough && !bearingChanged) return

        runCatching {
            activeMap.setCanRotateCamera(true)
            activeMap.setZoom(zoom.coerceIn(14, 20))
            activeMap.setLocation(MapLocation(target.longitude, target.latitude))
            nextBearing?.let { activeMap.rotate(it, true) }
        }.onSuccess {
            lastCameraFix = target
            if (nextBearing != null) lastCameraBearing = nextBearing
        }
    }

    private fun markerBitmap(resId: Int): Bitmap =
        markerBitmaps[resId] ?: renderVector(resId).also { markerBitmaps[resId] = it }

    private fun stopBitmap(kind: NavigationStopKind, number: Int, active: Boolean): Bitmap =
        stopBitmaps.getOrPut(Triple(kind, number, active)) {
            val resId = if (kind == NavigationStopKind.PICKUP) SharedR.drawable.qg_map_pickup
            else SharedR.drawable.qg_map_dropoff
            val base = renderVector(resId)
            val scale = base.width / 48f
            val bitmap = Bitmap.createBitmap(
                base.width + (8f * scale).toInt(),
                base.height + (9f * scale).toInt(),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            canvas.drawBitmap(base, 0f, 9f * scale, null)
            base.recycle()

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = if (active) Color.rgb(230, 0, 45) else Color.WHITE
            canvas.drawRoundRect(33f * scale, 0f, 55f * scale, 19f * scale, 7f * scale, 7f * scale, paint)
            paint.color = if (active) Color.WHITE else Color.rgb(14, 36, 56)
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = (if (number < 100) 12f else 9f) * scale
            val baseline = 9.5f * scale - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(number.toString(), 44f * scale, baseline, paint)
            bitmap
        }

    private fun renderVector(resId: Int): Bitmap {
        val context = requireNotNull(view?.context) { "Navigation map view is not attached" }
        val drawable = requireNotNull(context.getDrawable(resId)) { "Map marker resource unavailable: $resId" }
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
        }
    }

    private fun publishDiagnostics() {
        onDiagnostics?.invoke(
            NavigationMapDiagnostics(
                viewCreatedCount = viewCreatedCount,
                mapCreatedCount = mapCreatedCount,
                routeLayerRebuildCount = routeLayerRebuildCount,
                stopMutationCount = stopMutationCount,
                renderPassCount = renderPassCount
            )
        )
    }

    fun dispose() {
        disposed = true
        runCatching { pause() }
        map?.let { activeMap ->
            routeLines.forEach { line -> runCatching { activeMap.removeLine(line) } }
            riderPin?.let { pin -> runCatching { activeMap.removePin(pin) } }
            stopPins.values.forEach { rendered -> runCatching { activeMap.removePin(rendered.pin) } }
        }
        routeLines.clear()
        stopPins.clear()
        riderPin = null
        map = null
        view = null
    }
}

/**
 * Standalone navigation-map surface. Keep stateKey stable for the Rider account/session,
 * never an order id. Recomposition/refresh updates overlays only; AndroidView factory owns
 * the single MapGLSurfaceView instance for this composition.
 */
@Composable
fun RiderNavigationMap(
    stateKey: String,
    state: RiderNavigationMapState,
    modifier: Modifier = Modifier,
    initialCamera: NavigationMapCamera? = null,
    onMapReady: (Boolean) -> Unit = {},
    onCameraChanged: (NavigationMapCamera) -> Unit = {},
    onDiagnostics: ((NavigationMapDiagnostics) -> Unit)? = null
) {
    require(stateKey.isNotBlank()) { "Navigation map stateKey must be stable and non-blank" }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentState = rememberUpdatedState(state)
    val currentReady = rememberUpdatedState(onMapReady)
    val currentCameraChanged = rememberUpdatedState(onCameraChanged)
    val currentDiagnostics = rememberUpdatedState(onDiagnostics)
    val holder = remember(stateKey) {
        RiderNavigationMapHolder(
            onCameraChanged = { currentCameraChanged.value(it) },
            onDiagnostics = { currentDiagnostics.value?.invoke(it) }
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            (LayoutInflater.from(context)
                .inflate(SharedR.layout.view_longdo_shared_map, null, false) as MapGLSurfaceView).also { view ->
                holder.view = view
                // Keep the GL context across short background/resume cycles so returning to
                // QueueGo does not force the SDK to reconstruct already-rendered map tiles.
                view.setPreserveEGLContextOnPause(true)
                holder.markViewCreated()
                view.setListener(object : IMapListener {
                    override fun onMapCreated(map: com.longdo.api.Map) {
                        view.post {
                            holder.mapCreated(view)
                            if (holder.disposed) return@post
                            runCatching {
                                holder.attachMap(map)
                                map.setBase(LongdoLayer(context, "normal", 0, 1, 20).apply {
                                    layerURL = "https://ms.longdo.com/map/msn-server/tile.php"
                                })
                                map.setDrawCrossSign(false)
                                val start = initialCamera?.point?.takeIf { it.valid }
                                    ?: currentState.value.rider?.point?.takeIf { it.valid }
                                    ?: currentState.value.remainingStops().firstOrNull()?.point
                                    ?: NavigationMapPoint(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
                                map.setLocation(MapLocation(start.longitude, start.latitude))
                                map.setZoom(initialCamera?.zoom?.coerceIn(4, 20) ?: 15)
                                initialCamera?.bearingDegrees
                                    ?.takeIf { it.isFinite() }
                                    ?.let {
                                        map.setCanRotateCamera(true)
                                        map.rotate(((it % 360f) + 360f) % 360f, false)
                                    }
                                holder.render(currentState.value)
                            }.onSuccess {
                                currentReady.value(true)
                            }.onFailure {
                                Log.e("QueueGoNavMap", "map_setup_failed", it)
                                currentReady.value(false)
                            }
                        }
                    }
                })
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) holder.resume()
            }
        },
        update = {
            holder.render(state)
        }
    )

    DisposableEffect(lifecycle, stateKey) {
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
