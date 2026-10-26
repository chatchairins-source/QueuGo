package com.queuego.shared

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import java.io.File
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
    // Only adb run-as on a debuggable APK can provision this real-data CI archive.
    // Release builds continue to require the configured HTTPS service.
    queueGoDebugMapArchive(context)?.let { return "pmtiles://file://${it.absolutePath}" }
    val base = queueGoMapServiceBaseUrl(context) ?: return null
    val origin = if (base.endsWith("/map-api")) base.removeSuffix("/map-api") else base
    return "pmtiles://$origin/map-api/vector.pmtiles"
}

fun queueGoMapStyle(sourceUri: String): Style.Builder {
    // Web is the visual source of truth. Native registers the same OSM raster
    // presentation used by queuego-map-web.js so Customer/Merchant/Rider no
    // longer render a separate Shortbread-looking basemap.
    //
    // Keep the QueueGo-hosted vector source registered for QueueGo-owned map
    // capabilities and future overlays, but do not paint a second competing
    // basemap underneath/above the Web-parity raster layer.
    val webTiles = TileSet("2.2.0", QUEUEGO_WEB_BASEMAP_TILE).apply {
        attribution = "© OpenStreetMap contributors"
    }
    return Style.Builder()
        .withSource(RasterSource(QUEUEGO_WEB_BASEMAP_SOURCE, webTiles, 256))
        .withSource(VectorSource(QUEUEGO_VECTOR_SOURCE, sourceUri))
        .withLayer(
            BackgroundLayer("queuego-background").withProperties(
                backgroundColor("#f3f0e8")
            )
        )
        .withLayer(
            RasterLayer(QUEUEGO_WEB_BASEMAP_LAYER, QUEUEGO_WEB_BASEMAP_SOURCE)
        )
}

private fun Context.lifecycleOwnerOrNull(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.lifecycleOwnerOrNull() else null
    else -> null
}

private class QueueGoTrackingMapHolder {
    var view: MapView? = null
    var map: MapLibreMap? = null
    var started = false
    var resumed = false
    var destroyed = false
    var lastPoints: List<QgMapPoint> = emptyList()

    fun render(points: List<QgMapPoint>) {
        lastPoints = points.filter { it.valid }
        val style = map?.style ?: return
        val features = FeatureCollection.fromFeatures(
            lastPoints.map { point ->
                Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply {
                    addStringProperty("color", "#%06X".format(point.color and 0xFFFFFF))
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
                    )
            )
        }
        lastPoints.firstOrNull()?.let { point ->
            if (lastPoints.size == 1) {
                map?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(point.latitude, point.longitude),
                        16.0
                    ),
                    350
                )
            } else {
                val bounds = org.maplibre.android.geometry.LatLngBounds.Builder().apply {
                    lastPoints.forEach { include(LatLng(it.latitude, it.longitude)) }
                }.build()
                map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 48), 350)
            }
        } ?: map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(14.9918756, 103.0994795), 11.0),
            0
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
    var suppressNextIdle = false
    var onCenterChanged: (QgMapPoint) -> Unit = {}

    fun recenter(point: QgMapPoint?, token: Int) {
        val active = map ?: return
        if (point?.valid != true || lastRecenterToken == token) return
        lastRecenterToken = token
        suppressNextIdle = true
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
    onCenterChanged: (QgMapPoint) -> Unit,
    onReady: (Boolean) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentCenterChanged = rememberUpdatedState(onCenterChanged)
    val currentReady = rememberUpdatedState(onReady)
    val holder = remember { QueueGoLocationPickerHolder() }
    holder.onCenterChanged = { currentCenterChanged.value(it) }
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
                            map.addOnCameraIdleListener {
                                if (holder.destroyed) return@addOnCameraIdleListener
                                if (holder.suppressNextIdle) {
                                    holder.suppressNextIdle = false
                                    return@addOnCameraIdleListener
                                }
                                val target = map.cameraPosition.target
                                    ?: return@addOnCameraIdleListener
                                QgMapPoint(target.latitude, target.longitude, 0)
                                    .takeIf { it.valid }
                                    ?.let(holder.onCenterChanged)
                            }
                            map.setStyle(queueGoMapStyle(sourceUri)) {
                                holder.lastRecenterToken = recenterToken
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
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(12.dp)
                    .background(QgRed, CircleShape)
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
