package com.queuego.shared

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.longdo.api.IMapListener
import com.longdo.api.IStatusListener
import com.longdo.api.LongdoLayer
import com.longdo.api.MapGLSurfaceView
import com.longdo.api.Pin
import com.longdo.api.type.MapLocation
import java.lang.ref.WeakReference


object QueueGoLongdoPermissionBridge {
    private val maps = mutableListOf<WeakReference<com.longdo.api.Map>>()

    @Synchronized
    internal fun register(map: com.longdo.api.Map) {
        maps.removeAll { it.get() == null || it.get() === map }
        maps += WeakReference(map)
    }

    @Synchronized
    internal fun unregister(map: com.longdo.api.Map?) {
        maps.removeAll { it.get() == null || it.get() === map }
    }

    fun onRequestPermissionResult() {
        val active = synchronized(this) {
            maps.mapNotNull { it.get() }.also { maps.removeAll { ref -> ref.get() == null } }
        }
        active.forEach { map -> runCatching { map.onRequestPermissionResult() } }
    }
}

private class TrackingMapHolder {
    var view: MapGLSurfaceView? = null
    var map: com.longdo.api.Map? = null
    var renderedPoints: List<QgMapPoint>? = null
    var centered = false
    var disposed = false
    private var resumed = false

    fun resume() {
        val activeView = view ?: return
        if (disposed || resumed) return
        activeView.post {
            if (!disposed && view === activeView && !resumed) {
                runCatching { activeView.onResume() }
                runCatching { map?.onResume() }
                resumed = true
            }
        }
    }

    fun pause() {
        if (!resumed) return
        runCatching { map?.onPause() }
        runCatching { view?.onPause() }
        resumed = false
    }

    fun draw(points: List<QgMapPoint>) {
        val activeMap = map ?: return
        if (disposed) return
        val valid = points.filter { it.valid }
        if (renderedPoints == valid) return
        activeMap.clearPin()
        valid.forEach { point ->
            val density = view?.resources?.displayMetrics?.density ?: 1f
            val size = (26 * density).toInt().coerceAtLeast(26)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.color = point.color
            canvas.drawCircle(size / 2f, size / 2f, size * 10f / 26f, paint)
            activeMap.pushPin(Pin(MapLocation(point.longitude, point.latitude), bitmap, PointF(size / 2f, size / 2f)))
        }
        renderedPoints = valid
        // Refresh markers without pulling the user's manually panned viewport back.
        if (!centered && valid.isNotEmpty()) {
            activeMap.setZoom(14)
            activeMap.setLocation(MapLocation(valid.first().longitude, valid.first().latitude))
            centered = true
        }
    }
}

private fun Context.mapLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.mapLifecycleOwner() else null
    else -> null
}

/** Native OpenGL Longdo map; contains no browser or WebView. */
@Composable
fun QgLongdoTrackingMap(
    points: List<QgMapPoint>,
    modifier: Modifier = Modifier,
    onReady: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val holder = remember { TrackingMapHolder() }
    val currentPoints = rememberUpdatedState(points)
    val currentReady = rememberUpdatedState(onReady)
    val owner = remember(context) { context.mapLifecycleOwner() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapGLSurfaceView(ctx).also { view ->
                holder.view = view
                view.setListener(object : IMapListener {
                    override fun onMapCreated(map: com.longdo.api.Map) {
                        view.post {
                            if (!holder.disposed) {
                                runCatching {
                                    holder.map = map
                                    QueueGoLongdoPermissionBridge.register(map)
                                    map.setBase(LongdoLayer(ctx, "gray", 0, 1, 20))
                                    if (owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false) {
                                        runCatching { map.onResume() }
                                    }
                                    holder.draw(currentPoints.value)
                                }.onSuccess { currentReady.value(true) }
                                    .onFailure { currentReady.value(false) }
                            }
                        }
                    }
                })
                if (owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false) holder.resume()
            }
        },
        update = {
            runCatching { holder.draw(points) }.onFailure { currentReady.value(false) }
        }
    )
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.resume()
                Lifecycle.Event.ON_PAUSE -> holder.pause()
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            holder.disposed = true
            QueueGoLongdoPermissionBridge.unregister(holder.map)
            runCatching { holder.map?.clearPin() }
            holder.pause()
            holder.map = null
            holder.view = null
        }
    }
}


private class LocationPickerMapHolder {
    var view: MapGLSurfaceView? = null
    var map: com.longdo.api.Map? = null
    var disposed = false
    var lastRecenterToken: Int? = null
    private var resumed = false

    fun resume() {
        val activeView = view ?: return
        if (disposed || resumed) return
        activeView.post {
            if (!disposed && view === activeView && !resumed) {
                runCatching { activeView.onResume() }
                runCatching { map?.onResume() }
                resumed = true
            }
        }
    }

    fun pause() {
        if (!resumed) return
        runCatching { map?.onPause() }
        runCatching { view?.onPause() }
        resumed = false
    }

    fun recenter(point: QgMapPoint?, token: Int) {
        val activeMap = map ?: return
        if (disposed || point?.valid != true || lastRecenterToken == token) return
        lastRecenterToken = token
        activeMap.setZoom(16)
        activeMap.setLocation(MapLocation(point.longitude, point.latitude), true)
    }
}

/**
 * Native Longdo location picker. The selected coordinate is the map centre, matching
 * QueueGo web's drag-the-map picker without introducing a browser or WebView.
 */
@Composable
fun QgLongdoLocationPickerMap(
    initialPoint: QgMapPoint?,
    recenterPoint: QgMapPoint?,
    recenterToken: Int,
    modifier: Modifier = Modifier,
    onCenterChanged: (QgMapPoint) -> Unit,
    onReady: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val holder = remember { LocationPickerMapHolder() }
    val currentCenterChanged = rememberUpdatedState(onCenterChanged)
    val currentReady = rememberUpdatedState(onReady)
    val currentInitial = rememberUpdatedState(initialPoint)
    val currentRecenter = rememberUpdatedState(recenterPoint)
    val currentRecenterToken = rememberUpdatedState(recenterToken)
    val owner = remember(context) { context.mapLifecycleOwner() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapGLSurfaceView(ctx).also { view ->
                holder.view = view
                view.setListener(object : IMapListener {
                    override fun onMapCreated(map: com.longdo.api.Map) {
                        view.post {
                            if (!holder.disposed) {
                                runCatching {
                                    holder.map = map
                                    QueueGoLongdoPermissionBridge.register(map)
                                    map.setBase(LongdoLayer(ctx, "gray", 0, 1, 20))
                                    if (owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false) {
                                        runCatching { map.onResume() }
                                    }
                                    map.setDrawCrossSign(true)
                                    map.setCrossSignColor(floatArrayOf(239f / 255f, 51f / 255f, 64f / 255f, 1f))
                                    map.setStatusListener(object : IStatusListener {
                                        override fun onScale(scale: Float) = Unit

                                        override fun onMapStatusUpdated(
                                            location: MapLocation,
                                            zoomLevel: Int,
                                            scale: Float,
                                            action: String?,
                                            isUserAction: Boolean
                                        ) {
                                            if (!isUserAction || holder.disposed) return
                                            val point = QgMapPoint(location.lat, location.lon, 0)
                                            if (point.valid) {
                                                view.post {
                                                    if (!holder.disposed) currentCenterChanged.value(point)
                                                }
                                            }
                                        }
                                    })
                                    val start = currentInitial.value?.takeIf { it.valid }
                                        ?: QgMapPoint(15.0, 101.0, 0)
                                    map.setZoom(if (currentInitial.value?.valid == true) 16 else 6)
                                    map.setLocation(MapLocation(start.longitude, start.latitude))
                                    currentInitial.value?.takeIf { it.valid }?.let(currentCenterChanged.value)
                                    holder.recenter(currentRecenter.value, currentRecenterToken.value)
                                }.onSuccess { currentReady.value(true) }
                                    .onFailure { currentReady.value(false) }
                            }
                        }
                    }
                })
                if (owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false) holder.resume()
            }
        },
        update = {
            runCatching {
                holder.recenter(recenterPoint, recenterToken)
            }.onFailure { currentReady.value(false) }
        }
    )

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.resume()
                Lifecycle.Event.ON_PAUSE -> holder.pause()
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            holder.disposed = true
            QueueGoLongdoPermissionBridge.unregister(holder.map)
            holder.pause()
            holder.map = null
            holder.view = null
        }
    }
}
