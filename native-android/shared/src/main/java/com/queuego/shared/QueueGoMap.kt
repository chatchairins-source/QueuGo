package com.queuego.shared

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import java.io.File
import com.queuego.ridermaplibreosm.ShortbreadVillageStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val QUEUEGO_VECTOR_SOURCE = "queuego-vector"
private const val QUEUEGO_WEB_BASEMAP_SOURCE = "queuego-web-basemap"
private const val QUEUEGO_WEB_BASEMAP_LAYER = "queuego-web-basemap-layer"
private const val QUEUEGO_WEB_BASEMAP_TILE = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
private const val QUEUEGO_TRACKING_SOURCE = "queuego-tracking"
private const val QUEUEGO_TRACKING_LAYER = "queuego-tracking-points"
private const val QUEUEGO_LOCATION_PICKER_SOURCE = "queuego-location-picker"
private const val QUEUEGO_LOCATION_PICKER_LAYER = "queuego-location-picker-point"

fun queueGoMapServiceBaseUrl(context: Context): String? = runCatching {
    context.packageManager
        .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        .metaData
        ?.getString("queuego.map.service.base_url")
        ?.trim()
        ?.trimEnd('/')
        ?.takeIf { it.startsWith("https://") }
}.getOrNull()

private fun queueGoDebugMapArchive(context: Context): File? = queueGoDebugMapArchive(
    context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
    File(context.filesDir, "queuego-ci-buriram.pmtiles")
)

fun queueGoMapVectorSourceUri(context: Context): String? {
    // A bundled nationwide archive takes precedence over both CI data and HTTP.
    // Read only its header here; MapLibre reads tile ranges directly from the APK.
    val bundled = runCatching {
        context.assets.open("maps/thailand-shortbread.pmtiles").use { input ->
            queueGoPmTilesV3Header(input)
        }
    }.getOrDefault(false)
    if (bundled) return "pmtiles://asset://maps/thailand-shortbread.pmtiles"
    // Only adb run-as on a debuggable APK can provision this real-data CI archive.
    // Release builds continue to require the configured HTTPS service.
    queueGoDebugMapArchive(context)?.let { return "pmtiles://file://${it.absolutePath}" }
    val base = queueGoMapServiceBaseUrl(context) ?: return null
    val origin = if (base.endsWith("/map-api")) base.removeSuffix("/map-api") else base
    return "pmtiles://$origin/map-api/vector.pmtiles"
}

fun queueGoMapStyle(sourceUri: String): Style.Builder {
    // Use QueueGo Map vector rendering for both hosted and packaged tiles.
    return ShortbreadVillageStyle.builder(sourceUri)
}

private fun Context.lifecycleOwnerOrNull(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.lifecycleOwnerOrNull() else null
    else -> null
}

private fun trackingLabelBitmap(point: QgMapPoint, label: String): Bitmap {
    val cluster = point.role == QgMapRole.RIDER
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = if (cluster) Paint.Align.LEFT else Paint.Align.CENTER
        color = if (cluster) point.color else android.graphics.Color.WHITE
    }
    val bodyWidth = maxOf(72, kotlin.math.ceil(text.measureText(label)).toInt() + if (cluster) 70 else 28)
    val width = bodyWidth + 12
    val bitmap = Bitmap.createBitmap(width, if (cluster) 84 else 104, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val center = width / 2f
    fun fill(color: Int) { paint.style = Paint.Style.FILL; paint.color = color }
    fun border(color: Int, stroke: Float) {
        paint.style = Paint.Style.STROKE; paint.color = color; paint.strokeWidth = stroke
        paint.strokeJoin = Paint.Join.ROUND
    }
    if (!cluster) {
        val tip = Path().apply { moveTo(center - 14f, 66f); lineTo(center, 96f); lineTo(center + 14f, 66f); close() }
        fill(point.color); canvas.drawPath(tip, paint)
        border(android.graphics.Color.WHITE, 6f); canvas.drawPath(tip, paint)
    }
    fill(if (cluster) android.graphics.Color.WHITE else point.color)
    canvas.drawRoundRect(6f, 6f, width - 6f, 78f, 36f, 36f, paint)
    border(if (cluster) point.color else android.graphics.Color.WHITE, if (cluster) 4f else 6f)
    canvas.drawRoundRect(6f, 6f, width - 6f, 78f, 36f, 36f, paint)
    if (cluster) {
        border(point.color, 3f)
        paint.strokeCap = Paint.Cap.ROUND
        qgMotorcycleGeometry(
            drawLine = { x1, y1, x2, y2 -> canvas.drawLine(22f + x1 * 32f, 26f + y1 * 32f, 22f + x2 * 32f, 26f + y2 * 32f, paint) },
            drawCircle = { cx, cy, radius -> canvas.drawCircle(22f + cx * 32f, 26f + cy * 32f, radius * 32f, paint) }
        )
    }
    canvas.drawText(label, if (cluster) 60f else center, 42f - (text.ascent() + text.descent()) / 2f, text)
    return bitmap
}

private class QueueGoTrackingMapHolder {
    var view: MapView? = null
    var map: MapLibreMap? = null
    var started = false
    var resumed = false
    var destroyed = false
    var lastPoints: List<QgMapPoint> = emptyList()
    private var hasSetInitialViewport = false
    private val labelImages = mutableMapOf<String, Triple<QgMapRole, Int, String>>()

    fun render(points: List<QgMapPoint>) {
        lastPoints = points.filter { it.valid }
        val style = map?.style ?: return
        val features = FeatureCollection.fromFeatures(
            lastPoints.mapIndexed { index, point ->
                Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply {
                    addStringProperty("color", "#%06X".format(point.color and 0xFFFFFF))
                    point.label?.trim()?.take(12)?.takeIf { it.isNotEmpty() }?.let { label ->
                        val imageId = "$QUEUEGO_TRACKING_SOURCE-label-$index"
                        val signature = Triple(point.role, point.color, label)
                        if (labelImages[imageId] != signature) {
                            style.addImage(imageId, trackingLabelBitmap(point, label))
                            labelImages[imageId] = signature
                        }
                        addStringProperty("marker_image", imageId)
                        addStringProperty("marker_kind", if (point.role == QgMapRole.RIDER) "cluster" else "stop")
                    }
                }
            }
        )
        val source = style.getSource(QUEUEGO_TRACKING_SOURCE)
        if (source is GeoJsonSource) source.setGeoJson(features)
        else if (source == null) {
            style.addSource(GeoJsonSource(QUEUEGO_TRACKING_SOURCE, features))
            style.addLayer(
                CircleLayer(QUEUEGO_TRACKING_LAYER, QUEUEGO_TRACKING_SOURCE)
                    .withProperties(
                        circleColor(Expression.get("color")),
                        circleRadius(7f)
                    ).apply { setFilter(Expression.not(Expression.has("marker_image"))) }
            )
            listOf("stop" to "bottom", "cluster" to "center").forEach { (kind, anchor) ->
                style.addLayer(
                    SymbolLayer("$QUEUEGO_TRACKING_LAYER-$kind-label", QUEUEGO_TRACKING_SOURCE).apply {
                        setFilter(Expression.eq(Expression.get("marker_kind"), Expression.literal(kind)))
                        setProperties(
                            PropertyFactory.iconImage(Expression.get("marker_image")),
                            PropertyFactory.iconSize(0.5f),
                            PropertyFactory.iconAnchor(anchor),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true)
                        )
                    }
                )
            }
        }
        // Once an initial valid map position is chosen, user gestures own the camera.
        // Ride GPS/supply updates must redraw markers, never reset manual zoom/tilt.
        if (!hasSetInitialViewport && lastPoints.isNotEmpty()) {
            hasSetInitialViewport = true
            val activeMap = map ?: return
            if (lastPoints.size == 1) {
                val point = lastPoints.first()
                activeMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(point.latitude, point.longitude), 16.0
                    )
                )
            } else {
                val bounds = org.maplibre.android.geometry.LatLngBounds.Builder().apply {
                    lastPoints.forEach { include(LatLng(it.latitude, it.longitude)) }
                }.build()
                activeMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 48))
            }
        }
    }

    fun start() {
        if (destroyed || started) return
        view?.onStart()
        started = true
    }

    fun resume() {
        if (destroyed || resumed) return
        if (!started) start()
        view?.onResume()
        resumed = true
    }

    fun pause() {
        if (destroyed || !resumed) return
        view?.onPause()
        resumed = false
    }

    fun stop() {
        if (destroyed || !started) return
        if (resumed) pause()
        view?.onStop()
        started = false
    }

    fun destroy() {
        if (destroyed) return
        stop()
        view?.onDestroy()
        labelImages.clear()
        lastPoints = emptyList()
        hasSetInitialViewport = false
        destroyed = true
        map = null
        view = null
    }
}

@Composable
fun QgTrackingMap(
    points: List<QgMapPoint>,
    modifier: Modifier = Modifier,
    onReady: (Boolean) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentReady = rememberUpdatedState(onReady)
    val holder = remember { QueueGoTrackingMapHolder() }
    val sourceUri = remember(context.packageName) { queueGoMapVectorSourceUri(context) }

    if (sourceUri == null) {
        Box(modifier.background(Color(0xFFF1F2F3)))
        DisposableEffect(Unit) {
            currentReady.value(false)
            onDispose { }
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapLibre.getInstance(ctx.applicationContext)
            MapView(ctx).also { view ->
                holder.view = view
                view.onCreate(null)
                view.getMapAsync { map ->
                    if (holder.destroyed) return@getMapAsync
                    holder.map = map
                    map.uiSettings.isLogoEnabled = false
                    map.uiSettings.isAttributionEnabled = true
                    // QueueGo Ride: pinch zoom, two-finger rotate and two-finger 3D tilt.
                    map.uiSettings.isZoomGesturesEnabled = true
                    map.uiSettings.isScrollGesturesEnabled = true
                    map.uiSettings.isRotateGesturesEnabled = true
                    map.uiSettings.isTiltGesturesEnabled = true
                    map.uiSettings.setDisableRotateWhenScaling(false)
                    // Initial neutral Thailand viewport; the first valid Rider/pickup point
                    // frames once without hijacking user gestures on subsequent updates.
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(15.5, 100.5))
                        .zoom(6.0)
                        .build()
                    map.setStyle(queueGoMapStyle(sourceUri)) {
                        holder.render(points)
                        currentReady.value(true)
                    }
                }
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) holder.start()
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) holder.resume()
            }
        },
        update = {
            holder.render(points)
        }
    )

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> holder.start()
                Lifecycle.Event.ON_RESUME -> holder.resume()
                Lifecycle.Event.ON_PAUSE -> holder.pause()
                Lifecycle.Event.ON_STOP -> holder.stop()
                Lifecycle.Event.ON_DESTROY -> holder.destroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder.destroy()
        }
    }
}

private class QueueGoLocationPickerHolder {
    var view: MapView? = null
    var map: MapLibreMap? = null
    var started = false
    var resumed = false
    var destroyed = false
    var lastRecenterToken: Int? = null
    var selectedPoint: QgMapPoint? = null
    var onPointSelected: (QgMapPoint) -> Unit = {}

    fun renderSelectedPoint(point: QgMapPoint?) {
        selectedPoint = point?.takeIf { it.valid }
        val style = map?.style ?: return
        val features = FeatureCollection.fromFeatures(
            selectedPoint?.let {
                listOf(Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)))
            } ?: emptyList()
        )
        val source = style.getSource(QUEUEGO_LOCATION_PICKER_SOURCE)
        if (source is GeoJsonSource) {
            source.setGeoJson(features)
        } else if (source == null) {
            style.addSource(GeoJsonSource(QUEUEGO_LOCATION_PICKER_SOURCE, features))
            style.addLayer(
                CircleLayer(QUEUEGO_LOCATION_PICKER_LAYER, QUEUEGO_LOCATION_PICKER_SOURCE)
                    .withProperties(
                        circleColor("#E62E46"),
                        circleRadius(7f),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(2.5f)
                    )
            )
        }
    }

    fun recenter(point: QgMapPoint?, token: Int) {
        val active = map ?: return
        if (point?.valid != true) return
        renderSelectedPoint(point)
        if (lastRecenterToken == token) return
        lastRecenterToken = token
        active.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(point.latitude, point.longitude),
                16.0
            ),
            350
        )
    }

    fun start() {
        if (destroyed || started) return
        view?.onStart()
        started = true
    }

    fun resume() {
        if (destroyed || resumed) return
        if (!started) start()
        view?.onResume()
        resumed = true
    }

    fun pause() {
        if (destroyed || !resumed) return
        view?.onPause()
        resumed = false
    }

    fun stop() {
        if (destroyed || !started) return
        if (resumed) pause()
        view?.onStop()
        started = false
    }

    fun destroy() {
        if (destroyed) return
        stop()
        view?.onDestroy()
        destroyed = true
        map = null
        view = null
    }
}

@Composable
fun QgLocationPickerMap(
    initialPoint: QgMapPoint?,
    recenterPoint: QgMapPoint?,
    recenterToken: Int,
    modifier: Modifier = Modifier,
    onPointSelected: (QgMapPoint) -> Unit,
    onReady: (Boolean) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentPointSelected = rememberUpdatedState(onPointSelected)
    val currentReady = rememberUpdatedState(onReady)
    val holder = remember { QueueGoLocationPickerHolder() }
    holder.onPointSelected = { currentPointSelected.value(it) }
    val sourceUri = remember(context.packageName) { queueGoMapVectorSourceUri(context) }

    Box(modifier) {
        if (sourceUri == null) {
            Box(Modifier.fillMaxSize().background(Color(0xFFF1F2F3)))
            DisposableEffect(Unit) {
                currentReady.value(false)
                onDispose { }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapLibre.getInstance(ctx.applicationContext)
                    MapView(ctx).also { view ->
                        holder.view = view
                        view.onCreate(null)
                        view.getMapAsync { map ->
                            if (holder.destroyed) return@getMapAsync
                            holder.map = map
                            map.uiSettings.isLogoEnabled = false
                            map.uiSettings.isAttributionEnabled = true
                            val start = initialPoint?.takeIf { it.valid }
                            map.cameraPosition = CameraPosition.Builder()
                                .target(
                                    LatLng(
                                        start?.latitude ?: if (queueGoDebugMapArchive(ctx) != null) 14.9918756 else 15.0,
                                        start?.longitude ?: if (queueGoDebugMapArchive(ctx) != null) 103.0994795 else 101.0
                                    )
                                )
                                .zoom(if (start != null || queueGoDebugMapArchive(ctx) != null) 16.0 else 6.0)
                                .build()
                            map.addOnMapClickListener { target ->
                                if (holder.destroyed) return@addOnMapClickListener false
                                val point = QgMapPoint(target.latitude, target.longitude, 0)
                                if (!point.valid) return@addOnMapClickListener false
                                holder.renderSelectedPoint(point)
                                holder.onPointSelected(point)
                                true
                            }
                            map.setStyle(queueGoMapStyle(sourceUri)) {
                                holder.lastRecenterToken = recenterToken
                                holder.renderSelectedPoint(start)
                                currentReady.value(true)
                            }
                        }
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) holder.start()
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) holder.resume()
                    }
                },
                update = {
                    holder.recenter(recenterPoint, recenterToken)
                }
            )
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> holder.start()
                Lifecycle.Event.ON_RESUME -> holder.resume()
                Lifecycle.Event.ON_PAUSE -> holder.pause()
                Lifecycle.Event.ON_STOP -> holder.stop()
                Lifecycle.Event.ON_DESTROY -> holder.destroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder.destroy()
        }
    }
}
