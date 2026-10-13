package com.queuego.ridernavigationmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
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

private const val RIDER_MARKER_DEFAULT_ANIMATION_SECONDS = 0.72
private const val RIDER_MARKER_MIN_ANIMATION_SECONDS = 0.18
private const val RIDER_MARKER_MAX_ANIMATION_SECONDS = 0.85
private const val RIDER_MARKER_ANIMATION_CADENCE_RATIO = 0.82
private const val RIDER_MARKER_TELEPORT_METERS = 120.0
private const val CAMERA_ZOOM_MIN_INTERVAL_MS = 1_800L
private const val DEFAULT_LONGITUDE = 100.5018
private const val DEFAULT_LATITUDE = 13.7563
private const val SNAPSHOT_CAPTURE_THROTTLE_MS = 1_200L
private const val SNAPSHOT_CAPTURE_DELAY_MS = 180L
private const val SNAPSHOT_BRIDGE_SETTLE_MS = 500L
private const val SNAPSHOT_BRIDGE_RETRY_MS = 250L
private const val SNAPSHOT_BRIDGE_MAX_ATTEMPTS = 8
private const val RENDERER_WATCH_INTERVAL_MS = 100L
private const val RENDERER_WATCH_MAX_ATTEMPTS = 50

private val ACTIVE_ROUTE_COLOR = floatArrayOf(230f / 255f, 0f, 45f / 255f, 0.96f)
private val FUTURE_ROUTE_COLOR = floatArrayOf(230f / 255f, 0f, 45f / 255f, 0.28f)

private data class RenderedStop(val signature: String, val pin: Pin)

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
    previousRenderedFix: NavigationMapFix?,
    nextFix: NavigationMapFix
): Double {
    val previousNanos = previousRenderedFix?.elapsedRealtimeNanos ?: 0L
    val nextNanos = nextFix.elapsedRealtimeNanos
    if (previousNanos <= 0L || nextNanos <= previousNanos) {
        return RIDER_MARKER_DEFAULT_ANIMATION_SECONDS
    }
    val cadenceSeconds = (nextNanos - previousNanos).toDouble() / 1_000_000_000.0
    return (cadenceSeconds * RIDER_MARKER_ANIMATION_CADENCE_RATIO)
        .coerceIn(RIDER_MARKER_MIN_ANIMATION_SECONDS, RIDER_MARKER_MAX_ANIMATION_SECONDS)
}

private class RiderNavigationMapHolder {
    private var onCameraChanged: (NavigationMapCamera) -> Unit = {}
    private var onDiagnostics: ((NavigationMapDiagnostics) -> Unit)? = null
    var container: FrameLayout? = null
    var view: MapGLSurfaceView? = null
    var snapshotOverlay: ImageView? = null
    var map: com.longdo.api.Map? = null
    var disposed = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var snapshotBitmap: Bitmap? = null
    private var snapshotCaptureInFlight = false
    private var snapshotRefreshScheduled = false
    private var lastSnapshotCaptureAtMs = 0L
    private var bridgeGeneration = 0
    private var pendingBridgeWithoutSnapshot = false
    private var rendererWatchGeneration = 0
    private var announcedReplacementMap: com.longdo.api.Map? = null
    private var lastKnownCamera: NavigationMapCamera? = null
    private var lastState = RiderNavigationMapState()
    private var riderPin: Pin? = null
    private var lastRiderFix: NavigationMapFix? = null
    private var lastRenderedRiderFix: NavigationMapFix? = null
    private var lastCameraFix: NavigationMapPoint? = null
    private var lastCameraCenter: NavigationMapPoint? = null
    private var lastCameraBearing: Float? = null
    private var lastCameraZoomChangeAtMs: Long = 0L
    private val stopPins = linkedMapOf<String, RenderedStop>()
    private val routeLines = mutableListOf<Line>()
    private var renderedRouteSegments: List<NavigationRouteSegment> = emptyList()
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
    private var snapshotCaptureCount = 0
    private var snapshotBridgeCount = 0
    private var snapshotBridgeFailureCount = 0
    private var mapReplacementCount = 0
    private var snapshotVisible = false

    fun bindCallbacks(
        cameraChanged: (NavigationMapCamera) -> Unit,
        diagnostics: ((NavigationMapDiagnostics) -> Unit)?
    ) {
        // Binding happens during composition; never emit diagnostics from here or a callback
        // that writes Compose state could create a recomposition loop.
        onCameraChanged = cameraChanged
        onDiagnostics = diagnostics
    }

    fun unbindCallbacks() {
        onCameraChanged = {}
        onDiagnostics = null
    }

    fun attachContainer(root: FrameLayout, overlay: ImageView) {
        container = root
        snapshotOverlay = overlay
        snapshotBitmap?.let { bitmap ->
            overlay.setImageBitmap(bitmap)
            overlay.visibility = if (snapshotVisible) View.VISIBLE else View.GONE
            if (snapshotVisible) overlay.bringToFront()
        }
    }

    private fun replaceSnapshot(next: Bitmap) {
        val previous = snapshotBitmap
        snapshotBitmap = next
        snapshotOverlay?.setImageBitmap(next)
        if (previous != null && previous !== next && !previous.isRecycled) previous.recycle()
    }

    private fun setSnapshotVisible(visible: Boolean, countBridge: Boolean = false) {
        val overlay = snapshotOverlay
        if (visible && snapshotBitmap != null && overlay != null) {
            overlay.setImageBitmap(snapshotBitmap)
            overlay.visibility = View.VISIBLE
            overlay.bringToFront()
            if (!snapshotVisible && countBridge) snapshotBridgeCount += 1
            snapshotVisible = true
        } else if (!visible) {
            overlay?.visibility = View.GONE
            snapshotVisible = false
        }
        publishDiagnostics()
    }

    private fun requestSnapshot(
        force: Boolean,
        bridgeToken: Int? = null,
        attempt: Int = 0
    ) {
        val activeView = view ?: return
        if (disposed || activeView.width <= 0 || activeView.height <= 0) {
            if (bridgeToken != null && attempt < SNAPSHOT_BRIDGE_MAX_ATTEMPTS) {
                mainHandler.postDelayed(
                    { requestSnapshot(true, bridgeToken, attempt + 1) },
                    SNAPSHOT_BRIDGE_RETRY_MS
                )
            }
            return
        }
        if (snapshotCaptureInFlight) {
            if (bridgeToken != null && attempt < SNAPSHOT_BRIDGE_MAX_ATTEMPTS) {
                mainHandler.postDelayed(
                    { requestSnapshot(true, bridgeToken, attempt + 1) },
                    SNAPSHOT_BRIDGE_RETRY_MS
                )
            }
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!force && (snapshotVisible || now - lastSnapshotCaptureAtMs < SNAPSHOT_CAPTURE_THROTTLE_MS)) return

        val width = activeView.width.coerceAtLeast(1)
        val height = activeView.height.coerceAtLeast(1)
        val target = runCatching {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return

        snapshotCaptureInFlight = true
        runCatching {
            PixelCopy.request(activeView, target, { result ->
                snapshotCaptureInFlight = false
                if (disposed) {
                    if (!target.isRecycled) target.recycle()
                    return@request
                }
                if (result == PixelCopy.SUCCESS) {
                    replaceSnapshot(target)
                    lastSnapshotCaptureAtMs = SystemClock.elapsedRealtime()
                    snapshotCaptureCount += 1
                    if (bridgeToken != null && bridgeToken == bridgeGeneration) {
                        setSnapshotVisible(false)
                    } else if (pendingBridgeWithoutSnapshot) {
                        pendingBridgeWithoutSnapshot = false
                        setSnapshotVisible(true)
                        settleContinuityBridge()
                    } else {
                        publishDiagnostics()
                    }
                } else {
                    if (!target.isRecycled) target.recycle()
                    if (bridgeToken != null && bridgeToken == bridgeGeneration) {
                        if (attempt < SNAPSHOT_BRIDGE_MAX_ATTEMPTS) {
                            mainHandler.postDelayed(
                                { requestSnapshot(true, bridgeToken, attempt + 1) },
                                SNAPSHOT_BRIDGE_RETRY_MS
                            )
                        } else {
                            snapshotBridgeFailureCount += 1
                            setSnapshotVisible(false)
                        }
                    }
                }
            }, mainHandler)
        }.onFailure {
            snapshotCaptureInFlight = false
            if (!target.isRecycled) target.recycle()
            if (bridgeToken != null && bridgeToken == bridgeGeneration) {
                if (attempt < SNAPSHOT_BRIDGE_MAX_ATTEMPTS) {
                    mainHandler.postDelayed(
                        { requestSnapshot(true, bridgeToken, attempt + 1) },
                        SNAPSHOT_BRIDGE_RETRY_MS
                    )
                } else {
                    snapshotBridgeFailureCount += 1
                    setSnapshotVisible(false)
                }
            }
        }
    }

    private fun scheduleSnapshotRefresh() {
        if (disposed || snapshotVisible || snapshotRefreshScheduled) return
        snapshotRefreshScheduled = true
        mainHandler.postDelayed({
            snapshotRefreshScheduled = false
            requestSnapshot(force = false)
        }, SNAPSHOT_CAPTURE_DELAY_MS)
    }

    private fun showContinuityBridge() {
        if (!snapshotVisible) {
            // Count the bridge request at renderer-swap detection time. Visibility can follow
            // a few frames later if PixelCopy is still finishing the last good SurfaceView frame.
            snapshotBridgeCount += 1
        }
        if (snapshotBitmap == null) {
            pendingBridgeWithoutSnapshot = true
            requestSnapshot(force = true)
            publishDiagnostics()
            return
        }
        pendingBridgeWithoutSnapshot = false
        setSnapshotVisible(true)
    }

    private fun settleContinuityBridge() {
        if (!snapshotVisible) {
            scheduleSnapshotRefresh()
            return
        }
        val token = ++bridgeGeneration
        mainHandler.postDelayed(
            { requestSnapshot(force = true, bridgeToken = token) },
            SNAPSHOT_BRIDGE_SETTLE_MS
        )
    }

    fun markViewCreated() {
        viewCreatedCount += 1
        publishDiagnostics()
    }

    private fun announceRendererReplacement(nextMap: com.longdo.api.Map) {
        if (map == null || map === nextMap || announcedReplacementMap === nextMap) return
        announcedReplacementMap = nextMap
        mapReplacementCount += 1
        showContinuityBridge()
    }

    fun attachMap(nextMap: com.longdo.api.Map): Boolean {
        if (map === nextMap) return false
        if (map != null && announcedReplacementMap !== nextMap) {
            announceRendererReplacement(nextMap)
        }
        map = nextMap
        announcedReplacementMap = null
        riderPin = null
        stopPins.clear()
        routeLines.clear()
        renderedRouteSegments = emptyList()
        stopSource = null
        stopActiveId = null
        routeSource = null
        routeActiveId = null
        routeRemainingStopIds = emptySet()
        lastCameraFix = null
        lastCameraCenter = null
        lastCameraZoomChangeAtMs = 0L
        lastRenderedRiderFix = null
        // Preserve heading continuity across an internal Longdo renderer swap.
        mapCreatedCount += 1
        publishDiagnostics()
        return true
    }

    fun configureMap(
        context: android.content.Context,
        nextMap: com.longdo.api.Map,
        initialCamera: NavigationMapCamera? = null
    ): Boolean {
        val replacingExistingMap = map != null && map !== nextMap
        if (replacingExistingMap) announceRendererReplacement(nextMap)
        val newSdkMap = attachMap(nextMap)
        if (!newSdkMap) return false

        val continuityCamera = if (replacingExistingMap) lastKnownCamera else null
        val camera = continuityCamera ?: initialCamera
        runCatching {
            nextMap.setDoTileAnimation(false)
            nextMap.setDoPreShowCache(true)
            nextMap.setBase(LongdoLayer(context, "normal", 0, 1, 20).apply {
                layerURL = "https://ms.longdo.com/map/msn-server/tile.php"
                setDoPreDrawCache(true)
            })
            nextMap.setDrawCrossSign(false)
            nextMap.setCanRotateCamera(true)
            val start = camera?.point?.takeIf { it.valid }
                ?: lastState.rider?.point?.takeIf { it.valid }
                ?: lastState.remainingStops().firstOrNull()?.point
                ?: NavigationMapPoint(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
            nextMap.setLocation(MapLocation(start.longitude, start.latitude))
            nextMap.setZoom(camera?.zoom?.coerceIn(4, 20) ?: 15)
            val bearing = camera?.bearingDegrees ?: lastCameraBearing
            bearing?.takeIf { it.isFinite() }?.let {
                nextMap.rotate(((it % 360f) + 360f) % 360f, false)
            }
            render(lastState)
        }.onFailure {
            Log.e("QueueGoNavMap", "map_replacement_setup_failed", it)
            throw it
        }

        if (replacingExistingMap) settleContinuityBridge()
        return true
    }

    private fun adoptSilentRendererReplacement(activeView: MapGLSurfaceView): Boolean {
        val rendererMap = runCatching { activeView.renderer?.map }.getOrNull()
        val activeMap = map
        if (rendererMap == null || activeMap == null || rendererMap === activeMap) return false
        configureMap(activeView.context, rendererMap)
        return true
    }

    private fun watchRendererIdentity(generation: Int, attempt: Int = 0) {
        if (disposed || !resumed || generation != rendererWatchGeneration) return
        val activeView = view ?: return
        val replaced = runCatching { adoptSilentRendererReplacement(activeView) }
            .onFailure { Log.e("QueueGoNavMap", "renderer_identity_watch_failed", it) }
            .getOrDefault(false)
        if (replaced) return
        if (attempt < RENDERER_WATCH_MAX_ATTEMPTS) {
            mainHandler.postDelayed(
                { watchRendererIdentity(generation, attempt + 1) },
                RENDERER_WATCH_INTERVAL_MS
            )
        }
    }

    private fun startRendererIdentityWatch() {
        val generation = ++rendererWatchGeneration
        watchRendererIdentity(generation)
    }

    fun resume() {
        val activeView = view ?: return
        if (disposed) return
        // Longdo can replace renderer.map after ON_RESUME returns and may never emit
        // onMapCreated for that replacement. Check immediately and then keep a short bounded
        // watcher alive so the replacement is adopted through configureMap() as soon as it exists.
        val replacementPending = runCatching { adoptSilentRendererReplacement(activeView) }
            .onFailure { Log.e("QueueGoNavMap", "renderer_resume_adoption_failed", it) }
            .getOrDefault(false)
        // Longdo's Activity lifecycle does not require MapGLSurfaceView.onResume().
        // Calling it after ordinary background/foreground transitions can replace renderer.map
        // and visibly reassemble the map even though the retained View itself never changed.
        resumed = true
        // Deliberately keep lastRiderFix, map, pins and route layers. A refresh/resume must
        // never reconstruct the map just because Rider/order state changed.
        render(lastState)
        startRendererIdentityWatch()
        if (!replacementPending) settleContinuityBridge()
    }

    private fun captureCamera() {
        map?.let { activeMap ->
            runCatching {
                val location = activeMap.getLocation()
                val camera = NavigationMapCamera(
                    point = NavigationMapPoint(location.lat, location.lon),
                    zoom = activeMap.getZoom(),
                    bearingDegrees = lastCameraBearing
                )
                lastKnownCamera = camera
                onCameraChanged(camera)
            }
        }
    }

    fun retainAcrossVisibilityLoss() {
        rendererWatchGeneration += 1
        // Tab switches and ordinary background/foreground cycles keep the SDK renderer alive.
        // Capture a frozen frame quietly for emergency continuity, but do not cover a healthy
        // retained renderer. The bridge is shown only when attachMap() detects a real map
        // replacement from Android/Longdo.
        captureCamera()
        requestSnapshot(force = true)
    }

    fun shutdownSdk() {
        captureCamera()
        if (!resumed) return
        val activeView = view ?: run {
            resumed = false
            return
        }
        val rendererReady = runCatching { activeView.renderer?.map != null }
            .onFailure { Log.w("QueueGoNavMap", "map_renderer_state_failed", it) }
            .getOrDefault(false)
        if (rendererReady) {
            runCatching { activeView.onPause() }
                .onFailure { Log.w("QueueGoNavMap", "map_on_pause_failed", it) }
        }
        resumed = false
    }

    fun render(state: RiderNavigationMapState) {
        lastState = state
        val activeMap = map ?: return
        if (disposed) return
        renderPassCount += 1

        state.rider?.takeIf { it.point.valid }?.let { nextFix ->
            if (riderPin == null || shouldMoveRiderMarker(lastRenderedRiderFix, nextFix)) {
                renderRider(activeMap, nextFix)
            }
            lastRiderFix = nextFix
        }
        renderStops(activeMap, state)
        renderRoutes(activeMap, state)

        val followedFix = state.rider?.takeIf { it.point.valid } ?: lastRiderFix
        if (state.followRider && followedFix != null) {
            followRider(activeMap, state.copy(rider = followedFix))
        }
        // Publish once per applied state even when overlays are structurally unchanged.
        // This distinguishes a stable frame from a state update that never reached the map.
        // PixelCopy is deliberately not scheduled on every GPS tick: continuity snapshots
        // are captured on visibility loss or real renderer replacement so live navigation
        // never competes with screenshot work for the render thread.
        publishDiagnostics()
    }

    private fun renderRider(activeMap: com.longdo.api.Map, fix: NavigationMapFix) {
        val location = MapLocation(fix.point.longitude, fix.point.latitude)
        val existing = riderPin
        if (existing == null) {
            val pin = Pin(location, markerBitmap(SharedR.drawable.qg_map_rider))
            runCatching { activeMap.pushPin(pin) }
                .onSuccess {
                    riderPin = pin
                    lastRenderedRiderFix = fix
                }
        } else {
            val previous = lastRenderedRiderFix
            val distance = previous?.let { navigationHaversineMeters(it.point, fix.point) }
            val update = runCatching {
                if (distance != null && distance >= RIDER_MARKER_TELEPORT_METERS) {
                    // Never animate a stale/teleport correction across the whole screen.
                    existing.setLocation(location)
                } else {
                    existing.setLocation(
                        location,
                        true,
                        riderMarkerAnimationSeconds(previous, fix)
                    )
                }
            }
            if (update.isSuccess) {
                lastRenderedRiderFix = fix
            } else {
                runCatching { activeMap.removePin(existing) }
                val replacement = Pin(location, markerBitmap(SharedR.drawable.qg_map_rider))
                runCatching { activeMap.pushPin(replacement) }
                    .onSuccess {
                        riderPin = replacement
                        lastRenderedRiderFix = fix
                    }
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

        // Keep the route object order stable: future legs render first and the active leg last.
        val routes = state.styledRemainingRoutes()
            .sortedBy { if (it.style == NavigationRouteStyle.FUTURE) 0 else 1 }

        val canMutateInPlace =
            routeLines.size == routes.size &&
                renderedRouteSegments.size == routes.size &&
                renderedRouteSegments.zip(routes).all { (before, after) ->
                    before.id == after.id &&
                        before.toStopId == after.toStopId &&
                        before.style == after.style
                }

        if (canMutateInPlace) {
            var changed = false
            var mutationSucceeded = true
            routes.forEachIndexed { index, segment ->
                val previous = renderedRouteSegments[index]
                if (previous.points != segment.points) {
                    changed = true
                    val locations = segment.points
                        .map { MapLocation(it.longitude, it.latitude) }
                        .toTypedArray()
                    if (runCatching { routeLines[index].setLocation(locations) }.isFailure) {
                        mutationSucceeded = false
                    }
                }
            }
            if (mutationSucceeded) {
                renderedRouteSegments = routes
                routeSource = state.routes
                routeActiveId = activeStopId
                routeRemainingStopIds = remainingStopIds
                if (changed) routeLayerRebuildCount += 1
                publishDiagnostics()
                return
            }
        }

        // Topology/style changed or an in-place mutation failed: rebuild once as recovery.
        val failedToRemove = routeLines.filter { line ->
            runCatching { activeMap.removeLine(line) }.isFailure
        }
        routeLines.clear()
        renderedRouteSegments = emptyList()
        routeLines.addAll(failedToRemove)
        if (failedToRemove.isNotEmpty()) {
            routeSource = null
            routeActiveId = null
            routeRemainingStopIds = emptySet()
            publishDiagnostics()
            return
        }

        var renderSucceeded = true
        routes.forEach { segment ->
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
            renderedRouteSegments = routes
            routeSource = state.routes
            routeActiveId = activeStopId
            routeRemainingStopIds = remainingStopIds
            routeLayerRebuildCount += 1
        } else {
            // Keep partial route layers visible only until the next render can clean/rebuild them.
            renderedRouteSegments = emptyList()
            routeSource = null
            routeActiveId = null
            routeRemainingStopIds = emptySet()
        }
        publishDiagnostics()
    }

    private fun followRider(activeMap: com.longdo.api.Map, state: RiderNavigationMapState) {
        val fix = state.rider ?: return
        val decision = navigationCameraDecision(state, lastCameraBearing) ?: return
        val speed = fix.speedMetersPerSecond
        val movementThreshold = navigationCameraMovementThresholdMeters(speed)
        val centerMoved = lastCameraCenter == null ||
            navigationHaversineMeters(lastCameraCenter!!, decision.center) >= movementThreshold
        val bearingChanged = navigationBearingChangedEnough(
            lastCameraBearing,
            decision.bearingDegrees,
            minimumDeltaDegrees = 2.5f
        )

        val now = SystemClock.elapsedRealtime()
        val currentZoom = runCatching { activeMap.getZoom() }.getOrNull()
        val requestedZoomChange = currentZoom == null || currentZoom != decision.zoom
        val zoomChangeAllowed = requestedZoomChange && (
            lastCameraZoomChangeAtMs == 0L ||
                now - lastCameraZoomChangeAtMs >= CAMERA_ZOOM_MIN_INTERVAL_MS ||
                (currentZoom != null && kotlin.math.abs(currentZoom - decision.zoom) >= 2)
            )

        if (!centerMoved && !bearingChanged && !zoomChangeAllowed) return

        runCatching {
            // Use Longdo's native camera animation instead of jumping the viewport every GPS tick.
            if (centerMoved) {
                activeMap.setLocation(
                    MapLocation(decision.center.longitude, decision.center.latitude),
                    true
                )
            }
            if (zoomChangeAllowed) {
                activeMap.setZoom(decision.zoom, true)
            }
            if (bearingChanged) {
                decision.bearingDegrees?.let { activeMap.rotate(it, true) }
            }
        }.onSuccess {
            lastCameraFix = fix.point
            if (centerMoved) lastCameraCenter = decision.center
            if (bearingChanged && decision.bearingDegrees != null) {
                lastCameraBearing = decision.bearingDegrees
            }
            if (zoomChangeAllowed) lastCameraZoomChangeAtMs = now
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
                renderPassCount = renderPassCount,
                snapshotCaptureCount = snapshotCaptureCount,
                snapshotBridgeCount = snapshotBridgeCount,
                snapshotBridgeFailureCount = snapshotBridgeFailureCount,
                mapReplacementCount = mapReplacementCount,
                snapshotVisible = snapshotVisible
            )
        )
    }

    fun resetSurface() {
        runCatching { shutdownSdk() }
        map?.let { activeMap ->
            routeLines.forEach { line -> runCatching { activeMap.removeLine(line) } }
            riderPin?.let { pin -> runCatching { activeMap.removePin(pin) } }
            stopPins.values.forEach { rendered -> runCatching { activeMap.removePin(rendered.pin) } }
        }
        routeLines.clear()
        renderedRouteSegments = emptyList()
        stopPins.clear()
        riderPin = null
        map = null
        view = null
        container = null
        snapshotOverlay?.setImageDrawable(null)
        snapshotOverlay = null
        snapshotBitmap?.takeIf { !it.isRecycled }?.recycle()
        snapshotBitmap = null
        mainHandler.removeCallbacksAndMessages(null)
        snapshotCaptureInFlight = false
        snapshotRefreshScheduled = false
        snapshotVisible = false
        pendingBridgeWithoutSnapshot = false
        rendererWatchGeneration += 1
        announcedReplacementMap = null
        lastKnownCamera = null
        lastRenderedRiderFix = null
        lastCameraFix = null
        lastCameraCenter = null
        lastCameraZoomChangeAtMs = 0L
        resumed = false
        stopSource = null
        stopActiveId = null
        routeSource = null
        routeActiveId = null
        routeRemainingStopIds = emptySet()
    }

    fun dispose() {
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
        if (activeKey == stateKey && activeLifecycle === lifecycle &&
            current != null && !current.disposed
        ) return current

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
            // Keep the lifecycle observer registered while Home is unmounted so an Activity
            // destroy cannot leave a retained MapGLSurfaceView holding the old Activity context.
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
 * Standalone navigation-map surface. Keep stateKey stable for the Rider account/session,
 * never an order id. Recomposition/refresh updates overlays only; the account-keyed registry
 * retains the same MapGLSurfaceView when Home is temporarily unmounted for another Rider tab.
 */
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

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentState = rememberUpdatedState(state)
    val currentReady = rememberUpdatedState(onMapReady)
    val currentCameraChanged = rememberUpdatedState(onCameraChanged)
    val currentUserGesture = rememberUpdatedState(onUserGesture)
    val currentDiagnostics = rememberUpdatedState(onDiagnostics)
    val holder = remember(stateKey, lifecycle) { RiderNavigationMapRegistry.acquire(stateKey, lifecycle) }
    holder.bindCallbacks(
        cameraChanged = { currentCameraChanged.value(it) },
        diagnostics = { currentDiagnostics.value?.invoke(it) }
    )

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val retainedRoot = holder.container?.takeIf { it.context === context }
            if (holder.container != null && retainedRoot == null) {
                // Activity/context changed: never retain Views backed by a destroyed Activity.
                holder.resetSurface()
            }
            val root = retainedRoot ?: FrameLayout(context).also { frame ->
                val view = LayoutInflater.from(context)
                    .inflate(SharedR.layout.view_longdo_shared_map, frame, false) as MapGLSurfaceView
                view.setPreserveEGLContextOnPause(true)
                frame.addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                val overlay = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_XY
                    visibility = View.GONE
                    isClickable = false
                    isFocusable = false
                }
                frame.addView(
                    overlay,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                holder.view = view
                holder.attachContainer(frame, overlay)
                holder.markViewCreated()
            }
            (root.parent as? android.view.ViewGroup)?.removeView(root)
            val view = requireNotNull(holder.view)
            val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
            var touchDownX = 0f
            var touchDownY = 0f
            var userGestureReported = false
            view.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownX = event.x
                        touchDownY = event.y
                        userGestureReported = false
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (!userGestureReported) {
                            userGestureReported = true
                            currentUserGesture.value()
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!userGestureReported) {
                            val dx = event.x - touchDownX
                            val dy = event.y - touchDownY
                            if (dx * dx + dy * dy >= touchSlop * touchSlop) {
                                userGestureReported = true
                                currentUserGesture.value()
                            }
                        }
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> userGestureReported = false
                }
                false
            }
            view.setListener(object : IMapListener {
                    override fun onMapCreated(map: com.longdo.api.Map) {
                        view.post {
                            if (holder.disposed) return@post
                            runCatching {
                                // Initial callbacks and delayed replacement callbacks share the
                                // same setup path. Silent renderer swaps are adopted by resume().
                                holder.configureMap(context, map, initialCamera)
                                // A duplicate SDK callback for the same map is only a render retry;
                                // never reset base/camera or force the visible map to assemble again.
                                holder.render(currentState.value)
                                holder.resume()
                            }.onSuccess {
                                currentReady.value(true)
                            }.onFailure {
                                Log.e("QueueGoNavMap", "map_setup_failed", it)
                                currentReady.value(false)
                            }
                        }
                    }
                })
            if (holder.map != null) {
                holder.render(currentState.value)
                currentReady.value(true)
            }
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) holder.resume()
            root
        },
        update = {
            holder.render(state)
        }
    )

    DisposableEffect(lifecycle, stateKey) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.resume()
                Lifecycle.Event.ON_PAUSE -> holder.retainAcrossVisibilityLoss()
                Lifecycle.Event.ON_DESTROY -> RiderNavigationMapRegistry.destroy(stateKey, holder)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder.view?.setOnTouchListener(null)
            if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
                RiderNavigationMapRegistry.destroy(stateKey, holder)
            } else {
                // Tab/screen navigation may unmount Home while the account session remains active.
                // Keep the SDK renderer alive; detaching/reattaching must not trigger Longdo Map Pause.
                RiderNavigationMapRegistry.release(stateKey, holder)
            }
            currentReady.value(false)
        }
    }
}
