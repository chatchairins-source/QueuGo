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
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val QUEUEGO_VECTOR_SOURCE = "queuego-vector"
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
    var builder = Style.Builder()
        .withSource(VectorSource(QUEUEGO_VECTOR_SOURCE, sourceUri))
        .withLayer(
            BackgroundLayer("queuego-background").withProperties(
                backgroundColor("#f3f0e9")
            )
        )
        .withLayer(
            FillLayer("queuego-water", QUEUEGO_VECTOR_SOURCE)
                .withSourceLayer("water_polygons")
                .withProperties(
                    fillColor("#b9dadd"),
                    fillOpacity(1.0f)
                )
        )
        .withLayer(
            FillLayer("queuego-buildings", QUEUEGO_VECTOR_SOURCE)
                .withSourceLayer("buildings")
                .withProperties(
                    fillColor("#d5d7d9"),
                    fillOpacity(0.9f)
                )
                .also { it.minZoom = 14f }
        )

    val roads = listOf(
        Triple("motorway", "#e8a7a1", 7.0f),
        Triple("trunk", "#edb4a4", 6.5f),
        Triple("primary", "#f2cf86", 6.0f),
        Triple("secondary", "#f2dfa0", 5.4f),
        Triple("tertiary", "#f7f4e8", 4.8f),
        Triple("unclassified", "#ffffff", 4.2f),
        Triple("residential", "#ffffff", 4.2f),
        Triple("living_street", "#ffffff", 3.8f),
        Triple("service", "#ffffff", 3.2f)
    )
    roads.forEach { (kind, color, widthAt16) ->
        val filter = Expression.eq(
            Expression.get("kind"),
            Expression.literal(kind)
        )
        builder = builder
            .withLayer(
                LineLayer("queuego-road-$kind-casing", QUEUEGO_VECTOR_SOURCE)
                    .withSourceLayer("streets")
                    .withFilter(filter)
                    .withProperties(
                        lineColor("#b8b8b4"),
                        lineOpacity(1.0f),
                        lineCap(Property.LINE_CAP_ROUND),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineWidth(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.zoom(),
                                Expression.stop(12f, 1.0f),
                                Expression.stop(16f, widthAt16 + 1.8f),
                                Expression.stop(19f, widthAt16 + 4.0f)
                            )
                        )
                    )
            )
            .withLayer(
                LineLayer("queuego-road-$kind", QUEUEGO_VECTOR_SOURCE)
                    .withSourceLayer("streets")
                    .withFilter(filter)
                    .withProperties(
                        lineColor(color),
                        lineOpacity(1.0f),
                        lineCap(Property.LINE_CAP_ROUND),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineWidth(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.zoom(),
                                Expression.stop(12f, 0.6f),
                                Expression.stop(16f, widthAt16),
                                Expression.stop(19f, widthAt16 + 2.2f)
                            )
                        )
                    )
            )
    }
    return builder
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
                Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude))
            }
        )
        val source = style.getSource(QUEUEGO_TRACKING_SOURCE)
        if (source is GeoJsonSource) source.setGeoJson(features)
        else if (source == null) {
            style.addSource(GeoJsonSource(QUEUEGO_TRACKING_SOURCE, features))
            style.addLayer(
                CircleLayer(QUEUEGO_TRACKING_LAYER, QUEUEGO_TRACKING_SOURCE)
                    .withProperties(
                        circleColor("#E6002D"),
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
