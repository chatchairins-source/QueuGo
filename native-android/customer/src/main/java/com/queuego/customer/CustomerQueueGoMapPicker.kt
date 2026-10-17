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
import com.queuego.ridermaplibreosm.QueueGoMapLibrePickerView
import com.queuego.shared.QgMapPoint

@Composable
internal fun CustomerQueueGoMapPicker(
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

    val view = remember(context) {
        QueueGoMapLibrePickerView(context)
    }

    DisposableEffect(view, lifecycleOwner) {
        view.onCenterChanged = { lat, lng ->
            val point = QgMapPoint(lat, lng, 0)
            if (point.valid) currentChanged.value(point)
        }
        view.onCenterObserved = { lat, lng ->
            val point = QgMapPoint(lat, lng, 0)
            if (point.valid) currentObserved.value(point)
        }
        view.onReady = { ready -> currentReady.value(ready) }
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
            view.setCamera(
                latitude = it.latitude,
                longitude = it.longitude,
                zoom = 16.0,
                select = selectInitialPoint,
                animate = false
            )
        }
    }

    LaunchedEffect(recenterToken) {
        recenterPoint?.takeIf { it.valid }?.let {
            view.setCamera(
                latitude = it.latitude,
                longitude = it.longitude,
                zoom = 16.0,
                select = true,
                animate = true
            )
        }
    }

    AndroidView(
        factory = { view },
        modifier = modifier
    )
}
