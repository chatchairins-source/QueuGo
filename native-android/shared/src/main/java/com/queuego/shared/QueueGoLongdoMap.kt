package com.queuego.shared

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.view.LayoutInflater
import android.util.Log
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

private class TrackingMapHolder {
    var view: MapGLSurfaceView? = null
    var map: com.longdo.api.Map? = null
    var renderedPoints: List<QgMapPoint>? = null
    var centered = false
    var disposed = false
    private var resumed = false
    private var pausePending = false

    fun resume() {
        val activeView = view ?: return
        pausePending = false
        if (!disposed && !resumed) {
            resumed = runCatching { activeView.onResume() }
                .onFailure { Log.w("QueueGoLongdoMap", "shared_map_on_resume_failed", it) }
                .isSuccess
        }
    }

    fun pause() {
        if (resumed) {
            // SDK0.1.8 can touch renderer state while the GL map is still being created
            // or torn down on physical devices. Treat an exception/unknown renderer as
            // not-ready instead of allowing the whole Customer/Merchant process to die.
            val rendererReady = runCatching { view?.renderer?.map != null }
                .onFailure { Log.w("QueueGoLongdoMap", "shared_map_renderer_state_failed", it) }
                .getOrDefault(false)
            if (!rendererReady) {
                pausePending = true
                return
            }
            runCatching { view?.onPause() }
                .onFailure { Log.w("QueueGoLongdoMap", "shared_map_on_pause_failed", it) }
            resumed = false
            pausePending = false
        }
    }

    fun mapCreated(createdView: MapGLSurfaceView) {
        if (pausePending && resumed) {
            runCatching { createdView.onPause() }
                .onFailure { Log.w("QueueGoLongdoMap", "shared_map_created_pause_failed", it) }
            resumed = false
            pausePending = false
        }
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
            (LayoutInflater.from(ctx).inflate(R.layout.view_longdo_shared_map, null, false) as MapGLSurfaceView).also { view ->
                holder.view = view
                view.setListener(object : IMapListener {
                    override fun onMapCreated(map: com.longdo.api.Map) {
                        view.post {
                            holder.mapCreated(view)
                            if (!holder.disposed) {
                                runCatching {
                                    holder.map = map
                                    holder.renderedPoints = null
                                    map.setBase(LongdoLayer(ctx, "gray", 0, 1, 20).apply {
                                        layerURL = "https://ms2.longdo.com/mmmap/img.php"
                                    })
                                    holder.draw(currentPoints.value)
                                }.onSuccess { Log.i("QueueGoLongdoMap", "native_map_ready"); currentReady.value(true) }
                                    .onFailure { Log.e("QueueGoLongdoMap", "Native map operation failed", it); currentReady.value(false) }
                            }
                        }
                    }
                })
                if (owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false) holder.resume()
            }
        },
        update = {
            runCatching { holder.draw(points) }.onFailure { Log.e("QueueGoLongdoMap", "Native map operation failed", it); currentReady.value(false) }
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
            runCatching { holder.map?.clearPin() }
            runCatching { holder.pause() }
            holder.map = null
            holder.view = null
        }
    }
}


private class LocationPickerMapHolder {
    var view: MapGLSurfaceView? = null
    var map: com.longdo.api.Map? = null
    var disposed = false
    private var resumed = false
    private var pausePending = false

    fun resume() {
        val activeView = view ?: return
        pausePending = false
        if (!disposed && !resumed) {
            resumed = runCatching { activeView.onResume() }
                .onFailure { Log.w("QueueGoLongdoMap", "shared_map_on_resume_failed", it) }
                .isSuccess
        }
    }

    fun pause() {
        if (resumed) {
            // SDK0.1.8 can touch renderer state while the GL map is still being created
            // or torn down on physical devices. Treat an exception/unknown renderer as
            // not-ready instead of allowing the whole Customer/Merchant process to die.
            val rendererReady = runCatching { view?.renderer?.map != null }
                .onFailure { Log.w("QueueGoLongdoMap", "shared_map_renderer_state_failed", it) }
                .getOrDefault(false)
            if (!rendererReady) {
                pausePending = true
                return
            }
            runCatching { view?.onPause() }
                .onFailure { Log.w("QueueGoLongdoMap", "shared_map_on_pause_failed", it) }
            resumed = false
            pausePending = false
        }
    }

    fun mapCreated(createdView: MapGLSurfaceView) {
        if (pausePending && resumed) {
            runCatching { createdView.onPause() }
                .onFailure { Log.w("QueueGoLongdoMap", "shared_map_created_pause_failed", it) }
            resumed = false
            pausePending = false
        }
    }
    var lastRecenterToken: Int? = null

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
            (LayoutInflater.from(ctx).inflate(R.layout.view_longdo_shared_map, null, false) as MapGLSurfaceView).also { view ->
                holder.view = view
                view.setListener(object : IMapListener {
                    override fun onMapCreated(map: com.longdo.api.Map) {
                        view.post {
                            holder.mapCreated(view)
                            if (!holder.disposed) {
                                holder.map = map
                                holder.lastRecenterToken = null
                                val setup = runCatching {
                                    map.setBase(LongdoLayer(ctx, "gray", 0, 1, 20).apply {
                                        layerURL = "https://ms2.longdo.com/mmmap/img.php"
                                    })
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
                                }
                                setup.onFailure {
                                    Log.e("QueueGoLongdoMap", "Native map setup failed", it)
                                    currentReady.value(false)
                                }
                                if (setup.isSuccess) {
                                    fun finishInitialCamera(attempt: Int) {
                                        if (holder.disposed || holder.map !== map) return
                                        runCatching {
                                            val start = currentInitial.value?.takeIf { it.valid }
                                                ?: QgMapPoint(15.0, 101.0, 0)
                                            map.setZoom(if (currentInitial.value?.valid == true) 16 else 6)
                                            map.setLocation(MapLocation(start.longitude, start.latitude))
                                            currentInitial.value?.takeIf { it.valid }?.let(currentCenterChanged.value)
                                            holder.recenter(currentRecenter.value, currentRecenterToken.value)
                                        }.onSuccess {
                                            Log.i("QueueGoLongdoMap", "native_map_ready")
                                            currentReady.value(true)
                                        }.onFailure { failure ->
                                            if (failure is NullPointerException && attempt < 20 && !holder.disposed) {
                                                view.postDelayed({ finishInitialCamera(attempt + 1) }, 100L)
                                            } else {
                                                Log.e("QueueGoLongdoMap", "Native map operation failed", failure)
                                                currentReady.value(false)
                                            }
                                        }
                                    }
                                    // Longdo 0.1.8 can report onMapCreated before its zoom listener is attached.
                                    // Retry the first camera operation asynchronously instead of failing the visible map.
                                    view.postDelayed({ finishInitialCamera(0) }, 100L)
                                }
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
            }.onFailure { Log.e("QueueGoLongdoMap", "Native map operation failed", it); currentReady.value(false) }
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
            runCatching { holder.pause() }
            holder.map = null
            holder.view = null
        }
    }
}
