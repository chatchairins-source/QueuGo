package com.queuego.rider

import android.os.Bundle
import com.queuego.ridernavigationmap.NavigationMapCamera
import com.queuego.ridernavigationmap.NavigationMapPoint
import android.view.View
import android.view.ViewGroup
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import java.util.concurrent.CountDownLatch

/** Mounts the actual Home map without creating any authentication or business data. */
class RiderMapRuntimeActivity : ComponentActivity() {
    val mapReady = CountDownLatch(1)
    private val recenterSignal = mutableIntStateOf(0)
    private var nativeMap: MapLibreMap? = null
    fun recenterMap() { recenterSignal.intValue += 1 }
    private fun findMapView(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findMapView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    fun actualMapCamera(): DoubleArray {
        val camera = requireNotNull(nativeMap) { "Actual QueueGo map is not mounted" }.cameraPosition
        val target = requireNotNull(camera.target) { "Actual QueueGo camera target is unavailable" }
        return doubleArrayOf(target.longitude, target.latitude, camera.zoom)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QueueGoRiderNavigationMap(
                modifier = Modifier.fillMaxSize(),
                stateKey = "runtime-map",
                fallbackCamera = NavigationMapCamera(NavigationMapPoint(14.9918756, 103.0994795), 16),
                job = null,
                marketPickups = emptyList(),
                recenterSignal = recenterSignal.intValue,
                onMapReady = { ready ->
                    if (ready) findMapView(window.decorView)?.getMapAsync { map ->
                        nativeMap = map
                        mapReady.countDown()
                    }
                }
            )
        }
    }
}
