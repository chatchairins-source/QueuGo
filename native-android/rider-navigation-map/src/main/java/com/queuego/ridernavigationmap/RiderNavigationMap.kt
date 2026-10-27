package com.queuego.ridernavigationmap

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot

private const val RIDER_MARKER_DEFAULT_ANIMATION_MS = 1_050L
private const val RIDER_MARKER_MIN_ANIMATION_MS = 260L
private const val RIDER_MARKER_MAX_ANIMATION_MS = 1_250L
private const val RIDER_MARKER_ANIMATION_CADENCE_RATIO = 1.05
private const val RIDER_MARKER_TELEPORT_METERS = 120.0
private const val RIDER_ROUTE_ARROW_SPACING_PX = 80.0
private const val RIDER_ROUTE_ARROW_MAX_COUNT = 128
private const val CAMERA_BEARING_MIN_DELTA_DEGREES = 0.7f
private const val CAMERA_ANIMATION_FALLBACK_MS = 560L
private const val CAMERA_OVERVIEW_ANIMATION_MS = 380
private const val DEFAULT_LONGITUDE = 101.0
private const val DEFAULT_LATITUDE = 15.0
private const val RIDER_SOURCE_ID = "qg-rider-puck"
private const val RIDER_BODY_LAYER_ID = "qg-rider-puck-body"
private const val RIDER_ARROW_LAYER_ID = "qg-rider-puck-arrow-layer"
private const val RIDER_ARROW_IMAGE_ID = "qg-rider-puck-arrow"
private const val STOP_SOURCE_ID = "qg-rider-stops"
private const val STOP_SHOP_BODY_LAYER_ID = "qg-rider-stops-shop-body"
private const val STOP_SHOP_DONE_LAYER_ID = "qg-rider-stops-shop-done"
private const val STOP_CUSTOMER_BODY_LAYER_ID = "qg-rider-stops-customer-body"
private const val STOP_SHOP_NUMBER_LAYER_ID = "qg-rider-stops-shop-number"

internal fun navigationStopMarkerTitle(stop: NavigationMapStop): String = buildString {
    append("จุด ")
    append(stop.sequence)
    append(" · ")
    append(if (stop.kind == NavigationStopKind.PICKUP) "ร้านค้า" else "ลูกค้า")
    if (stop.label.isNotBlank()) {
        append(" · ")
        append(stop.label)
    }
}

internal fun riderMarkerMoveThresholdMeters(@Suppress("UNUSED_PARAMETER") speedMetersPerSecond: Float?): Double = 0.01

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
}

internal fun navigationRiderDisplayFix(state: RiderNavigationMapState): NavigationMapFix? {
    val rawFix = state.rider?.takeIf { it.point.valid } ?: return null
    val activeRoute = state.styledRemainingRoutes()
        .firstOrNull { it.style == NavigationRouteStyle.ACTIVE }
    val accuracy = rawFix.accuracyMeters
    val match = if (
        activeRoute != null &&
        accuracy != null &&
        accuracy.isFinite() &&
        accuracy <= 35f
    ) {
        matchNavigationRoute(
            segment = activeRoute,
            point = rawFix.point,
            bearingDegrees = navigationUsableGpsBearing(rawFix),
            speedMetersPerSecond = rawFix.speedMetersPerSecond
        )
    } else null
    val displayPoint = match
        ?.takeIf { it.offRouteMeters <= 25.0 }
        ?.snapped
        ?: rawFix.point
    return rawFix.copy(point = displayPoint)
}

internal fun normalizeRiderPuckBearing(value: Float?): Float? {
    if (value == null || !value.isFinite()) return null
    return ((value % 360f) + 360f) % 360f
}

internal fun riderPuckBearingDelta(from: Float, to: Float): Float =
    ((to - from + 540f) % 360f) - 180f

internal fun smoothRiderPuckHeading(
    previousHeading: Float?,
    rawHeading: Float?,
    speedMetersPerSecond: Float?
): Float? {
    val raw = normalizeRiderPuckBearing(rawHeading) ?: return previousHeading
    val previous = normalizeRiderPuckBearing(previousHeading) ?: return raw
    if (speedMetersPerSecond != null &&
        speedMetersPerSecond.isFinite() &&
        speedMetersPerSecond < 0.8f
    ) return previous
    val delta = riderPuckBearingDelta(previous, raw)
    if (abs(delta) < 2f) return previous
    val gain = when {
        speedMetersPerSecond != null && speedMetersPerSecond.isFinite() &&
            speedMetersPerSecond >= 12f -> 0.42f
        speedMetersPerSecond != null && speedMetersPerSecond.isFinite() &&
            speedMetersPerSecond >= 5f -> 0.34f
        else -> 0.24f
    }
    return normalizeRiderPuckBearing(previous + delta * gain)
}

internal fun interpolateRiderPuckBearing(
    fromHeading: Float?,
    toHeading: Float?,
    fraction: Float
): Float? {
    val from = normalizeRiderPuckBearing(fromHeading)
    val to = normalizeRiderPuckBearing(toHeading)
    if (from == null) return to
    if (to == null) return from
    return normalizeRiderPuckBearing(
        from + riderPuckBearingDelta(from, to) * fraction.coerceIn(0f, 1f)
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
    private var lastRenderedRiderPosition: LatLng? = null
    private var lastRenderedRiderHeading: Float? = null
    private var riderPuckHeading: Float? = null
    private var riderPuckHeadingFix: NavigationMapFix? = null
    private var riderSource: GeoJsonSource? = null
    private var riderStyleInitialized = false
    private var riderAnimator: ValueAnimator? = null
    private val routeLines = mutableListOf<Polyline>()
    private val activeHeadLines = mutableListOf<Polyline>()
    private val stopMarkers = mutableListOf<Marker>()
    private var stopSource: GeoJsonSource? = null
    private var stopStyleInitialized = false
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
    private var lastCameraZoom: Double? = null
    private var lastOverviewSignature: Int? = null
    private var lastAppliedRecenterRequest = 0
    private var onCameraChanged: (NavigationMapCamera) -> Unit = {}
    private var onUserGesture: () -> Unit = {}
    private var onDiagnostics: ((NavigationMapDiagnostics) -> Unit)? = null
    private var onReady: (Boolean) -> Unit = {}
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var gestureReported = false
    private var multiTouchGesture = false
    private var touchSlop = 8f

    fun resetNorth() {
        val activeMap = map ?: return
        val camera = activeMap.cameraPosition
        activeMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(camera).bearing(0.0).build()
            ),
            320
        )
        lastCameraBearing = 0f
    }

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
                        multiTouchGesture = false
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        // Match Web: pinch / rotate / pitch are temporary transforms and
                        // must not abandon FOLLOW. Only a deliberate one-finger pan does.
                        if (event.pointerCount >= 2) multiTouchGesture = true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (event.pointerCount >= 2) {
                            multiTouchGesture = true
                        } else if (
                            !multiTouchGesture &&
                            !gestureReported &&
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
                    MotionEvent.ACTION_CANCEL -> {
                        gestureReported = false
                        multiTouchGesture = false
                    }
                }
                false
            }
            mapView.getMapAsync { readyMap ->
                if (disposed || view !== mapView) return@getMapAsync
                map = readyMap
                mapCreatedCount += 1
                // QueueGo renders one branded compass in the Rider tool stack.
                readyMap.uiSettings.isCompassEnabled = false
                // Match the Web presentation: no MapLibre logo watermark, but keep
                // source attribution visible.
                readyMap.uiSettings.isLogoEnabled = false
                readyMap.uiSettings.isAttributionEnabled = true
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
        renderRider(activeMap, state)
        renderCamera(activeMap, state, force)
        publishDiagnostics()
    }

    private fun renderRoutes(
        activeMap: MapLibreMap,
        state: RiderNavigationMapState,
        force: Boolean
    ) {
        val routes = state.styledRemainingRoutes().map { segment ->
            // The existing signal pipeline already owns confidence-bounded road snapping.
            // Trim only a renderer copy; preserve coordinator matching/ETA/reroute thresholds.
            val fix = state.rider
            val accuracy = fix?.accuracyMeters
            val match = fix?.let {
                matchNavigationRoute(
                    segment = segment,
                    point = it.point,
                    bearingDegrees = navigationUsableGpsBearing(it),
                    speedMetersPerSecond = it.speedMetersPerSecond
                )
            }
            if (segment.style == NavigationRouteStyle.ACTIVE && accuracy != null &&
                accuracy.isFinite() && accuracy <= 35f && match != null &&
                match.offRouteMeters <= 25.0
            ) trimNavigationRouteSegment(segment, match.progressMeters, match.snapped)
            else segment
        }
        val signature = routes.hashCode()
        if (!force && signature == lastRouteSignature) return
        routeLines.toList().forEach { line -> runCatching { activeMap.removePolyline(line) } }
        routeLines.clear()
        activeHeadLines.clear()
        routes.sortedBy { if (it.style == NavigationRouteStyle.FUTURE) 0 else 1 }
            .forEach { segment ->
                if (!segment.renderable) return@forEach
                val color = if (segment.style == NavigationRouteStyle.ACTIVE) {
                    android.graphics.Color.rgb(230, 0, 45)
                } else {
                    android.graphics.Color.argb(72, 230, 0, 45)
                }
                val width = if (segment.style == NavigationRouteStyle.ACTIVE) 12f else 8f
                val firstLineIndex = routeLines.size
                // Keep the route distinct from pale roads/buildings at driving tilt.
                // Store casing and fill in the same list so refresh/dispose removes both.
                routeLines += activeMap.addPolyline(
                    PolylineOptions()
                        .addAll(segment.points.map { LatLng(it.latitude, it.longitude) })
                        .color(android.graphics.Color.WHITE)
                        .width(width + 4f)
                )
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
                if (segment.style == NavigationRouteStyle.ACTIVE) {
                    activeHeadLines += routeLines.subList(firstLineIndex, routeLines.size)
                    renderDirectionArrows(activeMap, segment)
                }
            }
        lastRenderedRiderPosition?.let { alignActiveRouteHead(activeMap, it) }
        lastRouteSignature = signature
        routeLayerRebuildCount += 1
        routeGeometryUpdateCount += 1
    }

    private fun alignActiveRouteHead(activeMap: MapLibreMap, position: LatLng) {
        // Presentation follows the exact interpolated puck, not the next GPS target.
        // Authoritative route/matching/ETA geometry remains in NavigationRouteCoordinator.
        activeHeadLines.forEach { line ->
            val first = line.points.firstOrNull()
            val offset = first?.let {
                navigationHaversineMeters(
                    NavigationMapPoint(position.latitude, position.longitude),
                    NavigationMapPoint(it.latitude, it.longitude)
                )
            } ?: Double.POSITIVE_INFINITY
            if (line.points.size >= 2 && offset <= 12.0) {
                line.points = listOf(position) + line.points.drop(1)
                activeMap.updatePolyline(line)
            }
        }
    }

    private fun renderDirectionArrows(activeMap: MapLibreMap, segment: NavigationRouteSegment) {
        val projected = segment.points.map { point ->
            activeMap.projection.toScreenLocation(LatLng(point.latitude, point.longitude))
        }
        if (projected.size < 2) return
        var walkedPixels = 0.0
        var nextArrowPixels = RIDER_ROUTE_ARROW_SPACING_PX / 2.0
        var count = 0
        projected.zipWithNext().forEach { (from, to) ->
            if (count >= RIDER_ROUTE_ARROW_MAX_COUNT) return
            val dx = (to.x - from.x).toDouble()
            val dy = (to.y - from.y).toDouble()
            val segmentPixels = hypot(dx, dy)
            if (segmentPixels <= 0.5) return@forEach
            val ux = dx / segmentPixels
            val uy = dy / segmentPixels
            while (nextArrowPixels <= walkedPixels + segmentPixels && count < RIDER_ROUTE_ARROW_MAX_COUNT) {
                val fraction = ((nextArrowPixels - walkedPixels) / segmentPixels).coerceIn(0.0, 1.0)
                val tipX = from.x + dx * fraction
                val tipY = from.y + dy * fraction
                fun screenPoint(x: Double, y: Double): LatLng = activeMap.projection.fromScreenLocation(
                    android.graphics.PointF(x.toFloat(), y.toFloat())
                )
                val left = screenPoint(tipX - ux * 9.0 - uy * 6.0, tipY - uy * 9.0 + ux * 6.0)
                val tip = screenPoint(tipX, tipY)
                val right = screenPoint(tipX - ux * 9.0 + uy * 6.0, tipY - uy * 9.0 - ux * 6.0)
                routeLines += activeMap.addPolyline(
                    PolylineOptions().addAll(listOf(left, tip, right))
                        .color(android.graphics.Color.WHITE).width(3.5f)
                )
                nextArrowPixels += RIDER_ROUTE_ARROW_SPACING_PX
                count += 1
            }
            walkedPixels += segmentPixels
        }
    }

    private fun stopFeatureCollectionJson(
        stops: List<NavigationMapStop>
    ): String {
        val features = JSONArray()
        stops.forEach { stop ->
            val properties = JSONObject()
                .put("sequence", stop.sequence.toString())
                .put("kind", if (stop.kind == NavigationStopKind.PICKUP) "shop" else "customer")
                .put("numbered", stop.numbered)
                .put("completed", stop.completed)
                .put("label", stop.label)
            val coordinates = JSONArray()
                .put(stop.point.longitude)
                .put(stop.point.latitude)
            val geometry = JSONObject()
                .put("type", "Point")
                .put("coordinates", coordinates)
            features.put(
                JSONObject()
                    .put("type", "Feature")
                    .put("geometry", geometry)
                    .put("properties", properties)
            )
        }
        return JSONObject()
            .put("type", "FeatureCollection")
            .put("features", features)
            .toString()
    }

    private fun ensureStopStyle(activeMap: MapLibreMap): GeoJsonSource? {
        if (stopStyleInitialized) stopSource?.let { return it }
        val style = activeMap.style ?: return null
        val source = GeoJsonSource(
            STOP_SOURCE_ID,
            """{"type":"FeatureCollection","features":[]}"""
        )
        style.addSource(source)

        val shopFilter = Expression.eq(Expression.get("kind"), Expression.literal("shop"))
        val customerFilter = Expression.eq(Expression.get("kind"), Expression.literal("customer"))
        val numberedFilter = Expression.eq(Expression.get("numbered"), Expression.literal(true))
        val completedFilter = Expression.eq(Expression.get("completed"), Expression.literal(true))
        style.addLayer(
            CircleLayer(STOP_SHOP_BODY_LAYER_ID, STOP_SOURCE_ID).apply {
                setFilter(shopFilter)
                setProperties(
                    PropertyFactory.circleRadius(10f),
                    PropertyFactory.circleColor(android.graphics.Color.rgb(22, 163, 74)),
                    PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                    PropertyFactory.circleStrokeWidth(3f)
                )
            }
        )
        style.addLayer(
            CircleLayer(STOP_SHOP_DONE_LAYER_ID, STOP_SOURCE_ID).apply {
                setFilter(Expression.all(shopFilter, numberedFilter, completedFilter))
                setProperties(
                    PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleColor(android.graphics.Color.rgb(148, 163, 184)),
                    PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                    PropertyFactory.circleStrokeWidth(3f)
                )
            }
        )
        style.addLayer(
            CircleLayer(STOP_CUSTOMER_BODY_LAYER_ID, STOP_SOURCE_ID).apply {
                setFilter(customerFilter)
                setProperties(
                    PropertyFactory.circleRadius(10f),
                    PropertyFactory.circleColor(android.graphics.Color.rgb(230, 0, 45)),
                    PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                    PropertyFactory.circleStrokeWidth(3f)
                )
            }
        )
        style.addLayer(
            SymbolLayer(STOP_SHOP_NUMBER_LAYER_ID, STOP_SOURCE_ID).apply {
                setFilter(
                    Expression.all(
                        shopFilter,
                        Expression.eq(Expression.get("numbered"), Expression.literal(true))
                    )
                )
                setProperties(
                    PropertyFactory.textField(Expression.get("sequence")),
                    PropertyFactory.textSize(10f),
                    PropertyFactory.textColor(android.graphics.Color.WHITE),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.textIgnorePlacement(true)
                )
            }
        )
        stopStyleInitialized = true
        stopSource = source
        return source
    }

    private fun renderStops(
        activeMap: MapLibreMap,
        state: RiderNavigationMapState,
        force: Boolean
    ) {
        val stops = state.stops.asSequence()
            .filter { it.point.valid && it.id.isNotBlank() && it.sequence > 0 }
            .sortedWith(compareBy<NavigationMapStop> { it.sequence }.thenBy { it.id })
            .toList()
        val signature = stops.hashCode()
        if (!force && signature == lastStopSignature) return
        stopMarkers.toList().forEach { marker -> runCatching { activeMap.removeMarker(marker) } }
        stopMarkers.clear()
        val source = ensureStopStyle(activeMap) ?: return
        source.setGeoJson(stopFeatureCollectionJson(stops))
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

    private fun riderFeatureCollectionJson(point: LatLng, bearingDegrees: Float?): String {
        val properties = JSONObject()
            .put("bearing", normalizeRiderPuckBearing(bearingDegrees)?.toDouble() ?: 0.0)
        val coordinates = JSONArray()
            .put(point.longitude)
            .put(point.latitude)
        val geometry = JSONObject()
            .put("type", "Point")
            .put("coordinates", coordinates)
        val feature = JSONObject()
            .put("type", "Feature")
            .put("geometry", geometry)
            .put("properties", properties)
        return JSONObject()
            .put("type", "FeatureCollection")
            .put("features", JSONArray().put(feature))
            .toString()
    }

    private fun ensureRiderStyle(activeMap: MapLibreMap): GeoJsonSource? {
        if (riderStyleInitialized) riderSource?.let { return it }
        val style = activeMap.style ?: return null
        val context = view?.context ?: return null
        val arrow = context.getDrawable(R.drawable.qg_rider_puck_arrow) ?: return null
        val source = GeoJsonSource(
            RIDER_SOURCE_ID,
            """{"type":"FeatureCollection","features":[]}"""
        )
        style.addSource(source)
        style.addImage(RIDER_ARROW_IMAGE_ID, arrow)
        style.addLayer(
            CircleLayer(RIDER_BODY_LAYER_ID, RIDER_SOURCE_ID).apply {
                setProperties(
                    PropertyFactory.circleRadius(24f),
                    PropertyFactory.circleColor(android.graphics.Color.rgb(230, 0, 45)),
                    PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                    PropertyFactory.circleStrokeWidth(4f)
                )
            }
        )
        style.addLayer(
            SymbolLayer(RIDER_ARROW_LAYER_ID, RIDER_SOURCE_ID).apply {
                setProperties(
                    PropertyFactory.iconImage(RIDER_ARROW_IMAGE_ID),
                    PropertyFactory.iconSize(1f),
                    PropertyFactory.iconRotate(Expression.get("bearing")),
                    PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                    PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
            }
        )
        riderSource = source
        riderStyleInitialized = true
        return source
    }

    private fun resolvedRiderPuckHeading(fix: NavigationMapFix?): Float? {
        if (fix == null) return riderPuckHeading
        if (fix === riderPuckHeadingFix) return riderPuckHeading
        riderPuckHeadingFix = fix
        riderPuckHeading = smoothRiderPuckHeading(
            previousHeading = riderPuckHeading,
            rawHeading = navigationUsableGpsBearing(fix),
            speedMetersPerSecond = fix.speedMetersPerSecond
        )
        return riderPuckHeading
    }

    private fun updateRiderPuck(
        activeMap: MapLibreMap,
        point: LatLng,
        bearingDegrees: Float?
    ) {
        val source = ensureRiderStyle(activeMap) ?: return
        source.setGeoJson(riderFeatureCollectionJson(point, bearingDegrees))
        lastRenderedRiderPosition = point
        lastRenderedRiderHeading = normalizeRiderPuckBearing(bearingDegrees)
        alignActiveRouteHead(activeMap, point)
    }

    private fun renderRider(activeMap: MapLibreMap, state: RiderNavigationMapState) {
        // Web parity: authoritative GNSS stays untouched while the visible puck follows the
        // same confidence-bounded road snap, red/white artwork and speed-aware heading policy.
        val fix = navigationRiderDisplayFix(state) ?: return
        val displayPoint = fix.point
        val target = LatLng(displayPoint.latitude, displayPoint.longitude)
        val targetHeading = resolvedRiderPuckHeading(state.rider)
        if (ensureRiderStyle(activeMap) == null) return
        val rendered = lastRenderedRiderPosition
        if (rendered == null) {
            updateRiderPuck(activeMap, target, targetHeading)
            lastRenderedRiderFix = fix
            return
        }

        if (!shouldMoveRiderMarker(lastRenderedRiderFix, fix)) {
            val oldHeading = lastRenderedRiderHeading
            if (
                targetHeading != null &&
                (oldHeading == null || abs(riderPuckBearingDelta(oldHeading, targetHeading)) >= 2f)
            ) {
                updateRiderPuck(activeMap, rendered, targetHeading)
            }
            return
        }

        val previousTarget = lastRenderedRiderFix
        val fromPoint = NavigationMapPoint(rendered.latitude, rendered.longitude)
        val fromHeading = lastRenderedRiderHeading
        riderAnimator?.cancel()
        if (shouldAnimateRiderMarker(fromPoint, displayPoint)) {
            riderAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = riderMarkerAnimationMillis(previousTarget, fix)
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    val fraction = navigationCameraEaseInOut(animation.animatedFraction)
                    val t = fraction.toDouble()
                    val point = LatLng(
                        fromPoint.latitude + (displayPoint.latitude - fromPoint.latitude) * t,
                        fromPoint.longitude + (displayPoint.longitude - fromPoint.longitude) * t
                    )
                    updateRiderPuck(
                        activeMap,
                        point,
                        interpolateRiderPuckBearing(fromHeading, targetHeading, fraction)
                    )
                }
                start()
            }
        } else {
            updateRiderPuck(activeMap, target, targetHeading)
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
                        .padding(0.0, 0.0, 0.0, 0.0)
                        .build()
                ),
                CAMERA_OVERVIEW_ANIMATION_MS
            )
            lastOverviewSignature = signature
            lastCameraPoint = decision.center
            lastCameraBearing = 0f
            lastCameraTilt = 0f
            lastCameraZoom = decision.zoom.toDouble()
            return
        }

        lastOverviewSignature = null
        // A one-shot idle recenter must survive map/style/GPS readiness. The old
        // integration pulsed follow for 180ms, so first-load requests could disappear
        // before this holder had an SDK map. Consume only after scheduling real motion.
        val explicitRecenter = state.recenterRequest > 0 &&
            state.recenterRequest != lastAppliedRecenterRequest
        if (!state.followRider && !explicitRecenter) return
        val renderedBearing = activeMap.cameraPosition.bearing.toFloat()
        val decision = navigationCameraDecision(state, renderedBearing, followPuckBearing = true) ?: return
        val targetBearing = if (state.followRider) {
            resolvedRiderPuckHeading(state.rider) ?: decision.bearingDegrees ?: renderedBearing
        } else {
            decision.bearingDegrees ?: renderedBearing
        }
        // Web FOLLOW anchors the camera on the exact presentation puck, then shifts that
        // anchor down the viewport according to speed and the bottom dock. Do the same here
        // instead of centering the route look-ahead point as a separate Native policy.
        val cameraCenter = navigationRiderDisplayFix(state)?.point
            ?: state.rider?.point
            ?: decision.center
        val pointChanged = lastCameraPoint == null ||
            navigationHaversineMeters(lastCameraPoint!!, cameraCenter) >=
            navigationCameraMovementThresholdMeters(state.rider?.speedMetersPerSecond)
        val bearingChanged = navigationBearingChangedEnough(
            lastCameraBearing ?: renderedBearing,
            targetBearing,
            CAMERA_BEARING_MIN_DELTA_DEGREES
        )
        val tiltChanged = navigationTiltChangedEnough(
            lastCameraTilt,
            decision.tiltDegrees
        )
        val zoomChanged = lastCameraZoom == null ||
            abs(lastCameraZoom!! - decision.zoom) >= 0.05
        if (
            !force && !explicitRecenter &&
            !pointChanged && !bearingChanged && !tiltChanged && !zoomChanged
        ) return

        val cameraAnimationMs = navigationRotationDurationMillis(
            currentBearing = renderedBearing,
            targetBearing = targetBearing,
            cadenceMillis = null,
            fallbackMillis = CAMERA_ANIMATION_FALLBACK_MS,
            speedMetersPerSecond = state.rider?.speedMetersPerSecond
        ).toInt()
        val cameraPadding = navigationFollowCameraPadding(
            insets = state.viewportInsets,
            viewportHeightPx = view?.height ?: 0,
            speedMetersPerSecond = state.rider?.speedMetersPerSecond
        )
        activeMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(cameraCenter.latitude, cameraCenter.longitude))
                    .zoom(decision.zoom)
                    .bearing(targetBearing.toDouble())
                    .tilt(decision.tiltDegrees.toDouble())
                    .padding(
                        cameraPadding[0],
                        cameraPadding[1],
                        cameraPadding[2],
                        cameraPadding[3]
                    )
                    .build()
            ),
            cameraAnimationMs
        )
        if (explicitRecenter) lastAppliedRecenterRequest = state.recenterRequest
        lastCameraPoint = cameraCenter
        lastCameraBearing = targetBearing
        lastCameraTilt = decision.tiltDegrees
        lastCameraZoom = decision.zoom
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
                riderPuckPresent = riderSource != null && lastRenderedRiderPosition != null
            )
        )
    }

    private fun resetSurface() {
        riderAnimator?.cancel()
        riderAnimator = null
        stop()
        routeLines.clear()
        activeHeadLines.clear()
        stopMarkers.clear()
        stopSource = null
        stopStyleInitialized = false
        demandMarkers.clear()
        riderSource = null
        riderStyleInitialized = false
        lastRenderedRiderPosition = null
        lastRenderedRiderHeading = null
        riderPuckHeading = null
        riderPuckHeadingFix = null
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
        lastCameraZoom = null
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
    compassResetSignal: Int = 0,
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

    LaunchedEffect(compassResetSignal) {
        if (compassResetSignal > 0) holder.resetNorth()
    }

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
