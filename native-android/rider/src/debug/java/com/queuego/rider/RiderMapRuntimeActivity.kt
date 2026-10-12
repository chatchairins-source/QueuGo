package com.queuego.rider

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.longdo.api.MapGLSurfaceView
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
    fun recenterMap() { recenterSignal.intValue += 1 }
    fun actualMapCamera(): DoubleArray {
        fun find(view: View): MapGLSurfaceView? {
            if (view is MapGLSurfaceView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) {
                find(view.getChildAt(i))?.let { return it }
            }
            return null
        }
        val map = requireNotNull(find(window.decorView)?.renderer?.map) { "Actual SDK map is not mounted" }
        val location = map.getLocation()
        return doubleArrayOf(location.lon, location.lat, map.getZoom().toDouble())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RiderLongdoMap(
                modifier = Modifier.fillMaxSize(), job = null,
                marketPickups = emptyList(), recenterSignal = recenterSignal.intValue,
                navigationState = RiderNavigationState(),
                onLocationFix = {},
                onMapReady = { ready -> if (ready) mapReady.countDown() }
            )
        }
    }
}
