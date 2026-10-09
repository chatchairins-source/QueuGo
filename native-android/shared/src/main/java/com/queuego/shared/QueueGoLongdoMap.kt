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
                                    map.setBase(LongdoLayer(ctx, "gray", 0, 1, 20))
                                    holder.draw(currentPoints.value)
                                }.onSuccess { currentReady.value(true) }
                                    .onFailure { currentReady.value(false) }
                            }
                        }
                    }
                })
                if (owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false) view.onResume()
            }
        },
        update = {
            runCatching { holder.draw(points) }.onFailure { currentReady.value(false) }
        }
    )
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.view?.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.view?.onPause()
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose {
            owner?.lifecycle?.removeObserver(observer)
            holder.disposed = true
            runCatching { holder.map?.clearPin() }
            runCatching { holder.view?.onPause() }
            holder.map = null
            holder.view = null
        }
    }
}
