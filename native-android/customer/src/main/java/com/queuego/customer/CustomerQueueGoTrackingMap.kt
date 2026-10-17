package com.queuego.customer

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
import com.queuego.ridermaplibreosm.QueueGoMapLibreTrackingView
import com.queuego.ridermaplibreosm.QueueGoTrackingPoint
import com.queuego.shared.QgMapPoint
import com.queuego.shared.QgMapRole

@Composable
internal fun CustomerQueueGoTrackingMap(
    points: List<QgMapPoint>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = remember(context) { QueueGoMapLibreTrackingView(context) }

    DisposableEffect(view, lifecycleOwner) {
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
