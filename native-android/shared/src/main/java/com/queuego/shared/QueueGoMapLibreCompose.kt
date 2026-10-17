package com.queuego.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.queuego.ridermaplibreosm.QueueGoMapLibrePickerView
import com.queuego.ridermaplibreosm.QueueGoMapLibreTrackingView
import com.queuego.ridermaplibreosm.QueueGoTrackingPoint

@Composable
fun QgQueueGoLocationPickerMap(
    initialPoint: QgMapPoint?,
    recenterPoint: QgMapPoint?,
    recenterToken: Int,
    modifier: Modifier = Modifier,
    selectInitialPoint: Boolean = true,
    onCenterChanged: (QgMapPoint) -> Unit,
    onCenterObserved: (QgMapPoint) -> Unit = {},
    onReady: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentChanged = rememberUpdatedState(onCenterChanged)
    val currentObserved = rememberUpdatedState(onCenterObserved)
    val currentReady = rememberUpdatedState(onReady)
    val view = remember(context) { QueueGoMapLibrePickerView(context) }

    DisposableEffect(view, lifecycleOwner) {
        view.onCenterChanged = { lat, lng ->
            val point = QgMapPoint(lat, lng, 0)
            if (point.valid) currentChanged.value(point)
        }
        view.onCenterObserved = { lat, lng ->
            val point = QgMapPoint(lat, lng, 0)
            if (point.valid) currentObserved.value(point)
        }
        view.onReady = { currentReady.value(it) }
        view.onCreate()

        fun sync(state: Lifecycle.State) {
            if (state.isAtLeast(Lifecycle.State.STARTED)) view.onStart() else view.onStop()
            if (state.isAtLeast(Lifecycle.State.RESUMED)) view.onResume() else view.onPause()
        }
        sync(lifecycleOwner.lifecycle.currentState)
        val observer = LifecycleEventObserver { _, _ -> sync(lifecycleOwner.lifecycle.currentState) }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.destroy()
        }
    }

    LaunchedEffect(initialPoint?.latitude, initialPoint?.longitude, selectInitialPoint) {
        initialPoint?.takeIf { it.valid }?.let {
            view.setCamera(it.latitude, it.longitude, zoom = 16.0, select = selectInitialPoint, animate = false)
        }
    }
    LaunchedEffect(recenterToken) {
        recenterPoint?.takeIf { it.valid }?.let {
            view.setCamera(it.latitude, it.longitude, zoom = 16.0, select = true, animate = true)
        }
    }

    AndroidView(factory = { view }, modifier = modifier)
}

@Composable
fun QgQueueGoTrackingMap(
    points: List<QgMapPoint>,
    modifier: Modifier = Modifier,
    onReady: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentReady = rememberUpdatedState(onReady)
    val view = remember(context) { QueueGoMapLibreTrackingView(context) }

    DisposableEffect(view, lifecycleOwner) {
        view.onReady = { currentReady.value(it) }
        view.onCreate()
        fun sync(state: Lifecycle.State) {
            if (state.isAtLeast(Lifecycle.State.STARTED)) view.onStart() else view.onStop()
            if (state.isAtLeast(Lifecycle.State.RESUMED)) view.onResume() else view.onPause()
        }
        sync(lifecycleOwner.lifecycle.currentState)
        val observer = LifecycleEventObserver { _, _ -> sync(lifecycleOwner.lifecycle.currentState) }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.destroy()
        }
    }

    LaunchedEffect(points) {
        view.setPoints(
            points.filter { it.valid }.map { point ->
                QueueGoTrackingPoint(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    color = point.color,
                    title = when (point.role) {
                        QgMapRole.MERCHANT -> "จุดรับ"
                        QgMapRole.CUSTOMER -> "จุดหมาย"
                        QgMapRole.RIDER -> "Rider"
                    }
                )
            }
        )
    }

    AndroidView(factory = { view }, modifier = modifier)
}
